package dev.arcana.magic;

import dev.arcana.ArcanaConfig;
import dev.arcana.item.WandItem;
import dev.arcana.net.Net;
import dev.arcana.registry.ModAttachments;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The server-authoritative cast pipeline: validation, mana, cooldown, mastery, feedback. */
public final class CastService {

    public static ItemStack heldWand(Player p) {
        if (p.getMainHandItem().getItem() instanceof WandItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof WandItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static PlayerMagic magic(ServerPlayer p) {
        PlayerMagic m = p.getData(ModAttachments.MAGIC);
        if (m.mana < 0) m.mana = maxMana(p);
        return m;
    }

    public static float maxMana(ServerPlayer p) {
        PlayerMagic m = p.getData(ModAttachments.MAGIC);
        int levels = 0;
        for (School s : School.real()) levels += m.level(s);
        float max = ArcanaConfig.BASE_MANA.get().floatValue() + 3f * levels;
        ItemStack w = heldWand(p);
        if (!w.isEmpty()) max += WandItem.styleOf(w).handleType().bonusMana;
        return max;
    }

    public static float manaCost(SpellContext c) {
        double cost = c.spell.baseMana * ArcanaConfig.SPELLS.get(c.spell).mana().get() * ArcanaConfig.MANA_COST.get();
        cost *= 1.0 - Math.min(0.5, 0.02 * c.mastery);
        cost *= c.style.handleType().mana;
        cost *= c.style.runeProduct(r -> r.mana);
        return (float) cost;
    }

    public static long cooldownTicks(SpellContext c) {
        double cd = c.spell.baseCooldown * ArcanaConfig.SPELLS.get(c.spell).cooldown().get() * ArcanaConfig.COOLDOWN.get();
        cd *= 1.0 - Math.min(0.4, 0.015 * c.mastery);
        cd *= c.style.handleType().cooldown;
        cd *= c.style.runeProduct(r -> r.cooldown);
        return Math.max(1, Math.round(cd));
    }

    private static boolean free(ServerPlayer p) {
        return p.isCreative() && ArcanaConfig.CREATIVE_FREE.get();
    }

    /** Checks whether the spell could be cast right now; informs the player (action bar) when not. */
    public static boolean canStart(ServerPlayer p, ItemStack wand, Spell spell) {
        PlayerMagic m = magic(p);
        long now = p.level().getGameTime();
        if (!ArcanaConfig.SPELLS.get(spell).enabled().get()) return fail(p, "disabled");
        if (spell.school != School.NONE && spell.minStage > WandItem.stageOf(wand)) return fail(p, "stage");
        if (spell.isMimic()) {
            Long t = m.recent.get(spell.mimicked().id);
            if (!free(p) && (t == null || now - t > ArcanaConfig.MIMIC_WINDOW.get())) return fail(p, "mimic_locked");
        }
        if (!free(p) && m.cooldownLeft(spell, now) > 0) return fail(p, "cooldown");
        SpellContext c = SpellContext.of(p, wand, spell, 1f);
        if (!free(p) && m.mana < manaCost(c)) return fail(p, "mana");
        return true;
    }

    private static boolean fail(ServerPlayer p, String why) {
        p.displayClientMessage(Component.translatable("cast.arcana.fail." + why), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
        return false;
    }

    public static boolean cast(ServerPlayer p, ItemStack wand, Spell spell, float charge, InteractionHand hand) {
        if (wand.isEmpty() || !canStart(p, wand, spell)) return false;
        PlayerMagic m = magic(p);
        SpellContext c = SpellContext.of(p, wand, spell, charge);
        if (!spell.impl.cast(c)) {
            p.displayClientMessage(Component.translatable("cast.arcana.fail.nothing"), true);
            return false;
        }
        long now = p.level().getGameTime();
        if (!free(p)) {
            m.mana = Math.max(0, m.mana - manaCost(c));
            m.cooldowns.put(spell.id, now + cooldownTicks(c));
        }
        School credited = c.school;
        if (credited != School.NONE) {
            m.recent.put(credited.id, now);
            m.addXp(credited, Math.max(2, Math.round(manaCost(c) / 4f)));
        }
        if (spell.slot != SpellSlot.ULTIMATE && spell != Spell.ECHO) {
            m.lastSpell = spell.isMimic() ? Spell.of(spell.mimicked(), SpellSlot.PRIMARY) : spell;
            m.lastCharge = charge;
        }
        p.swing(hand == null ? InteractionHand.MAIN_HAND : hand, true);
        castBurst(c);
        if (wand.getItem() instanceof WandItem wi) wi.playCast(p, wand);
        if (spell.slot == SpellSlot.ULTIMATE) Net.cinematic(p, c.rgb(), spell == Spell.TIME_STOP ? 110 : 75, spell == Spell.TIME_STOP ? 1 : 0, spell.ordinal());
        Net.sync(p, true);
        return true;
    }

    /** Cast flourish at the wand tip. */
    public static void castBurst(SpellContext c) {
        Vec3 tip = tip(c.player);
        ParticleOptions trail = Magic.trail(c);
        Magic.fx(c.level, trail, tip, 14, 0.15, 0.15, 0.15, 0.08);
        Magic.fx(c.level, Magic.dust(c, 1.4f), tip, 10, 0.2, 0.2, 0.2, 0);
        Magic.snd(c.level, tip, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.8f + c.school.ordinal() * 0.08f);
    }

    public static Vec3 tip(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize().scale(0.35);
        return p.getEyePosition().add(look.scale(0.9)).add(right).add(0, -0.25, 0);
    }

    /** Server-side charging feedback: converging particles and a rising hum. */
    public static void chargeTick(ServerPlayer p, ItemStack wand, Spell spell, float charge, int used) {
        if (used % 3 != 0) return;
        School school = WandItem.schoolOf(wand);
        int rgb = WandItem.tintOf(wand, spell.isMimic() ? spell.mimicked() : school);
        var level = p.serverLevel();
        Vec3 tip = tip(p);
        var fx = WandItem.styleOf(wand).fxType();
        ParticleOptions opt = fx.particle != null ? fx.particle : School.dust(rgb, 0.9f);
        double radius = 1.1 - 0.8 * charge;
        int n = Math.max(2, Magic.count(2 + (int) (charge * 6)));
        for (int i = 0; i < n; i++) {
            double a = (used * 0.35) + i * (Math.PI * 2 / n);
            Vec3 off = new Vec3(Math.cos(a) * radius, Math.sin(a * 1.3) * radius * 0.6, Math.sin(a) * radius);
            level.sendParticles(opt, tip.x + off.x, tip.y + off.y, tip.z + off.z, 1, 0, 0, 0, 0);
        }
        if (used % 12 == 0) Magic.snd(level, tip, SoundEvents.BEACON_AMBIENT, 0.4f, 0.6f + charge);
        if (charge >= 1f && used % 12 == 0) level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 4, 0.1, 0.1, 0.1, 0.05);
    }

    private CastService() {}

}

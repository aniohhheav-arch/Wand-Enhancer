package com.wandcraft.magic;

import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Damage-time hooks for the Defense school and the Force Jump landing cushion. */
public final class DefenseEvents {
    private DefenseEvents() {}

    static boolean magical(DamageSource s) {
        return s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC)
                || s.is(DamageTypes.FIREBALL) || s.is(DamageTypes.WITHER_SKULL) || s.is(DamageTypes.DRAGON_BREATH)
                || s.is(DamageTypes.LIGHTNING_BOLT) || s.is(DamageTypes.SONIC_BOOM);
    }

    private static Vec3 sourcePos(DamageSource s) {
        Vec3 p = s.getSourcePosition();
        return p;
    }

    /** True if the damage comes from within the shield arc in front of the player. */
    private static boolean frontal(ServerPlayer p, DamageSource s, double cosArc) {
        Vec3 src = sourcePos(s);
        if (src == null) return false;
        Vec3 to = src.subtract(p.getEyePosition());
        if (to.lengthSqr() < 1e-4) return true;
        return to.normalize().dot(p.getLookAngle()) >= cosArc;
    }

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerMagic m = Magic.of(p);
        long now = p.level().getGameTime();
        DamageSource src = e.getSource();
        ServerLevel l = p.serverLevel();

        // Sanctuary: allies inside take less magical and projectile damage.
        if (p.getPersistentData().getLong("wc_sanct") > now && magical(src)) e.setAmount(e.getAmount() * 0.6f);

        // Counterspell: cancel the first magical hit and answer with a counterblast.
        if (m.parryUntil > now && magical(src)) {
            m.parryUntil = 0;
            e.setCanceled(true);
            Vec3 at = p.position().add(0, 1, 0);
            Fx.shockwave(l, Fx.spark(0xFFE9A0, 1.1f), p.position().add(0, 0.2, 0), 3.8f, 5);
            Fx.burst(l, Fx.spark(0xFFFFFF, 1.3f), at, 30, 0.4, 0.5);
            Fx.sound(l, at, SoundEvents.TRIDENT_RETURN, 1f, 1.4f);
            Fx.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.8f);
            Spell cs = Spells.get("counterspell");
            int hits = 0;
            for (LivingEntity t : Combat.enemiesIn(l, p.position(), 3.8, p)) {
                Combat.hurt(l, p, p, t, 5f);
                Combat.pushAway(t, p.position(), 0.9, 0.3);
                hits++;
            }
            if (cs != null) Magic.credit(p, cs, 1 + Math.min(hits, 2));
            return;
        }

        // Mirror Ward: reflect the first frontal projectile.
        if (m.mirrorUntil > now && src.getDirectEntity() instanceof Projectile pr && frontal(p, src, 0.2)) {
            m.mirrorUntil = 0;
            e.setCanceled(true);
            reflect(p, pr);
            return;
        }

        // Phase Guard: one hit mostly passes through.
        if (m.phaseUntil > now && !src.is(DamageTypes.FELL_OUT_OF_WORLD) && !src.is(DamageTypes.GENERIC_KILL)) {
            m.phaseUntil = 0;
            e.setAmount(e.getAmount() * 0.2f);
            Fx.burst(l, Fx.wisp(0xB48CFF, 1f), p.position().add(0, 1, 0), 18, 0.4, 0.05);
            Fx.sound(l, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.6f);
            Spell ps = Spells.get("phase_guard");
            if (ps != null) Magic.credit(p, ps, 1);
            return;
        }

        // Magic Shield / Aegis Shield: absorb frontal damage while the barrier holds.
        if (m.channelSpell != null && m.shieldCapacity > 0 && frontal(p, src, m.shieldArc)) {
            boolean projectile = src.getDirectEntity() instanceof Projectile;
            float absorbed = projectile ? e.getAmount() : e.getAmount() * m.shieldReduction;
            m.shieldCapacity -= absorbed + (projectile ? 1.5f : 0);
            if (projectile) {
                e.setCanceled(true);
                src.getDirectEntity().discard();
            } else {
                e.setAmount(e.getAmount() - absorbed);
            }
            Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(1.3));
            Fx.burst(l, Fx.spark(0x8FD0FF, 0.9f), at, 10, 0.4, 0.1);
            Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 1.2f);
            Spell sp = Spells.get(m.channelSpell);
            if (sp != null) Magic.credit(p, sp, 1);
            if (m.shieldCapacity <= 0) {
                m.shieldCapacity = 0;
                Fx.burst(l, ParticleTypes.CLOUD, at, 15, 0.5, 0.1);
                Fx.sound(l, at, SoundEvents.GLASS_BREAK, 1f, 0.7f);
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("§cYour shield shattered!"), true);
                p.stopUsingItem();
            }
        }
    }

    /** Sends a projectile back at the nearest valid target, or deflects it harmlessly upward. */
    public static void reflect(ServerPlayer owner, Projectile pr) {
        ServerLevel l = owner.serverLevel();
        Vec3 pos = pr.position();
        double speed = Math.max(1.2, pr.getDeltaMovement().length());
        LivingEntity best = null;
        double bd = 24 * 24;
        for (LivingEntity t : Combat.enemiesIn(l, pos, 24, owner)) {
            double d = t.distanceToSqr(pos);
            if (d < bd && owner.hasLineOfSight(t)) {
                bd = d;
                best = t;
            }
        }
        Vec3 dir = best != null
                ? best.position().add(0, best.getBbHeight() * 0.6, 0).subtract(pos).normalize()
                : owner.getLookAngle().add(0, 0.6, 0).normalize();
        pr.setOwner(owner);
        pr.setDeltaMovement(dir.scale(speed));
        pr.hurtMarked = true;
        Fx.burst(l, Fx.spark(0xFFFFFF, 1.2f), pos, 20, 0.3, 0.3);
        Fx.sound(l, pos, SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 1.5f);
        Spell s = Spells.get("mirror_ward");
        if (s != null && best != null) Magic.credit(owner, s, 1);
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Magic.of(p).softFallUntil > p.level().getGameTime()) {
            e.setDamageMultiplier(0.15f);
        }
    }

    static Entity none() { return null; }
}

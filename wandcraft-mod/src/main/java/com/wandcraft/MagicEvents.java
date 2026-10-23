package com.wandcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Rank;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.util.Scheduler;
import com.wandcraft.magic.util.TempBlocks;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class MagicEvents {
    private MagicEvents() {}

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerMagic m = Magic.of(p);
        long now = p.level().getGameTime();

        // Mana regeneration pauses briefly after casting and while channelling.
        if (m.channelSpell == null && now - m.lastCastTick > 30) {
            float regen = (float) (Config.MANA_REGEN_PER_SECOND.get() / 20.0) * (1f + 0.03f * m.level());
            if (m.pactUntil > now) regen *= 2f;
            if (m.mana < m.maxMana()) {
                m.mana = Math.min(m.maxMana(), m.mana + regen);
                m.dirty = true;
            }
        } else if (m.mana > m.maxMana()) {
            m.mana = m.maxMana();
        }

        // Losing the wand always stops a channel.
        if (m.channelSpell != null && (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof com.wandcraft.item.WandItem))) {
            Casting.endChannel(p);
        }

        if (m.stealthUntil > now && p.tickCount % 10 == 0) {
            ServerLevel sl0 = p.serverLevel();
            for (net.minecraft.world.entity.Mob mob : sl0.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(24),
                    mob -> mob.getTarget() == p && mob.distanceToSqr(p) > 16)) mob.setTarget(null);
        }

        if (m.wingsUntil > now) {
            if (now % 5 == 0) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 12, 0, false, false, false));
            if (!p.onGround()) {
                ServerLevel sl = p.serverLevel();
                sl.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.5, 0.3, 0.5, 0.01);
                sl.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.6, 0.2, 0.6, 0.0);
            }
        } else if (m.wingsUntil != 0) {
            m.wingsUntil = 0;
            m.dirty = true;
        }

        if ((m.dirty && p.tickCount % 2 == 0) || p.tickCount % 20 == 0) Magic.sync(p);
    }

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel l)) return;
        Scheduler.tick(l);
        if (l.getGameTime() % 4 == 0) TempBlocks.tick(l);
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) {
        for (ServerLevel l : e.getServer().getAllLevels()) {
            // Restore anything still temporary so nothing becomes permanent.
            TempBlocks.restoreAll(l);
        }
        Scheduler.clear();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Magic.sync(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Magic.of(p).mana = Magic.of(p).maxMana();
            Magic.sync(p);
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Magic.sync(p);
    }

    /** Temporary blocks are never harvested; mining one chips its durability instead of dropping loot. */
    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel l)) return;
        BlockPos pos = e.getPos();
        if (TempBlocks.isTemp(l, pos)) {
            e.setCanceled(true);
            TempBlocks.damage(l, pos, 1);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wandcraft").requires(s -> s.hasPermission(2))
                .then(Commands.literal("level").then(Commands.argument("n", IntegerArgumentType.integer(0, 30)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PlayerMagic m = Magic.of(p);
                    int n = IntegerArgumentType.getInteger(c, "n");
                    m.uses.clear();
                    m.uses.put("arcane_bolt", n * n * 3);
                    m.mana = m.maxMana();
                    Magic.sync(p);
                    c.getSource().sendSuccess(() -> Component.literal("Wizard level set to " + n), true);
                    return 1;
                })))
                .then(Commands.literal("master").then(Commands.argument("spell", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(c, "spell");
                    Spell s = id.equals("all") ? null : Spells.get(id);
                    if (id.equals("all")) {
                        for (Spell sp : Spells.all()) Magic.of(p).uses.merge(sp.id, Rank.MASTER.uses, Math::max);
                    } else if (s == null) {
                        c.getSource().sendFailure(Component.literal("Unknown spell " + id));
                        return 0;
                    } else {
                        Magic.of(p).uses.merge(s.id, Rank.MASTER.uses, Math::max);
                    }
                    Magic.sync(p);
                    return 1;
                })))
                .then(Commands.literal("prop").then(Commands.argument("kind", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String kind = StringArgumentType.getString(c, "kind");
                    if (com.wandcraft.entity.Props.get(kind) == null) {
                        c.getSource().sendFailure(Component.literal("Unknown prop " + kind));
                        return 0;
                    }
                    net.minecraft.world.phys.Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
                    com.wandcraft.entity.SpellProp pr = com.wandcraft.entity.SpellProp.spawn(p.serverLevel(), kind, at, 1f, 400);
                    pr.facing(p.getYRot() + 180f, 0f);
                    return 1;
                })))
                .then(Commands.literal("mana").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Magic.of(p).mana = Magic.of(p).maxMana();
                    Magic.sync(p);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PlayerMagic m = Magic.of(p);
                    m.uses.clear();
                    m.cooldownEnd.clear();
                    m.selected = "arcane_bolt";
                    Magic.sync(p);
                    return 1;
                })));
    }
}

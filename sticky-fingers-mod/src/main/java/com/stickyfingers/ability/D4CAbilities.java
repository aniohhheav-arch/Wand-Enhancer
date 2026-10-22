package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.*;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** D4C (Dirty Deeds Done Dirt Cheap) ability implementations. */
public final class D4CAbilities {

    public static boolean activate(AbilityType type, ServerPlayer p, StandData data, boolean alt) {
        return switch (type) {
            case D4C_PORTAL -> portal(p, data);
            case D4C_DIMENSION_JUMP -> dimensionJump(p, data);
            case D4C_CLONE -> clone(p, data);
            case D4C_PARALLEL_SWAP -> parallelSwap(p, data);
            case D4C_AMBUSH -> ambush(p, data);
            case D4C_LOVE_TRAIN -> loveTrain(p, data);
            case D4C_REALITY_FRACTURE -> realityFracture(p, data);
            case D4C_COLLISION -> collision(p, data);
            case D4C_CLONE_ASSAULT -> cloneAssault(p, data);
            case D4C_DIMENSIONAL_COLLAPSE -> dimensionalCollapse(p, data);
            default -> false;
        };
    }

    // ─── Create Portal ────────────────────────────────────────────────────
    private static boolean portal(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        var hit = Abilities.pickBlock(p, SFConfig.range(AbilityType.D4C_PORTAL));
        Vec3 spawnPos = hit != null ? Vec3.atCenterOf(hit.getBlockPos()).add(0, 1, 0) : p.position().add(p.getLookAngle().scale(3));

        int dur = SFConfig.duration(AbilityType.D4C_PORTAL);
        D4CPortalEntity portalA = D4CPortalEntity.create(level, p, spawnPos, dur);

        int existingPortalId = data.d4cPortalA();
        Entity existing = existingPortalId >= 0 ? level.getEntity(existingPortalId) : null;
        if (existing instanceof D4CPortalEntity portalB && !portalB.isRemoved()) {
            portalA.linkTo(portalB);
            data.setPortals(-1, -1);
            p.displayClientMessage(Component.literal("Portals linked!").withStyle(ChatFormatting.GOLD), true);
        } else {
            data.setPortals(portalA.getId(), -1);
            p.displayClientMessage(Component.literal("Portal opened. Open another to link.").withStyle(ChatFormatting.GOLD), true);
        }

        Fx.sound(level, spawnPos, ModSounds.PORTAL_OPEN.get(), 1.5f, 1.0f);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), spawnPos, 40, 1.2, 0.08);
        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), spawnPos, 20, 1.0, 0.1);
        StandManager.animate(p, StandAnim.CAST);
        return true;
    }

    // ─── Dimension Jump: travel to/from parallel dimension ────────────────
    private static boolean dimensionJump(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        MinecraftServer server = p.getServer();
        if (server == null) return false;

        Fx.particles(level, ModParticles.VOID_MOTE.get(), p.position(), 25, 1.2, 0.08);
        Fx.sound(level, p.position(), ModSounds.PORTAL_OPEN.get(), 1.5f, 1.0f);

        // If already in the End (parallel stand-in), return to origin overworld coords
        if (data.isInParallelDimension()) {
            ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld == null) return false;
            double ox = data.dimOriginX(), oy = data.dimOriginY(), oz = data.dimOriginZ();
            data.clearDimOrigin();
            p.teleportTo(overworld, ox, oy, oz, p.getYRot(), p.getXRot());
            Fx.sound(overworld, new Vec3(ox, oy, oz), ModSounds.DIMENSION_JUMP.get(), 1.5f, 0.9f);
            p.displayClientMessage(Component.literal("Returned from parallel universe.").withStyle(ChatFormatting.GOLD), true);
        } else {
            // Travel to End as parallel dimension stand-in
            ServerLevel parallel = server.getLevel(net.minecraft.world.level.Level.END);
            if (parallel == null) {
                p.displayClientMessage(Component.literal("Parallel dimension not loaded.").withStyle(ChatFormatting.RED), true);
                return false;
            }
            data.setDimOrigin(p.getX(), p.getY(), p.getZ());
            p.teleportTo(parallel, p.getX(), 65, p.getZ(), p.getYRot(), p.getXRot());
            p.displayClientMessage(Component.literal("Entered parallel universe!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
        StandManager.animate(p, StandAnim.SUMMON);
        return true;
    }

    // ─── Summon Clone ─────────────────────────────────────────────────────
    private static boolean clone(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        int existingId = data.d4cClone();
        Entity existing = existingId >= 0 ? level.getEntity(existingId) : null;
        if (existing instanceof D4CCloneEntity dc && !dc.isRemoved()) {
            dc.discard();
            Fx.particles(level, ModParticles.VOID_MOTE.get(), dc.position(), 20, 0.8, 0.1);
            Fx.sound(level, dc.position(), ModSounds.PORTAL_CLOSE.get(), 1.0f, 1.2f);
            data.setClone(-1);
            return true;
        }

        int lifeTicks = SFConfig.duration(AbilityType.D4C_CLONE);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), p.position().add(2, 0, 0), 30, 1.0, 0.08);
        Fx.sound(level, p.position(), ModSounds.PORTAL_OPEN.get(), 1.0f, 1.2f);
        D4CCloneEntity clone = D4CCloneEntity.spawn(level, p, lifeTicks);
        data.setClone(clone.getId());
        p.displayClientMessage(Component.literal("Alternate self summoned!").withStyle(ChatFormatting.GOLD), true);
        StandManager.animate(p, StandAnim.SUMMON);
        return true;
    }

    // ─── Parallel Swap ────────────────────────────────────────────────────
    private static boolean parallelSwap(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        int cloneId = data.d4cClone();
        Entity clone = cloneId >= 0 ? level.getEntity(cloneId) : null;
        if (!(clone instanceof D4CCloneEntity dc) || dc.isRemoved()) return false;

        Vec3 pPos = p.position();
        Vec3 cPos = dc.position();
        Fx.particles(level, ModParticles.VOID_MOTE.get(), pPos, 20, 1.0, 0.1);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), cPos, 20, 1.0, 0.1);
        p.teleportTo(cPos.x, cPos.y, cPos.z);
        dc.moveTo(pPos.x, pPos.y, pPos.z);
        Fx.sound(level, cPos, ModSounds.DIMENSION_JUMP.get(), 1.0f, 0.9f);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), cPos, 25, 1.0, 0.12);
        StandManager.animate(p, StandAnim.CAST);
        return true;
    }

    // ─── Dimension Ambush ─────────────────────────────────────────────────
    private static boolean ambush(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.D4C_AMBUSH));
        if (target == null || !Abilities.canAffect(target)) return false;

        ServerLevel level = p.serverLevel();
        StandManager.animate(p, StandAnim.HEAVY);
        Vec3 savedPos = p.position();
        Vec3 strikePos = target.position().add(p.getLookAngle().scale(-1).normalize());

        Fx.sound(level, p.position(), ModSounds.PORTAL_OPEN.get(), 1.0f, 1.3f);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), p.position(), 15, 0.8, 0.1);

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 4) {
                    pl.teleportTo(strikePos.x, strikePos.y, strikePos.z);
                    Fx.particles(level, ModParticles.VOID_MOTE.get(), strikePos, 15, 0.8, 0.1);
                }
                if (t == 8) {
                    if (target.isAlive()) {
                        float dmg = (float)(14.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
                        target.hurt(pl.damageSources().playerAttack(pl), dmg);
                        Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position().add(0, 1, 0), 20, 0.3, 0.3);
                        Fx.sound(level, target.position(), ModSounds.PUNCH_HEAVY.get(), 1.2f, 1.1f);
                    }
                }
                if (t == 14) {
                    pl.teleportTo(savedPos.x, savedPos.y, savedPos.z);
                    Fx.sound(level, savedPos, ModSounds.PORTAL_CLOSE.get(), 1.0f, 1.3f);
                    Fx.particles(level, ModParticles.VOID_MOTE.get(), savedPos, 10, 0.5, 0.1);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "d4c_ambush"; }
        });
        return true;
    }

    // ─── Love Train ───────────────────────────────────────────────────────
    private static boolean loveTrain(ServerPlayer p, StandData data) {
        if (data.isLoveTrain()) {
            data.setLoveTrain(false);
            p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            p.removeEffect(MobEffects.ABSORPTION);
            p.displayClientMessage(Component.literal("Love Train dissipated.").withStyle(ChatFormatting.GOLD), true);
        } else {
            int dur = SFConfig.duration(AbilityType.D4C_LOVE_TRAIN);
            data.setLoveTrain(true);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 3));
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, 2));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1));
            Fx.sound(p.serverLevel(), p.position(), ModSounds.LOVE_TRAIN.get(), 1.5f, 1.0f);
            Fx.particles(p.serverLevel(), ModParticles.SEAM_GLOW.get(), p.position().add(0, 1, 0), 60, 2.0, 0.06);
            p.displayClientMessage(Component.literal("Love Train activated! Calamity redirected.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            // Auto-cancel scheduled via a tickdown sequence
            ServerLevel level = p.serverLevel();
            final int[] remaining = {dur};
            Scheduler.run(p, new Scheduler.Sequence() {
                @Override
                public boolean tick(ServerPlayer pl, int t) {
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        if (data.isLoveTrain()) {
                            data.setLoveTrain(false);
                            pl.displayClientMessage(Component.literal("Love Train ended.").withStyle(ChatFormatting.GOLD), true);
                        }
                        return false;
                    }
                    return true;
                }
                @Override public String key() { return "d4c_lovetrain"; }
            });
        }
        StandManager.animate(p, StandAnim.IDLE);
        StandManager.sync(p);
        return true;
    }

    // ─── Reality Fracture ─────────────────────────────────────────────────
    private static boolean realityFracture(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(8));

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(center, 10, 10, 10),
                e -> e != p && e.isAlive() && Abilities.canAffect(e));

        if (targets.isEmpty()) return false;

        float dmg = (float)(10.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
        for (LivingEntity t : targets) {
            t.hurt(p.damageSources().playerAttack(p), dmg);
            Vec3 push = t.position().subtract(center).normalize().scale(2.5).add(0, 0.4, 0);
            t.setDeltaMovement(push);
        }
        Fx.shake(level, center, 6, 0.8f, 10);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), center, 50, 3.0, 0.1);
        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), center, 30, 2.0, 0.1);
        Fx.sound(level, center, ModSounds.REALITY_FRACTURE.get(), 1.5f, 1.0f);
        Fx.screen(p, ScreenFx.SHAKE, 0.8f, 10);
        StandManager.animate(p, StandAnim.SLAM);
        return true;
    }

    // ─── Parallel Universe Collision ──────────────────────────────────────
    private static boolean collision(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.D4C_COLLISION));
        if (target == null || !Abilities.canAffect(target)) return false;

        ServerLevel level = p.serverLevel();
        StandManager.animate(p, StandAnim.SLAM);
        Fx.sound(level, target.position(), ModSounds.PORTAL_OPEN.get(), 1.5f, 0.8f);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), target.position(), 50, 2.0, 0.1);

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 10) {
                    if (!target.isAlive()) return false;
                    float dmg = (float)(20.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
                    target.hurt(pl.damageSources().playerAttack(pl), dmg);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
                    Fx.shake(level, target.position(), 6, 0.9f, 12);
                    Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), target.position(), 30, 1.0, 0.2);
                    Fx.sound(level, target.position(), ModSounds.FINAL_IMPACT.get(), 2.0f, 0.9f);
                    Fx.screen(pl, ScreenFx.SHAKE, 1.2f, 12);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "d4c_collision"; }
        });
        return true;
    }

    // ─── Clone Assault ────────────────────────────────────────────────────
    private static boolean cloneAssault(ServerPlayer p, StandData data) {
        LivingEntity target = Abilities.pickEntity(p, SFConfig.range(AbilityType.D4C_CLONE_ASSAULT));
        if (target == null || !Abilities.canAffect(target)) return false;

        ServerLevel level = p.serverLevel();
        StandManager.animate(p, StandAnim.BARRAGE);
        Fx.sound(level, target.position(), ModSounds.PORTAL_OPEN.get(), 1.5f, 1.0f);
        float dmgEach = (float)(10.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));
        int clones = 4;

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (!target.isAlive()) return false;
                for (int i = 0; i < clones; i++) {
                    if (t == i * 8) {
                        double angle = (i * Math.PI * 2.0) / clones;
                        Vec3 strikeFrom = target.position().add(Math.sin(angle) * 2, 0, Math.cos(angle) * 2);
                        Fx.particles(level, ModParticles.VOID_MOTE.get(), strikeFrom, 10, 0.5, 0.1);
                        target.hurt(pl.damageSources().playerAttack(pl), dmgEach);
                        Vec3 kb = target.position().subtract(strikeFrom).normalize().scale(0.8).add(0, 0.2, 0);
                        target.setDeltaMovement(target.getDeltaMovement().add(kb));
                        Fx.sound(level, target.position(), ModSounds.PUNCH.get(), 1.0f, 0.9f + (float)(i * 0.05));
                    }
                }
                if (t == clones * 8 + 6) {
                    target.hurt(pl.damageSources().playerAttack(pl), dmgEach * 2);
                    Fx.shake(level, target.position(), 8, 1.0f, 12);
                    Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), target.position(), 40, 2.0, 0.2);
                    Fx.sound(level, target.position(), ModSounds.FINAL_IMPACT.get(), 2.0f, 0.8f);
                    Fx.screen(pl, ScreenFx.SHAKE, 1.0f, 12);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "d4c_assault"; }
        });
        return true;
    }

    // ─── Dimensional Collapse (Ultimate) ──────────────────────────────────
    private static boolean dimensionalCollapse(ServerPlayer p, StandData data) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position().add(p.getLookAngle().scale(8));

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(center, 20, 10, 20),
                e -> e != p && e.isAlive() && Abilities.canAffect(e));

        if (targets.isEmpty()) return false;

        StandManager.animate(p, StandAnim.SLAM);
        Fx.sound(level, center, ModSounds.PORTAL_OPEN.get(), 2.5f, 0.7f);
        Fx.screen(p, ScreenFx.SHAKE, 2.0f, 16);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), center, 100, 8.0, 0.06);
        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), center, 60, 6.0, 0.08);

        float dmg = (float)(16.0 * SFConfig.safe(SFConfig.DAMAGE_MULTIPLIER::get, 1.0));

        Scheduler.run(p, new Scheduler.Sequence() {
            @Override
            public boolean tick(ServerPlayer pl, int t) {
                if (t == 10) {
                    for (LivingEntity t2 : targets) {
                        if (!t2.isAlive()) continue;
                        Vec3 pull = center.subtract(t2.position()).normalize().scale(1.5).add(0, 0.3, 0);
                        t2.setDeltaMovement(pull);
                    }
                }
                int aliveCount = 0;
                for (int i = 0; i < targets.size() && i < 6; i++) {
                    LivingEntity t2 = targets.get(i);
                    if (!t2.isAlive()) continue;
                    aliveCount++;
                    int hitTick = 20 + i * 6;
                    if (t == hitTick) {
                        t2.hurt(pl.damageSources().playerAttack(pl), dmg);
                        Fx.particles(level, ModParticles.PUNCH_IMPACT.get(), t2.position().add(0, 1, 0), 15, 0.4, 0.3);
                    }
                }
                if (t == 60) {
                    for (LivingEntity t2 : targets) {
                        if (!t2.isAlive()) continue;
                        t2.hurt(pl.damageSources().playerAttack(pl), dmg * 1.5f);
                        t2.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 5));
                    }
                    Fx.shake(level, center, 12, 1.2f, 16);
                    Fx.particles(level, ModParticles.VOID_MOTE.get(), center, 80, 6.0, 0.08);
                    Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), center, 50, 4.0, 0.1);
                    Fx.sound(level, center, ModSounds.FINAL_IMPACT.get(), 3.0f, 0.6f);
                    Fx.screen(pl, ScreenFx.SHAKE, 3.0f, 20);
                    pl.displayClientMessage(Component.literal("Dimensional Collapse!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    return false;
                }
                return true;
            }
            @Override public String key() { return "d4c_collapse"; }
        });
        return true;
    }

    private D4CAbilities() {}
}

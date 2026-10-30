package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power Beam: the gauntlet trembles as energy pours into it for a second, then a coiling purple beam erupts and can
 * be swept across the battlefield for a second and a half. Anything it kills is disintegrated in a slow-motion
 * close-up; the beam's end detonates when it cuts out.
 */
public final class PowerBeamAbility extends Ability {
    static final int CHARGE = 20;
    static final int FIRE = 32;
    static final double RANGE = 40;

    public PowerBeamAbility() {
        super("power_beam", InfinityStone.POWER, 160);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        PowerFx.pose(player, PowerPose.CHARGE, CHARGE);
        PowerFx.anim(ctx, "power_charge");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE + FIRE, 3, false, false, false));
        Vfx.send(level, VfxType.CHARGE, ctx.handPos(), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 2.6f, CHARGE + 2);
        Vfx.sound(level, ctx.handPos(), ModSounds.POWER_CHARGE, 2f, 1f);
        Vfx.camera(level, player.position(), 4, 0.15f, 0.92f, CHARGE + 6, 0, 0, 0);
        boolean[] finished = {false};
        ServerScheduler.schedule(CHARGE, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            PowerFx.pose(player, PowerPose.THRUST, FIRE + 4);
            PowerFx.anim(ctx, "power_fire");
            Vfx.sound(level, ctx.handPos(), ModSounds.POWER_BEAM, 3f, 0.9f);
            Vfx.camera(level, player.position(), 6, 0.6f, 1.08f, 10, 0xFFFFFF, 0.25f, 3);
            Vfx.glitch(level, player.position(), 8, 0.35f);
        });
        for (int i = 0; i < FIRE; i += 2) {
            int step = i;
            ServerScheduler.schedule(CHARGE + i, () -> fireStep(ctx, step, finished));
        }
        ServerScheduler.schedule(CHARGE + FIRE, () -> {
            if (player.isAlive() && player.level() == level) {
                Vec3 end = beamEnd(ctx);
                Vfx.send(level, VfxType.POWER_EXPLOSION, end, Vec3.ZERO, PowerFx.PURPLE, 0xFFD8FF, 2.6f, 22);
                Vfx.send(level, VfxType.SHOCKWAVE, end, ctx.look().scale(-1), PowerFx.MAGENTA, PowerFx.PINK, 5f, 12);
                Vfx.sound(level, end, ModSounds.POWER_EXPLOSION, 2.5f, 1.1f);
                for (LivingEntity e : CombatFx.targets(player, end, 3.5)) {
                    CombatFx.strike(player, e, 8f, e.position().subtract(end).normalize().scale(1.1).add(0, 0.6, 0), 1.2f,
                            CombatFx.DeathStyle.DISINTEGRATE, false);
                }
            }
        });
        return CastResult.SUCCESS;
    }

    private static Vec3 beamEnd(AbilityContext ctx) {
        BlockHitResult hit = ctx.pickBlock(RANGE);
        return hit.getType() == HitResult.Type.MISS ? ctx.eye().add(ctx.look().scale(RANGE)) : hit.getLocation();
    }

    private static void fireStep(AbilityContext ctx, int step, boolean[] finished) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        if (!player.isAlive() || player.level() != level) {
            return;
        }
        Vec3 start = ctx.handPos();
        Vec3 end = beamEnd(ctx);
        Vec3 dir = end.subtract(start).normalize();
        Vfx.send(level, VfxType.HELIX_BEAM, start, end, PowerFx.PURPLE, PowerFx.PINK, 0.42f, 3);
        if (step % 6 == 0) {
            PowerFx.cracks(level, end, 1.6f, 40, PowerFx.MAGENTA);
            Vfx.send(level, VfxType.SPARK_BURST, end, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 0.9f, 8);
        }
        player.setDeltaMovement(player.getDeltaMovement().subtract(dir.scale(0.03)));
        player.hurtMarked = true;
        AABB box = new AABB(start, end).inflate(1.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> TargetRules.canTarget(player, e))) {
            if (distanceToSegment(e.getBoundingBox().getCenter(), start, end) > 1.0 + e.getBbWidth() * 0.5) {
                continue;
            }
            CombatFx.react(level, e, CombatFx.Reaction.RECOIL, dir, 6);
            boolean killed = CombatFx.strike(player, e, 4.5f, dir.scale(0.45).add(0, 0.08, 0), 0.5f, CombatFx.DeathStyle.DISINTEGRATE, false);
            if (killed && !finished[0]) {
                finished[0] = true;
                disintegration(player, level, e);
            }
        }
    }

    /** Disintegration finisher: a slow-motion close-up as the victim burns away, then the residue detonates. */
    private static void disintegration(ServerPlayer player, ServerLevel level, LivingEntity victim) {
        Vec3 at = victim.getBoundingBox().getCenter();
        PowerFx.cinematic(player, null, at, PowerFx.rel(player, 2.6, 0.4, -2.6), PowerFx.rel(player, 4.2, 1.6, -0.5), 36, 10f, 0.75f);
        CombatFx.slowmo(level, at, 16, 0.25f, 24);
        Vfx.dissolve(level, victim, InfinityStone.POWER);
        Vfx.send(level, VfxType.SHARD_NOVA, at, Vec3.ZERO, 0xFFB0F0, 0xFFFFFF, 3f, 30);
        Vfx.sound(level, at, ModSounds.POWER_FINISHER, 2.5f, 1.2f);
        Vfx.glitch(level, at, 20, 0.7f);
        ServerScheduler.schedule(14, () -> {
            Vfx.send(level, VfxType.POWER_EXPLOSION, at, Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 3.5f, 26);
            Vfx.send(level, VfxType.SPHERE_PULSE, at, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 6f, 14);
            Vfx.sound(level, at, ModSounds.POWER_EXPLOSION, 3f, 0.8f);
            Vfx.camera(level, at, 30, 1f, 0, 0, 0xFFE0FF, 0.45f, 8);
        });
    }

    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}

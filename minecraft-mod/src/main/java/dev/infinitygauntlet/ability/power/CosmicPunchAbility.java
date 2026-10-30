package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Cosmic Punch: the caster dashes at the target and lands one blow. The world stops for a beat on contact (hit-stop,
 * impact frame, zoom), then a vertical shockwave erupts and the target is launched. A wounded target is executed: the
 * camera swings low and to the side, time slows to a crawl as it flies, and it explodes where it falls.
 */
public final class CosmicPunchAbility extends Ability {
    static final double RANGE = 16;
    static final float DAMAGE = 18f;

    public CosmicPunchAbility() {
        super("cosmic_punch", InfinityStone.POWER, 120);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = ctx.pickLiving(RANGE);
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        PowerFx.pose(player, PowerPose.PUNCH, 18);
        PowerFx.anim(ctx, "power_punch");
        Vfx.sound(level, player.position(), ModSounds.POWER_WHOOSH, 2f, 0.8f);
        Vfx.send(level, VfxType.ENTITY_TRAIL, player.position(), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 0.9f, 14, player.getId());
        Vfx.camera(level, player.position(), 4, 0, 1.18f, 12, 0, 0, 0);
        boolean[] landed = {false};
        for (int i = 0; i < 12; i++) {
            ServerScheduler.schedule(1 + i, () -> {
                if (landed[0] || !player.isAlive() || !target.isAlive() || player.level() != level) {
                    return;
                }
                Vec3 to = target.position().subtract(player.position());
                if (to.length() < 2.4 + target.getBbWidth() * 0.5) {
                    landed[0] = true;
                    impact(ctx, target);
                    return;
                }
                player.setDeltaMovement(to.normalize().scale(Math.min(2.0, 0.6 + to.length() * 0.35)).add(0, 0.08, 0));
                player.hurtMarked = true;
                player.fallDistance = 0;
            });
        }
        return CastResult.SUCCESS;
    }

    private static void impact(AbilityContext ctx, LivingEntity target) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 dir = target.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        Vec3 contact = target.getBoundingBox().getCenter().subtract(dir.scale(target.getBbWidth() * 0.5));
        boolean execute = target.getHealth() <= CombatFx.damage(DAMAGE) || target.getHealth() / target.getMaxHealth() <= 0.35f;

        // the impact frame: everything stops, the screen flashes, the camera punches in
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        TimeManager.apply(target, 0f, 5, true);
        CombatFx.hitstop(level, contact, 6, 5, player);
        Vfx.send(level, VfxType.IMPACT_STAR, contact, dir, PowerFx.PURPLE, 0xFFFFFF, 3.2f, 7);
        Vfx.sound(level, contact, ModSounds.POWER_PUNCH_IMPACT, 3f, 0.9f);
        Vfx.camera(level, contact, 24, 1.3f, 0.78f, 7, 0xFFFFFF, 0.55f, 3);
        Vfx.glitch(level, contact, 12, 0.7f);
        CombatFx.react(level, target, CombatFx.Reaction.RECOIL, dir, 6);

        ServerScheduler.schedule(5, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            Vec3 launch = dir.scale(execute ? 2.6 : 2.0).add(0, execute ? 1.25 : 1.0, 0);
            Vfx.send(level, VfxType.SHOCKWAVE, contact, dir, PowerFx.PURPLE, PowerFx.PINK, 7f, 14);
            Vfx.send(level, VfxType.SHOCKWAVE, contact, dir, PowerFx.MAGENTA, 0xFFFFFF, 4f, 9);
            Vfx.send(level, VfxType.SPHERE_PULSE, contact, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 2.5f, 8);
            Vfx.send(level, VfxType.ENTITY_TRAIL, target.position(), Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 1.1f, 30, target.getId());
            Vfx.sound(level, contact, ModSounds.POWER_EXPLOSION, 2f, 1.5f);
            CombatFx.react(level, target, CombatFx.Reaction.TUMBLE, dir, 30);
            boolean killed = CombatFx.strike(player, target, DAMAGE, launch, 2f, CombatFx.DeathStyle.EXECUTION, execute);
            if (execute) {
                execution(player, level, target, dir, killed);
            }
        });
    }

    /** Execution finisher. */
    private static void execution(ServerPlayer player, ServerLevel level, LivingEntity target, Vec3 dir, boolean killed) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        PowerFx.cinematic(player, target, target.position(), side.scale(4.5).add(0, 0.2, 0).add(dir.scale(-1.5)),
                side.scale(7).add(0, 3.5, 0).add(dir.scale(2)), 34, -12f, 0.85f);
        PowerFx.pose(player, PowerPose.PUNCH, 16);
        Vfx.sound(level, target.position(), ModSounds.POWER_FINISHER, 3f, 1f);
        CombatFx.slowmo(level, target.position(), 24, 0.22f, 20);
        if (!killed) {
            TimeManager.apply(target, 0.22f, 20, true);
        }
        ServerScheduler.schedule(26, () -> {
            Vec3 at = target.position().add(0, 0.5, 0);
            Vfx.send(level, VfxType.POWER_EXPLOSION, at, Vec3.ZERO, PowerFx.PURPLE, 0xFFE0FF, 4.2f, 30);
            Vfx.send(level, VfxType.SHOCKWAVE, at, new Vec3(0, 1, 0), PowerFx.MAGENTA, PowerFx.PINK, 10f, 18);
            Vec3 ground = dev.infinitygauntlet.util.TeleportUtil.groundBelow(level, at, 64);
            if (at.y - ground.y < 3) {
                PowerFx.cracks(level, ground.add(0, 0.04, 0), 5f, 120, PowerFx.MAGENTA); // only when it explodes on the ground
            }
            Vfx.sound(level, at, ModSounds.POWER_EXPLOSION, 3.5f, 0.75f);
            Vfx.camera(level, at, 40, 1.2f, 0, 0, 0xFFD8FF, 0.5f, 8);
            for (LivingEntity e : CombatFx.targets(player, at, 4.5)) {
                CombatFx.strike(player, e, 10f, e.position().subtract(at).normalize().scale(1.2).add(0, 0.7, 0), 1.2f, CombatFx.DeathStyle.RAGDOLL, false);
            }
        });
    }
}

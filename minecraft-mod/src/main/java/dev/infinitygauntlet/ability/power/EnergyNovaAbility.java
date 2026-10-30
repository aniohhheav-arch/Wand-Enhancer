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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Energy Nova: the caster rises off the ground, arms spread, while energy gathers around them and the world nearby
 * slows to a crawl. The camera circles them. Then everything is released at once in a sphere of force that tears the
 * picture apart with chromatic aberration and hurls everything away.
 */
public final class EnergyNovaAbility extends Ability {
    static final int CHARGE = 40;
    static final double RADIUS = 14;

    public EnergyNovaAbility() {
        super("energy_nova", InfinityStone.POWER, 300);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        double baseY = player.getY();
        Vec3 anchor = player.position();
        PowerFx.pose(player, PowerPose.SPREAD, CHARGE + 6);
        PowerFx.anim(ctx, "power_spread");
        PowerFx.cinematic(player, player, player.position().add(0, 1.2, 0), PowerFx.rel(player, 4.5, 0.6, -4.5), PowerFx.rel(player, -4.5, 2.2, -4.5),
                CHARGE + 10, 0f, 1.05f);
        Vfx.sound(level, anchor, ModSounds.POWER_NOVA_CHARGE, 3f, 1f);
        Vfx.send(level, VfxType.CHARGE, anchor.add(0, 3.6, 0), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 7f, CHARGE + 2);
        Vfx.send(level, VfxType.GROUND_GLYPH, anchor.add(0, 0.05, 0), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 4f, CHARGE + 6);
        for (int i = 0; i < CHARGE; i++) {
            int tick = i;
            ServerScheduler.schedule(i + 1, () -> {
                if (!player.isAlive() || player.level() != level) {
                    return;
                }
                double lift = (baseY + 3 - player.getY()) * 0.18;
                player.setDeltaMovement(0, lift, 0);
                player.hurtMarked = true;
                player.fallDistance = 0;
                if (tick % 4 == 0) {
                    for (LivingEntity e : CombatFx.targets(player, player.position(), RADIUS)) {
                        TimeManager.apply(e, 0.25f, 6, true);
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.04, 0));
                        e.hurtMarked = true;
                        if (tick % 12 == 0) {
                            CombatFx.react(level, e, CombatFx.Reaction.SUSPEND, Vec3.ZERO, 14);
                        }
                    }
                }
                if (tick % 10 == 0) {
                    Vfx.send(level, VfxType.DISTORTION, player.position().add(0, 1, 0), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 1.5f + tick * 0.08f, 12);
                    Vfx.shake(level, player.position(), 30, 0.15f + tick * 0.01f);
                }
            });
        }
        ServerScheduler.schedule(CHARGE + 1, () -> {
            if (player.isAlive() && player.level() == level) {
                release(player, level);
            }
        });
        return CastResult.SUCCESS;
    }

    private static void release(ServerPlayer player, ServerLevel level) {
        Vec3 c = player.position().add(0, 1, 0);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false, false));
        Vfx.send(level, VfxType.SPHERE_PULSE, c, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, (float) RADIUS + 4, 22);
        Vfx.send(level, VfxType.SPHERE_PULSE, c, Vec3.ZERO, PowerFx.MAGENTA, 0xFFE0FF, (float) RADIUS * 0.6f, 14);
        Vfx.send(level, VfxType.SHOCKWAVE, c, new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, (float) RADIUS * 1.7f, 20);
        Vfx.send(level, VfxType.SHOCKWAVE, c, new Vec3(1, 0.4, 0).normalize(), PowerFx.MAGENTA, 0xFFFFFF, (float) RADIUS * 1.3f, 18);
        Vfx.send(level, VfxType.SHOCKWAVE, c, new Vec3(0, 0.4, 1).normalize(), PowerFx.MAGENTA, 0xFFFFFF, (float) RADIUS * 1.3f, 18);
        Vfx.send(level, VfxType.SPARK_BURST, c, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 3f, 20);
        Vfx.sound(level, c, ModSounds.POWER_NOVA_RELEASE, 5f, 1f);
        Vfx.camera(level, c, 64, 1.4f, 1.3f, 10, 0xFFF0FF, 0.7f, 8);
        Vfx.glitch(level, c, 48, 1f);
        PowerFx.cracks(level, dev.infinitygauntlet.util.TeleportUtil.groundBelow(level, c, 8).add(0, 0.04, 0), 9f, 140, PowerFx.PURPLE);
        for (LivingEntity e : CombatFx.targets(player, c, RADIUS)) {
            TimeManager.clear(e);
            Vec3 away = e.getBoundingBox().getCenter().subtract(c);
            float falloff = (float) Math.max(0.3, 1 - away.length() / RADIUS);
            Vec3 dir = away.normalize();
            CombatFx.strike(player, e, 22f * falloff, dir.scale(2.6 * falloff + 0.4).add(0, 0.7, 0), 1.6f, CombatFx.DeathStyle.RAGDOLL, false);
            CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, dir, 30);
        }
    }
}

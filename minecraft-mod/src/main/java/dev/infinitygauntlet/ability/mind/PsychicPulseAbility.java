package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Psychic Pulse: a sphere of mental force bursts out of the caster, golden geometry hanging in the air behind it.
 * Everything nearby is shoved away and left dazed: creatures wander aimlessly, players reel.
 */
public final class PsychicPulseAbility extends Ability {
    static final double RADIUS = 10;

    public PsychicPulseAbility() {
        super("psychic_pulse", InfinityStone.MIND, 160);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        var player = ctx.player();
        var level = ctx.level();
        Vec3 c = player.position().add(0, 1, 0);
        PowerFx.pose(player, PowerPose.SPREAD, 14);
        PowerFx.anim(ctx, "mind_push");
        Vfx.send(level, VfxType.SPHERE_PULSE, c, Vec3.ZERO, MindManager.GOLD, 0xFFFFFF, (float) RADIUS, 16);
        Vfx.send(level, VfxType.SHOCKWAVE, player.position().add(0, 0.1, 0), new Vec3(0, 1, 0), MindManager.GOLD, MindManager.PALE, (float) RADIUS * 1.4f, 16);
        Vfx.send(level, VfxType.GEOMETRY_BURST, c, Vec3.ZERO, MindManager.GOLD, MindManager.PALE, (float) RADIUS * 0.8f, 30);
        Vfx.send(level, VfxType.GROUND_GLYPH, player.position().add(0, 0.05, 0), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 3.5f, 30);
        Vfx.sound(level, c, ModSounds.MIND_PULSE, 3f, 1f);
        Vfx.camera(level, c, 24, 0.5f, 1.08f, 10, 0xFFF0B0, 0.25f, 5);
        Vfx.glitch(level, c, 16, 0.4f);
        for (LivingEntity e : MindManager.targets(player, c, RADIUS)) {
            Vec3 away = e.position().subtract(player.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
            double falloff = 1.2 - e.position().distanceTo(player.position()) / RADIUS;
            e.setDeltaMovement(away.scale(1.4 * falloff + 0.3).add(0, 0.45, 0));
            e.hurtMarked = true;
            e.hurt(level.damageSources().indirectMagic(player, player), MindManager.damage(4f));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            if (e instanceof Mob mob) {
                MindManager.daze(mob, 100);
            } else if (e instanceof Player) {
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            }
        }
        return CastResult.SUCCESS;
    }
}

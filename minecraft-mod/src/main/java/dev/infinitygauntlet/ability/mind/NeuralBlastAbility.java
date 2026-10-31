package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Neural Blast: a bright golden charge gathers at the gauntlet, then a beam threaded with neural filaments strikes
 * the crosshair. The impact blooms into a circular golden explosion.
 */
public final class NeuralBlastAbility extends Ability {
    static final int CHARGE = 12;

    public NeuralBlastAbility() {
        super("neural_blast", InfinityStone.MIND, 100);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        var player = ctx.player();
        var level = ctx.level();
        PowerFx.pose(player, PowerPose.CHARGE, CHARGE);
        PowerFx.anim(ctx, "mind_focus");
        Vfx.send(level, VfxType.CHARGE, ctx.handPos(), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 1.8f, CHARGE + 2);
        Vfx.send(level, VfxType.GEOMETRY_BURST, ctx.handPos(), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 0.8f, CHARGE);
        Vfx.sound(level, ctx.handPos(), ModSounds.MIND_BEAM_CHARGE, 1.6f, 1f);
        ServerScheduler.schedule(CHARGE, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            LivingEntity target = ctx.pickLiving(48);
            Vec3 end = target != null ? target.getBoundingBox().getCenter() : ctx.aimPoint(48);
            Vec3 start = ctx.handPos();
            Vec3 dir = end.subtract(start).normalize();
            PowerFx.pose(player, PowerPose.PUSH, 10);
            PowerFx.anim(ctx, "mind_push");
            Vfx.send(level, VfxType.NEURAL_BEAM, start, end, MindManager.GOLD, MindManager.PALE, 0.32f, 12);
            Vfx.send(level, VfxType.SPHERE_PULSE, end, Vec3.ZERO, MindManager.GOLD, 0xFFFFFF, 3.5f, 12);
            Vfx.send(level, VfxType.SHOCKWAVE, end, dir, MindManager.GOLD, MindManager.PALE, 4.5f, 12);
            Vfx.send(level, VfxType.GEOMETRY_BURST, end, Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 2.6f, 18);
            Vfx.sound(level, start, ModSounds.MIND_BEAM, 2.5f, 1f);
            Vfx.sound(level, end, ModSounds.MIND_BURST, 2f, 1.1f);
            Vfx.camera(level, start, 6, 0.4f, 1.06f, 8, 0xFFF2B0, 0.2f, 3);
            if (target != null) {
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().indirectMagic(player, player), MindManager.damage(14f));
                target.setDeltaMovement(dir.scale(1.1).add(0, 0.35, 0));
                target.hurtMarked = true;
            }
            for (LivingEntity e : MindManager.targets(player, end, 3)) {
                if (e != target) {
                    e.hurt(level.damageSources().indirectMagic(player, player), MindManager.damage(6f));
                    e.setDeltaMovement(e.position().subtract(end).normalize().scale(0.7).add(0, 0.3, 0));
                    e.hurtMarked = true;
                }
            }
        });
        return CastResult.SUCCESS;
    }
}

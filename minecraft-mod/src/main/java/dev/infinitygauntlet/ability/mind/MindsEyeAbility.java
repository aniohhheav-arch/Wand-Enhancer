package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.network.MindStatePayload;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.world.phys.Vec3;

/**
 * Mind's Eye: for twenty seconds every living creature nearby shows as a golden silhouette through walls, labelled
 * with its distance, and markers around the crosshair point toward each one. Only the caster sees it.
 */
public final class MindsEyeAbility extends Ability {
    public static final int TICKS = 400;

    public MindsEyeAbility() {
        super("minds_eye", InfinityStone.MIND, 400);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ModNetwork.sendTo(ctx.player(), new MindStatePayload(MindStatePayload.MINDS_EYE, TICKS));
        PowerFx.pose(ctx.player(), PowerPose.TEMPLE, 16);
        PowerFx.anim(ctx, "mind_focus");
        Vfx.send(ctx.level(), VfxType.SPHERE_PULSE, ctx.player().getEyePosition(), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 12f, 18);
        Vfx.sound(ctx.level(), ctx.player().position(), ModSounds.MIND_EYE, 1.6f, 1f);
        return CastResult.SUCCESS;
    }
}

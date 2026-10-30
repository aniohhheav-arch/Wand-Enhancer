package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.power.GravityCoreEntity;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/** Gravity Impact: calls a singularity down on the aimed spot (see {@link GravityCoreEntity}). */
public final class GravityImpactAbility extends Ability {
    public GravityImpactAbility() {
        super("gravity_impact", InfinityStone.POWER, 320);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Vec3 ground = ctx.groundTarget(40, 22);
        GravityCoreEntity core = new GravityCoreEntity(ModEntities.GRAVITY_CORE.get(), ctx.level());
        core.setup(ctx.player(), GravityCoreEntity.DESCEND + GravityCoreEntity.AFTER, 3f);
        core.place(ground, 15, 9f);
        ctx.level().addFreshEntity(core);
        PowerFx.pose(ctx.player(), PowerPose.RAISE, GravityCoreEntity.DESCEND - 8);
        PowerFx.anim(ctx, "power_raise");
        Vfx.sound(ctx.level(), ctx.player().position(), ModSounds.POWER_CHARGE, 2f, 0.6f);
        ServerScheduler.schedule(GravityCoreEntity.DESCEND - 8, () -> {
            if (ctx.player().isAlive()) {
                PowerFx.pose(ctx.player(), PowerPose.SLAM, 16);
                PowerFx.anim(ctx, "power_slam");
            }
        });
        return CastResult.SUCCESS;
    }
}

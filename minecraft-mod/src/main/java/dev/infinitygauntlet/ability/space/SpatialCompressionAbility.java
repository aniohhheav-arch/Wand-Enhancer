package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.SpatialCompressionEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/** Launches a sphere of compressed space to the aimed point, where it pulls everything in and detonates. */
public final class SpatialCompressionAbility extends Ability {
    public SpatialCompressionAbility() {
        super("spatial_compression", InfinityStone.SPACE, 300);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Vec3 aim = ctx.aimPoint(32);
        Vec3 center = aim.subtract(ctx.look().scale(2.5)).add(0, 1.5, 0);
        SpatialCompressionEntity sphere = new SpatialCompressionEntity(ModEntities.SPATIAL_COMPRESSION.get(), ctx.level());
        sphere.setup(ctx.player(), 80, 2.6f);
        sphere.moveTo(center.x, center.y, center.z);
        ctx.level().addFreshEntity(sphere);
        Vfx.send(ctx.level(), dev.infinitygauntlet.vfx.VfxType.BEAM, ctx.handPos(), center, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), 0.3f, 8);
        return CastResult.SUCCESS;
    }
}

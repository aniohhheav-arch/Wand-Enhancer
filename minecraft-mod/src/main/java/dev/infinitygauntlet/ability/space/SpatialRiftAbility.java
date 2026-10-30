package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.SpatialRiftEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Tears an unstable rift into the ground beneath the target (or aimed point). */
public final class SpatialRiftAbility extends Ability {
    public SpatialRiftAbility() {
        super("spatial_rift", InfinityStone.SPACE, 200);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = ctx.pickLiving(40);
        Vec3 point = target != null ? target.position() : ctx.aimPoint(40);
        Vec3 ground = TeleportUtil.groundBelow(ctx.level(), point.add(0, 0.5, 0), 12);
        SpatialRiftEntity rift = new SpatialRiftEntity(ModEntities.SPATIAL_RIFT.get(), ctx.level());
        rift.setup(ctx.player(), 110, 3.2f);
        rift.moveTo(ground.x, ground.y + 0.05, ground.z);
        ctx.level().addFreshEntity(rift);
        Vfx.link(ctx.level(), ctx.handPos(), ground, InfinityStone.SPACE, 10);
        return CastResult.SUCCESS;
    }
}

package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.TemporalLoopEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/** Marks a spot with a time anomaly that keeps snapping whoever enters it back to the moment they arrived. */
public final class TemporalLoopAbility extends Ability {
    public TemporalLoopAbility() {
        super("temporal_loop", InfinityStone.TIME, 500);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Vec3 ground = ctx.groundTarget(32, 14);
        TemporalLoopEntity loop = new TemporalLoopEntity(ModEntities.TEMPORAL_LOOP.get(), ctx.level());
        loop.setup(ctx.player(), ServerConfig.LOOP_SECONDS.get() * 20, 3.5f);
        loop.moveTo(ground.x, ground.y + 0.03, ground.z);
        ctx.level().addFreshEntity(loop);
        Vfx.link(ctx.level(), ctx.handPos(), ground.add(0, 0.3, 0), InfinityStone.TIME, 10);
        return CastResult.SUCCESS;
    }
}

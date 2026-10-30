package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.TimeFieldEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/** Lays a field of slowed time on the ground where the caster aims. */
public final class TimeSlowAbility extends Ability {
    public TimeSlowAbility() {
        super("time_slow", InfinityStone.TIME, 300);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Vec3 ground = ctx.groundTarget(32, 14);
        TimeFieldEntity field = new TimeFieldEntity(ModEntities.TIME_FIELD.get(), ctx.level());
        field.setup(ctx.player(), ServerConfig.SLOW_FIELD_SECONDS.get() * 20, 7f);
        field.configure(TimeFieldEntity.SHAPE_DISC, ServerConfig.SLOW_FIELD_SCALE.get().floatValue());
        field.moveTo(ground.x, ground.y + 0.02, ground.z);
        ctx.level().addFreshEntity(field);
        Vfx.link(ctx.level(), ctx.handPos(), ground.add(0, 0.5, 0), InfinityStone.TIME, 10);
        return CastResult.SUCCESS;
    }
}

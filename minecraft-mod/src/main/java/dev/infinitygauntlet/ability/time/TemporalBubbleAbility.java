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

/** A great sphere in which time almost stops; the caster can move freely outside it. */
public final class TemporalBubbleAbility extends Ability {
    public TemporalBubbleAbility() {
        super("temporal_bubble", InfinityStone.TIME, 700);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        float radius = ServerConfig.BUBBLE_RADIUS.get().floatValue();
        Vec3 ground = ctx.groundTarget(48, radius + 8);
        // keep the caster outside: push the centre away if they are standing inside it
        Vec3 center = ground.add(0, radius * 0.45, 0);
        Vec3 away = center.subtract(ctx.player().position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.length() < radius + 1.5) {
            Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(ctx.look().x, 0, ctx.look().z).normalize() : flat.normalize();
            center = ctx.player().position().add(dir.scale(radius + 1.5)).add(0, radius * 0.45, 0);
        }
        TimeFieldEntity bubble = new TimeFieldEntity(ModEntities.TIME_FIELD.get(), ctx.level());
        bubble.setup(ctx.player(), ServerConfig.BUBBLE_SECONDS.get() * 20, radius);
        bubble.configure(TimeFieldEntity.SHAPE_SPHERE, ServerConfig.BUBBLE_SCALE.get().floatValue());
        bubble.moveTo(center.x, center.y, center.z);
        ctx.level().addFreshEntity(bubble);
        Vfx.link(ctx.level(), ctx.handPos(), center, InfinityStone.TIME, 12);
        Vfx.camera(ctx.level(), center, radius * 4, 0.3f, 0, 0, 0, 0, 0);
        return CastResult.SUCCESS;
    }
}

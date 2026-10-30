package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.SpatialPrisonEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Seals the aimed creature inside six rotating portal rings, suspended in the air. */
public final class SpatialPrisonAbility extends Ability {
    public SpatialPrisonAbility() {
        super("spatial_prison", InfinityStone.SPACE, 400);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = ctx.pickLiving(32);
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        if (!ctx.level().getEntitiesOfClass(SpatialPrisonEntity.class, target.getBoundingBox().inflate(2), p -> p.target() == target).isEmpty()) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.prison.already"));
        }
        float radius = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.75f + 0.6f;
        Vec3 center = target.position().add(0, target.getBbHeight() / 2 + 1.2, 0);
        SpatialPrisonEntity prison = new SpatialPrisonEntity(ModEntities.SPATIAL_PRISON.get(), ctx.level());
        prison.setup(ctx.player(), ServerConfig.PRISON_DURATION_SECONDS.get() * 20, radius);
        prison.setTarget(target);
        prison.moveTo(center.x, center.y, center.z);
        ctx.level().addFreshEntity(prison);
        Vfx.link(ctx.level(), ctx.handPos(), center, InfinityStone.SPACE, 12);
        Vfx.shockwave(ctx.level(), center, new Vec3(0, 1, 0), InfinityStone.SPACE, radius * 2.5f, 12);
        return CastResult.success(Component.translatable("message.infinitygauntlet.prison.sealed", target.getDisplayName()));
    }
}

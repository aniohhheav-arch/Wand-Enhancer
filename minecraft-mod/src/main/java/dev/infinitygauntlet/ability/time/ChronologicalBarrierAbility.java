package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.ChronoBarrierEntity;
import dev.infinitygauntlet.registry.ModEntities;
import org.jetbrains.annotations.Nullable;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;

/** Raises a clockwork shield that suspends incoming projectiles; casting again dismisses it and fires them back. */
public final class ChronologicalBarrierAbility extends Ability {
    public ChronologicalBarrierAbility() {
        super("chronological_barrier", InfinityStone.TIME, 600);
    }

    @Nullable
    private static ChronoBarrierEntity active(AbilityContext ctx) {
        return ctx.level().getEntitiesOfClass(ChronoBarrierEntity.class, ctx.player().getBoundingBox().inflate(8),
                b -> b.ownerEntity() == ctx.player() && b.isAlive()).stream().findFirst().orElse(null);
    }

    @Override
    public boolean bypassesCooldown(AbilityContext ctx) {
        return active(ctx) != null;
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ChronoBarrierEntity existing = active(ctx);
        if (existing != null) {
            int n = existing.suspendedCount();
            existing.dismiss();
            return CastResult.utility(Component.translatable("message.infinitygauntlet.barrier.released", n));
        }
        ChronoBarrierEntity barrier = new ChronoBarrierEntity(ModEntities.CHRONO_BARRIER.get(), ctx.level());
        barrier.setup(ctx.player(), ServerConfig.BARRIER_SECONDS.get() * 20, 2.1f);
        barrier.bind(ctx.player());
        ctx.level().addFreshEntity(barrier);
        return CastResult.success(Component.translatable("message.infinitygauntlet.barrier.raised"));
    }
}

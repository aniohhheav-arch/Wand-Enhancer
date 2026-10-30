package dev.infinitygauntlet.finisher;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** A stone's signature finisher as a castable ability: it only works on a target weakened enough to be finished. */
public final class FinisherAbility extends Ability {
    private final FinisherType type;

    public FinisherAbility(String name, FinisherType type, int cooldown) {
        super(name, type.stone, cooldown);
        this.type = type;
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        if (!FinisherManager.enabled()) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.finisher.disabled"));
        }
        LivingEntity target = ctx.pickLiving(24);
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        if (!FinisherManager.canFinish(ctx.player(), target, 0)) {
            int percent = (int) Math.round(ServerConfig.FINISHER_HEALTH_THRESHOLD.get() * 100);
            return CastResult.fail(Component.translatable("message.infinitygauntlet.finisher.too_strong", target.getDisplayName(), percent));
        }
        FinisherManager.start(ctx.player(), target, type);
        return CastResult.SUCCESS;
    }
}

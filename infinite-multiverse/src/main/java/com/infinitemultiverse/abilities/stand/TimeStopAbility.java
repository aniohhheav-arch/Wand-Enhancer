package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.stand.StandAction;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.TimeStopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Toggle: press to stop time, press again to resume. The user keeps full use of every other ability while time is
 * stopped. Survival pays upkeep and earns a cooldown that grows with how long time was held; creative is free.
 */
public final class TimeStopAbility extends StandAbility {
    private static final int POSE_TICKS = 14;

    private final float durationScale;
    private final String shoutKey;

    public TimeStopAbility(float durationScale, String shoutKey) {
        super(ActivationType.TOGGLE);
        this.durationScale = durationScale;
        this.shoutKey = shoutKey;
    }

    @Override
    public float upkeepPerSecond() {
        return MultiverseConfig.SERVER.timeStopUpkeep.get().floatValue();
    }

    @Override
    public int toggleCooldown(ServerPlayer player, int activeTicks) {
        if (player.isCreative()) {
            return 0;
        }
        return cooldownTicks() + (int) Math.round(activeTicks * MultiverseConfig.SERVER.timeStopCooldownPerTick.get());
    }

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        if (TimeStopManager.isFrozen(ctx.player())) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.time_already_stopped"));
            return false;
        }
        int cap = Math.round(MultiverseConfig.SERVER.timeStopMaxDuration.get() * durationScale);
        if (stand.isReady()) {
            stand.startAction(StandAction.TIME_STOP, POSE_TICKS);
        }
        TimeStopManager.start(ctx.player(), MultiverseConfig.SERVER.timeStopRadius.get(), cap,
                Component.translatable(shoutKey).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return true;
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        // Time resumed on its own (cap reached, Stand gone): end the toggle so the cooldown starts.
        if (activeTicks > 1 && !TimeStopManager.isActive(ctx.player())) {
            AbilityManager.deactivate(ctx.player(), ctx.data(), this, DeactivationReason.MANUAL);
        }
    }

    @Override
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
        TimeStopManager.endAll(ctx.player());
    }
}

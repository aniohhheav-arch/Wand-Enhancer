package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.stand.StandAction;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.TimeStopManager;
import net.minecraft.network.chat.Component;

/** Stops time around the user. Everything else freezes; damage dealt to frozen creatures lands when time resumes. */
public final class TimeStopAbility extends StandAbility {
    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.timeStop.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.timeStop.cooldown();
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        if (TimeStopManager.isFrozen(ctx.player())) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.time_already_stopped"));
            return false;
        }
        int duration = MultiverseConfig.SERVER.timeStopDuration.get();
        stand.startAction(StandAction.TIME_STOP, duration);
        TimeStopManager.start(ctx.player(), MultiverseConfig.SERVER.timeStopRadius.get(), duration);
        return true;
    }
}

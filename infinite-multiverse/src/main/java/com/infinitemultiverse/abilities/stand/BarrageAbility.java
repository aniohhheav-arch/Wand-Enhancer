package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.infinitemultiverse.stand.StandAction;
import com.infinitemultiverse.stand.StandEntity;

/** The Stand steps forward and unleashes a rapid punch barrage: a hit every 2 ticks on everything in front. */
public final class BarrageAbility extends StandAbility {
    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.barrage.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.barrage.cooldown();
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        stand.startAction(StandAction.BARRAGE, MultiverseConfig.SERVER.barrageDuration.get());
        MultiverseVfx.shout(ctx.level(), ctx.player().position(),
                Component.translatable("message.infinitemultiverse.shout.ora").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), 24.0);
        return true;
    }
}

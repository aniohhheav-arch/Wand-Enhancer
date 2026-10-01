package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.stand.StandAction;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The Stand steps forward and unleashes a rush of punches: a hit every 2 ticks on everything in front, with its battle cry. */
public final class BarrageAbility extends StandAbility {
    private final String shoutKey;
    private final ChatFormatting shoutColor;

    public BarrageAbility(String shoutKey, ChatFormatting shoutColor) {
        this.shoutKey = shoutKey;
        this.shoutColor = shoutColor;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        stand.startAction(StandAction.BARRAGE, MultiverseConfig.SERVER.barrageDuration.get());
        MultiverseVfx.shout(ctx.level(), ctx.player().position(),
                Component.translatable(shoutKey).withStyle(shoutColor, ChatFormatting.BOLD), 24.0);
        return true;
    }
}

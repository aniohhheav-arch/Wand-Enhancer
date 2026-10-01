package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandManager;
import net.minecraft.network.chat.Component;

/** An ability performed by the user's manifested Stand. Unlocked by awakening a Stand that grants it. */
public abstract class StandAbility extends Ability {
    protected StandAbility() {
        super(MultiverseSystem.STANDS, ActivationType.INSTANT, false);
    }

    @Override
    public final boolean activate(AbilityContext ctx) {
        StandEntity stand = StandManager.get(ctx.player());
        if (stand == null) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.stand_not_manifested"));
            return false;
        }
        if (!stand.isReady()) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.stand_busy"));
            return false;
        }
        return activateWithStand(ctx, stand);
    }

    protected abstract boolean activateWithStand(AbilityContext ctx, StandEntity stand);
}

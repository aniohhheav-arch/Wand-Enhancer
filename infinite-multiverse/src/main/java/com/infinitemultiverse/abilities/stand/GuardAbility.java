package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.stand.StandAction;
import com.infinitemultiverse.stand.StandEntity;

/** The Stand plants itself in front of you: catches incoming projectiles and blunts melee hits. */
public final class GuardAbility extends StandAbility {
    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        stand.startAction(StandAction.GUARD, MultiverseConfig.SERVER.guardDuration.get());
        return true;
    }
}

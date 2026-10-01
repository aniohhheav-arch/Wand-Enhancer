package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.world.Blink;
import com.infinitemultiverse.stand.StandEntity;

/** Made in Heaven's speed covers 24 blocks in an instant, to the nearest safe spot along your gaze. */
public final class HeavenBlinkAbility extends StandAbility {
    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        return Blink.perform(ctx, 24.0);
    }
}

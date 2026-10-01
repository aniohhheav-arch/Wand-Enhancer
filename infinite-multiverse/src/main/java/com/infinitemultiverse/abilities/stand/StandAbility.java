package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandManager;
import net.minecraft.network.chat.Component;

/**
 * An ability performed by the user's manifested Stand. Unlocked by awakening a Stand that grants it.
 * Cost and cooldown come from {@code stands.abilities.<id path>} in the server config.
 */
public abstract class StandAbility extends Ability {
    protected StandAbility() {
        this(ActivationType.INSTANT);
    }

    protected StandAbility(ActivationType type) {
        super(MultiverseSystem.STANDS, type, false);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.standTuning(id().getPath()).cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.standTuning(id().getPath()).cooldown();
    }

    @Override
    public final boolean activate(AbilityContext ctx) {
        StandEntity stand = StandManager.get(ctx.player());
        if (stand == null) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.stand_not_manifested"));
            return false;
        }
        if (requiresReadyStand() && !stand.isReady()) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.stand_busy"));
            return false;
        }
        return activateWithStand(ctx, stand);
    }

    /** Whether the Stand must be idle. Abilities that don't move the Stand can be used mid-attack. */
    protected boolean requiresReadyStand() {
        return true;
    }

    protected abstract boolean activateWithStand(AbilityContext ctx, StandEntity stand);
}

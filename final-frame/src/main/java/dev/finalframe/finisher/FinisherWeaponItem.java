package dev.finalframe.finisher;

import net.minecraft.world.item.Item;

/** Base class for items that can initiate a cinematic finisher. */
public abstract class FinisherWeaponItem extends Item {
    protected FinisherWeaponItem(Properties properties) {
        super(properties);
    }

    public abstract FinisherDefinition finisher();
}

package dev.finalframe.finisher;

import java.util.List;
import net.minecraft.world.item.Item;

/** Base class for items that can initiate a cinematic finisher. */
public abstract class FinisherWeaponItem extends Item {
    protected FinisherWeaponItem(Properties properties) {
        super(properties);
    }

    /** Every finisher this weapon can perform, in priority order. The first whose condition holds is played. */
    public abstract List<FinisherDefinition> finishers();
}

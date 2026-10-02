package dev.riftverse.entity.creature;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/** A moonstone guardian. Peaceful toward travellers; it smashes anything hostile that wanders into its plains. */
public class LunarGolemEntity extends IronGolem {
    public LunarGolemEntity(EntityType<? extends LunarGolemEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 140.0).add(Attributes.GRAVITY, 0.04);
    }
}

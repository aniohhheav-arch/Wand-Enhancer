package dev.riftverse.item;

import dev.riftverse.registry.RvArmorMaterials;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

public enum ArmorSet {
    RIFT_WALKER("rift_walker", 0xB070FF, 0x40E0FF),
    VOYAGER("voyager", 0x7DF9FF, 0xFFC14D),
    EVENT_HORIZON("event_horizon", 0xFF8A3A, 0x5A2AFF),
    ASTRAL("astral", 0xFFE6A0, 0x8F6BFF);

    public final String id;
    public final int primary;
    public final int secondary;

    ArmorSet(String id, int primary, int secondary) {
        this.id = id;
        this.primary = primary;
        this.secondary = secondary;
    }

    public static ArmorSet of(Holder<ArmorMaterial> material) {
        if (material.is(RvArmorMaterials.RIFT_WALKER.getKey())) return RIFT_WALKER;
        if (material.is(RvArmorMaterials.VOYAGER.getKey())) return VOYAGER;
        if (material.is(RvArmorMaterials.EVENT_HORIZON.getKey())) return EVENT_HORIZON;
        return ASTRAL;
    }
}

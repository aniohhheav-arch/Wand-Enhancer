package dev.mysticarts.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The three robe sets. Wearing every piece of a set (helmet optional where the set has none) grants its passive. */
public enum Vestments {
    /** +50 max mystic energy, +50% mystic regeneration. */
    SORCERER_SUPREME("sorcerer_supreme", 0x2B4C9B, 50f, 1.5f, false),
    /** +80 max mystic energy, mystic costs -20%. Includes the hood. */
    ANCIENT_ONE("ancient_one", 0xE8B23A, 80f, 1.2f, true),
    /** +120 max mystic energy, mystic spells hit 25% harder, but regeneration is slower. */
    DARK_DIMENSION("dark_dimension", 0x5A1E8A, 120f, 0.8f, false);

    public final String id;
    public final int color;
    public final float bonusEnergy;
    public final float regen;
    public final boolean needsHelmet;

    Vestments(String id, int color, float bonusEnergy, float regen, boolean needsHelmet) {
        this.id = id;
        this.color = color;
        this.bonusEnergy = bonusEnergy;
        this.regen = regen;
        this.needsHelmet = needsHelmet;
    }

    @Nullable
    public static Vestments worn(LivingEntity entity) {
        Vestments found = null;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = entity.getItemBySlot(slot);
            if (!(s.getItem() instanceof SorcererArmorItem a)) return null;
            if (found == null) found = a.set();
            else if (found != a.set()) return null;
        }
        if (found != null && found.needsHelmet) {
            ItemStack h = entity.getItemBySlot(EquipmentSlot.HEAD);
            if (!(h.getItem() instanceof SorcererArmorItem a) || a.set() != found) return null;
        }
        return found;
    }
}

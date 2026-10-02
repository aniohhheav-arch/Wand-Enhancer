package com.infinitemultiverse.core.energy;

import com.infinitemultiverse.core.MultiverseSystem;
import net.minecraft.network.chat.Component;

/** Separate resource pools. Each ability spends from the pool of its system; all share the configured max and regen. */
public enum EnergyPool {
    MULTIVERSE("energy", 0x7FE9FF, 0x2F6BFF),
    CURSED("cursed_energy", 0x6D8BFF, 0x1A1446),
    MANA("mana", 0xFFC46A, 0xC4501C),
    COSMIC("cosmic_energy", 0xFFE27A, 0x9A3AFF);

    private final String id;
    private final int topColor;
    private final int bottomColor;

    EnergyPool(String id, int topColor, int bottomColor) {
        this.id = id;
        this.topColor = topColor;
        this.bottomColor = bottomColor;
    }

    public String id() {
        return id;
    }

    public int topColor() {
        return topColor;
    }

    public int bottomColor() {
        return bottomColor;
    }

    public Component displayName() {
        return Component.translatable("energy_pool.infinitemultiverse." + id);
    }

    public static EnergyPool forSystem(MultiverseSystem system) {
        return switch (system) {
            case CURSED_TECHNIQUES -> CURSED;
            case MYSTIC_ARTS -> MANA;
            case INFINITY_GAUNTLET -> COSMIC;
            default -> MULTIVERSE;
        };
    }
}

package dev.arcana.item;

import net.minecraft.resources.ResourceLocation;

/** Magical cores; each adds an on-hit effect to every spell. */
public enum Core {
    NONE("none", null),
    EMBER("ember", "arcana:core_ember"),
    FROST("frost", "arcana:core_frost"),
    VOLT("volt", "arcana:core_volt"),
    SPIRIT("spirit", "arcana:core_spirit");

    public final String id;
    public final ResourceLocation item;

    Core(String id, String item) {
        this.id = id;
        this.item = item == null ? null : ResourceLocation.parse(item);
    }

    public static Core byIndex(int i) {
        Core[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

package dev.arcana.item;

import net.minecraft.resources.ResourceLocation;

/** Spell modifier runes (two slots per wand). */
public enum Rune {
    NONE("none", null, 1, 1, 1, 1, 0),
    AMPLIFY("amplify", "arcana:rune_amplify", 1.25, 1.15, 1, 1, 0),
    EFFICIENT("efficient", "arcana:rune_efficient", 1, 0.75, 1, 1, 0),
    SWIFT("swift", "arcana:rune_swift", 1, 1, 0.75, 1, 0),
    WIDE("wide", "arcana:rune_wide", 0.92, 1, 1, 1.3, 0),
    LEECH("leech", "arcana:rune_leech", 1, 1, 1, 1, 0.15);

    public final String id;
    public final ResourceLocation item;
    public final double damage, mana, cooldown, radius, leech;

    Rune(String id, String item, double damage, double mana, double cooldown, double radius, double leech) {
        this.id = id;
        this.item = item == null ? null : ResourceLocation.parse(item);
        this.damage = damage;
        this.mana = mana;
        this.cooldown = cooldown;
        this.radius = radius;
        this.leech = leech;
    }

    public static Rune byIndex(int i) {
        Rune[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

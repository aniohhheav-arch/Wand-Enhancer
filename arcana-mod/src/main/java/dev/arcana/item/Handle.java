package dev.arcana.item;

import net.minecraft.resources.ResourceLocation;

/** Wand handle materials; each carries one passive trait. */
public enum Handle {
    OAK("oak", "minecraft:stick", 1.0, 1.0, 1.0, 0),
    BONE("bone", "minecraft:bone", 1.0, 0.9, 1.0, 0),
    GILDED("gilded", "minecraft:gold_ingot", 1.0, 1.0, 1.0, 30),
    OBSIDIAN("obsidian", "minecraft:obsidian", 1.12, 1.0, 1.0, 0),
    ECHO("echo", "minecraft:echo_shard", 1.0, 1.0, 0.88, 0);

    public final String id;
    public final ResourceLocation item;
    public final double damage, cooldown, mana;
    public final int bonusMana;

    Handle(String id, String item, double damage, double cooldown, double mana, int bonusMana) {
        this.id = id;
        this.item = ResourceLocation.parse(item);
        this.damage = damage;
        this.cooldown = cooldown;
        this.mana = mana;
        this.bonusMana = bonusMana;
    }

    public static Handle byIndex(int i) {
        Handle[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

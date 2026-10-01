package dev.riftverse.universe;

import dev.riftverse.Riftverse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** The "prime" reality families. Each defines a default identity that universes are mutated from. */
public enum Archetype {
    NEON_SPRAWL("neon_sprawl", "Neon Sprawl", 0x00F0FF, new String[] {"Sprawl", "Arcology", "Megaplex", "Grid", "Undercity"}),
    XENOFLORA("xenoflora", "Xenoflora Wilds", 0x5CFF9D, new String[] {"Wilds", "Canopy", "Verdance", "Bloomlands", "Thicket"}),
    SKYSHATTER("skyshatter", "Skyshatter Isles", 0x9FD8FF, new String[] {"Isles", "Skyreach", "Drift", "Aerie", "Archipelago"}),
    THALASSIC("thalassic", "Thalassic Expanse", 0x2A7FFF, new String[] {"Expanse", "Deep", "Tidesea", "Abyss", "Maelstrom"}),
    PRISMATIC("prismatic", "Prismatic Reach", 0xD69CFF, new String[] {"Reach", "Lattice", "Geode", "Refraction", "Spirefield"}),
    ASHEN("ashen", "Ashen Remnant", 0xC27B4A, new String[] {"Remnant", "Wastes", "Graveworld", "Cinderbelt", "Husk"}),
    ASTRAL("astral", "Astral Vastness", 0x8F6BFF, new String[] {"Vastness", "Firmament", "Starfall", "Empyrean", "Zenith"}),
    CORRUPTED("corrupted", "Corrupted Sector", 0x39FF14, new String[] {"Sector", "Fault", "Overflow", "Null-Space", "Segfault"}),
    ELDER("elder", "Elder Dominion", 0xFFC14D, new String[] {"Dominion", "Sanctum", "Dynasty", "Reliquary", "Throneworld"}),
    INVERTED("inverted", "Inverted Heights", 0x7AF0D0, new String[] {"Heights", "Updrift", "Antipode", "Skyfall", "Weightless"}),
    HOLLOW("hollow", "The Hollow Dark", 0x6A3AAA, new String[] {"Hollow", "Umbra", "Nightfold", "Silence", "Void"}),
    SOMNIUM("somnium", "Somnium", 0xFFB3E6, new String[] {"Somnium", "Reverie", "Daydream", "Lullaby", "Mirage"}),
    CINDER("cinder", "Cinder Forge", 0xFF5A1F, new String[] {"Forge", "Inferno", "Caldera", "Pyre", "Furnace"}),
    RIME("rime", "Rime Eternal", 0xA8E8FF, new String[] {"Eternal", "Glacier", "Frostfall", "Whiteout", "Permafrost"});

    public final String id;
    public final String displayName;
    public final int signatureColor;
    public final String[] epithets;
    public final ResourceKey<Biome> biome;

    Archetype(String id, String displayName, int signatureColor, String[] epithets) {
        this.id = id;
        this.displayName = displayName;
        this.signatureColor = signatureColor;
        this.epithets = epithets;
        this.biome = ResourceKey.create(Registries.BIOME, Riftverse.id(id));
    }

    public static Archetype byId(int i) {
        return values()[Math.floorMod(i, values().length)];
    }

    public static Archetype byName(String name) {
        for (Archetype a : values()) {
            if (a.id.equalsIgnoreCase(name) || a.name().equalsIgnoreCase(name)) return a;
        }
        return null;
    }
}

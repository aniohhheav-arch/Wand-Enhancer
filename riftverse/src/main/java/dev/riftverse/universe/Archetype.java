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
    RIME("rime", "Rime Eternal", 0xA8E8FF, new String[] {"Eternal", "Glacier", "Frostfall", "Whiteout", "Permafrost"}),
    SUNSCAR("sunscar", "Sunscar Dunes", 0xFFC24D, new String[] {"Dunes", "Sandsea", "Scorch", "Mirage", "Sirocco"}),
    MESA("mesa", "Rust Mesa", 0xC85A2A, new String[] {"Mesa", "Badlands", "Gorge", "Rustlands", "Hoodoo"}),
    CORAL("coral", "Coral Shallows", 0xFF6FA5, new String[] {"Shallows", "Atoll", "Reefscape", "Lagoon", "Cay"}),
    MYCELIA("mycelia", "Myco Hollows", 0xBB7AF0, new String[] {"Hollows", "Sporefields", "Underbloom", "Fungarium", "Capworld"}),
    CLOCKWORK("clockwork", "Clockwork Reach", 0xCB9A3C, new String[] {"Reach", "Mechanism", "Gearworks", "Cogwheel", "Orrery"}),
    SANGUINE("sanguine", "Sanguine Expanse", 0xC01030, new String[] {"Expanse", "Carnage", "Scarlet", "Veinworld", "Pulse"}),
    CONFECTION("confection", "Confection", 0xFF8FD0, new String[] {"Confection", "Sugarfall", "Candyland", "Sweetscape", "Frosting"}),
    TEMPEST("tempest", "Tempest Reach", 0x6FA8FF, new String[] {"Reach", "Stormfront", "Galeworld", "Thunderhead", "Squall"}),
    MIRE("mire", "Fenrot Mire", 0x7AA83C, new String[] {"Mire", "Fen", "Swampdeep", "Bog", "Marshland"}),
    OBSIDIAN("obsidian", "Obsidian Spires", 0x4A2A6A, new String[] {"Spires", "Glasswaste", "Shardfall", "Blacklands", "Cinderglass"}),
    VERDIGRIS("verdigris", "Verdigris Ruins", 0x4AC8A8, new String[] {"Ruins", "Patina", "Overgrowth", "Greenbronze", "Mossworks"}),
    RADIANCE("radiance", "Radiant Expanse", 0xFFF0C8, new String[] {"Expanse", "Empyrean", "Aureole", "Dawnlands", "Halo"}),
    FERROUS("ferrous", "Ferrous Wastes", 0x9AA2AC, new String[] {"Wastes", "Slagworld", "Ironfall", "Scrapheap", "Oxide"}),
    BLOOM("bloom", "Spectral Bloom", 0xFF5AD0, new String[] {"Bloom", "Petalfall", "Floradream", "Blossomreach", "Pollen"}),
    AURORA("aurora", "Aurora Tundra", 0x7AFFC8, new String[] {"Tundra", "Borealis", "Polar Veil", "Lightfall", "Hush"}),
    MAGMA("magma", "Magma Throne", 0xFF4A10, new String[] {"Throne", "Caldera", "Lavaheart", "Moltenreach", "Searing"}),
    PRIMEVAL("primeval", "Primeval Jungle", 0x3ABF3A, new String[] {"Jungle", "Wildwood", "Primordia", "Greenhell", "Overgrowth"}),
    NEBULA("nebula", "Nebula Drift", 0xC050FF, new String[] {"Drift", "Starcloud", "Gasreach", "Cosmos", "Nursery"}),
    WASTELAND("wasteland", "Fallout Wastes", 0x9A9A40, new String[] {"Wastes", "Fallout", "Ruinlands", "Deadzone", "Rustbelt"}),
    GEODE("geode", "Amethyst Geode", 0xA060FF, new String[] {"Geode", "Crystalheart", "Hollowgem", "Shardvault", "Resonance"}),
    DEEPDARK("deepdark", "Sculk Depths", 0x0A6A7A, new String[] {"Depths", "Echo", "Underdark", "Sculkreach", "Whisper"}),
    SAVANNA("savanna", "Golden Savanna", 0xE0B040, new String[] {"Savanna", "Plains", "Sunveld", "Grassreach", "Pride"}),
    CHROME("chrome", "Chrome Metropolis", 0xE0F0FF, new String[] {"Metropolis", "Chromeplex", "Spirecity", "Utopia", "Mirrorgrid"}),
    LUNAR("lunar", "Lunar Plains", 0xD8D8E8, new String[] {"Plains", "Mare", "Moonfield", "Selene", "Silence"}),
    HIVE("hive", "Golden Hive", 0xFFC020, new String[] {"Hive", "Honeycomb", "Combworld", "Nectar", "Swarm"}),
    MIRROR("mirror", "Mirror Realm", 0xB0E0FF, new String[] {"Realm", "Reflection", "Stillwater", "Looking-Glass", "Glassworld"});

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

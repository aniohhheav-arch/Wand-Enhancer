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
    MIRROR("mirror", "Mirror Realm", 0xB0E0FF, new String[] {"Realm", "Reflection", "Stillwater", "Looking-Glass", "Glassworld"}),
    STARFORGE("starforge", "Starforge Foundry", 0x9AC8FF, new String[] {"Foundry", "Anvilworld", "Forgeheart", "Smeltery", "Starworks"}),
    FROSTGLASS("frostglass", "Frostglass Caverns", 0xE0FFFF, new String[] {"Caverns", "Icecathedral", "Shiverhall", "Glasswood", "Coldlight"}),
    ECHO("echo", "Echoing Abyss", 0x30E0F0, new String[] {"Abyss", "Resonance", "Hollowsong", "Murmur", "Reverb"}),
    MOLTENSEA("moltensea", "Molten Sea", 0xFF6A10, new String[] {"Sea", "Lavawaste", "Firetide", "Burning Deep", "Cinderocean"}),
    CLOUDKINGDOM("cloudkingdom", "Cloud Kingdom", 0xF0F8FF, new String[] {"Kingdom", "Skyhold", "Nimbus", "Highcastle", "Cumulus"}),
    DROWNED("drowned", "Drowned Ruins", 0x2A8AC0, new String[] {"Ruins", "Sunken Empire", "Tidetomb", "Atlantis", "Deepcrown"}),
    TOXIC("toxic", "Toxic Bog", 0x80FF20, new String[] {"Bog", "Plaguefen", "Acidmire", "Blightmarsh", "Sludge"}),
    CRIMSONWEALD("crimsonweald", "Crimson Weald", 0xD02040, new String[] {"Weald", "Bloodwood", "Scarletgrove", "Heartwood", "Redcanopy"}),
    WARPEDWEALD("warpedweald", "Warped Weald", 0x20D0B0, new String[] {"Weald", "Twistwood", "Teal Tangle", "Warpgrove", "Wyrdwood"}),
    GOLDENTEMPLE("goldentemple", "Golden Temple", 0xFFC14D, new String[] {"Temple", "Sunsanctum", "Aurum", "Godsreach", "Gildhall"}),
    RAINBOW("rainbow", "Rainbow Reach", 0xFF70C0, new String[] {"Reach", "Spectrum", "Prismfall", "Chromaland", "Hues"}),
    VOIDGLASS("voidglass", "Voidglass Expanse", 0x5A30A0, new String[] {"Expanse", "Glassnight", "Obscura", "Blackmirror", "Nullsea"}),
    RUNIC("runic", "Runic Plateau", 0xFFC14D, new String[] {"Plateau", "Runeheight", "Scriptlands", "Glyphmesa", "Spellstone"}),
    EMBERSTEPPE("embersteppe", "Ember Steppe", 0xFF6020, new String[] {"Steppe", "Cinderplain", "Ashveld", "Smoulder", "Brandland"}),
    PASTEL("pastel", "Pastel Dreamscape", 0xFFC0E0, new String[] {"Dreamscape", "Softlands", "Cottonworld", "Lullaby", "Sherbet"}),
    PETRIFIED("petrified", "Petrified Starwood", 0x9A7AC0, new String[] {"Starwood", "Stoneforest", "Fossilgrove", "Timberrock", "Agate"}),
    CRYSTALOCEAN("crystalocean", "Crystal Ocean", 0xA0F0FF, new String[] {"Ocean", "Glasswater", "Shardsea", "Lucid Deep", "Clearwater"}),
    DUSKHIGHLANDS("duskhighlands", "Dusk Highlands", 0xFF9060, new String[] {"Highlands", "Twilight Peaks", "Gloaming", "Vesper", "Duskcrown"}),
    NEONJUNGLE("neonjungle", "Neon Jungle", 0x00F0FF, new String[] {"Jungle", "Glowwild", "Neonvine", "Lumina", "Biolume"}),
    CELESTIAL("celestial", "Celestial Court", 0xFFF0C8, new String[] {"Court", "Throne of Stars", "Empyreal", "Seraphim", "Firmament"}),
    LUNARCOLONY("lunarcolony", "Lunar Colony", 0xD0E8FF, new String[] {"Colony", "Outpost", "Moonbase", "Domeworld", "Selenopolis"}),
    CORALKING("coralking", "Coral Kingdom", 0xFF70A0, new String[] {"Kingdom", "Reefcastle", "Shellthrone", "Pearlhold", "Tidecourt"}),
    SKETCHBOOK("sketchbook", "Sketchbook Realm", 0xE8E0D0, new String[] {"Sketch", "Draft", "Doodle", "Notebook", "Scribble"}),
    NOIR("noir", "Noir City", 0x9A9A9A, new String[] {"City", "Shadowtown", "Monochrome", "Silver Screen", "Flicker"}),
    PSYCHEDELIA("psychedelia", "Psychedelia", 0xFF40FF, new String[] {"Psychedelia", "Kaleidos", "Trip", "Morph", "Spectrumland"}),
    COMICVERSE("comicverse", "Comicverse", 0xFFD020, new String[] {"Comicverse", "Splashpage", "Panelworld", "Inkline", "Kapow"}),
    PIXELWORLD("pixelworld", "Pixel World", 0x40E040, new String[] {"World", "8-Bit", "Cartridge", "Overworld-1", "Sprite"}),
    CANVAS("canvas", "The Painted Canvas", 0xE0A040, new String[] {"Canvas", "Oil", "Brushland", "Impasto", "Easel"}),
    PAPERCRAFT("papercraft", "Papercraft Fold", 0xF0F0E0, new String[] {"Fold", "Cutout", "Paperland", "Crease", "Popup"}),
    NEONGRID("neongrid", "The Neon Grid", 0x00F0FF, new String[] {"Grid", "Tron", "Mainframe", "Linebreak", "Vectorline"}),
    REVERIE("reverie", "Reverie Shores", 0xFFC0E0, new String[] {"Shores", "Sleepwater", "Driftdream", "Halfwake", "Moonlull"}),
    CORRUPTDATA("corruptdata", "Corrupted Data", 0x39FF14, new String[] {"Data", "Bitrot", "Segfault", "Nullsector", "Crashdump"}),
    BLUEPRINT("blueprint", "Blueprint Plane", 0x2050A0, new String[] {"Plane", "Schematic", "Draftworks", "Plan", "Elevation"}),
    CLAYMATION("claymation", "Claymation Valley", 0xE08060, new String[] {"Valley", "Plasticine", "Stopmotion", "Modelland", "Squish"}),
    WIREFRAME("wireframe", "The Wireframe", 0x40FFE0, new String[] {"Wireframe", "Mesh", "Polygon", "Vertex", "Render"}),
    INKWASH("inkwash", "Inkwash Mountains", 0x202020, new String[] {"Mountains", "Sumi", "Brushpeak", "Calligraph", "Mistink"}),
    GLASSWORLD("glassworld", "Glassworld", 0xC0F0FF, new String[] {"Glassworld", "Prismwater", "Clearlands", "Crystalline", "Lens"}),
    WATERCOLOR("watercolor", "Watercolour Meadows", 0x80C0E0, new String[] {"Meadows", "Washland", "Pigment", "Bleedfield", "Aquarelle"}),
    NEGATIVE("negative", "The Negative", 0x00FFFF, new String[] {"Negative", "Inversion", "Antiworld", "Obverse", "Reverse"}),
    SILHOUETTE("silhouette", "Silhouette Savanna", 0xFF8040, new String[] {"Savanna", "Duskshade", "Shadowplay", "Umbrafield", "Outline"}),
    VHSTAPE("vhstape", "The Lost Tape", 0xC0A0FF, new String[] {"Tape", "Recording", "Rewind", "Static", "Channel 3"}),
    ASTRALPLANE("astralplane", "The Astral Plane", 0x8060FF, new String[] {"Plane", "Stardeep", "Cosmorama", "Skyborne", "Ether"}),
    ORIGAMI("origami", "Origami Reach", 0xFF6060, new String[] {"Reach", "Foldland", "Kami", "Crane", "Pleat"}),
    XRAY("xray", "The X-Ray Expanse", 0x4080FF, new String[] {"Expanse", "Radiograph", "Skeleton", "Bonelight", "Scan"}),
    FRACTAL("fractal", "The Fractal Infinite", 0xFF9020, new String[] {"Infinite", "Recursion", "Mandel", "Selfsame", "Iteration"}),
    COMICPANELS("comicpanels", "Panel Country", 0xFF3030, new String[] {"Country", "Gutterlands", "Issue One", "Storyboard", "Splash"}),
    GRAPHITE("graphite", "Graphite Memory", 0x808080, new String[] {"Memory", "Pencilgray", "Afterimage", "Lead", "Ashsketch"}),
    CYBERROT("cyberrot", "Cyberrot", 0xFF0080, new String[] {"Cyberrot", "Bleedgrid", "Hackspace", "Darknet", "Overclock"}),
    DREAMWASH("dreamwash", "Dreamwash", 0xC0A0FF, new String[] {"Dreamwash", "Softfall", "Lullwater", "Pastelglow", "Haze"}),
    POPTRIP("poptrip", "Pop Trip", 0xFF60FF, new String[] {"Trip", "Popart", "Zap", "Kablam", "Hypercolor"}),
    TAPEHORROR("tapehorror", "The Static Hollow", 0x608060, new String[] {"Hollow", "Deadair", "Lastbroadcast", "Snowscreen", "Signal"}),
    PRISMPIXEL("prismpixel", "Prism Arcade", 0x40FFFF, new String[] {"Arcade", "Highscore", "Bitspectrum", "Insertcoin", "Bonus Stage"});

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

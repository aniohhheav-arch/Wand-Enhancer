package dev.riftverse.universe;

import dev.riftverse.universe.UniverseTraits.CreatureKind;
import dev.riftverse.universe.UniverseTraits.MegaKind;
import dev.riftverse.universe.UniverseTraits.MusicKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.universe.UniverseTraits.WeatherKind;
import dev.riftverse.util.ColorUtil;
import dev.riftverse.util.Hash;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Turns a free-text description ("a dark cyberpunk city during a permanent thunderstorm") into a concrete universe
 * spec. The interpreter scores archetypes, blends a secondary one, then layers dozens of independent modifiers for
 * sky, weather, time, terrain, scale, inhabitants, physics, atmosphere and colour.
 */
public final class PromptInterpreter {
    public record Result(UniverseSpec spec, List<String> notes) {}

    private static final Map<Archetype, Map<String, Float>> ARCHETYPE_WORDS = new EnumMap<>(Archetype.class);
    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();

    static {
        words(Archetype.NEON_SPRAWL, 3, "cyberpunk", "neon", "metropolis", "megacity", "cyber", "skyscraper", "skyscrapers", "arcology", "synthwave", "dystopian", "dystopia");
        words(Archetype.NEON_SPRAWL, 2, "city", "cities", "urban", "futuristic", "hologram", "holograms", "streets", "downtown", "tech", "technology");
        words(Archetype.NEON_SPRAWL, 1, "robot", "robots", "tower", "towers", "street", "lights");
        words(Archetype.XENOFLORA, 3, "jungle", "jungles", "forest", "forests", "flora", "bioluminescent", "rainforest", "overgrown");
        words(Archetype.XENOFLORA, 2, "alien", "plants", "plant", "trees", "tree", "vines", "lush", "garden", "fungal", "wilderness", "wild");
        words(Archetype.XENOFLORA, 1, "glowing", "green", "nature");
        words(Archetype.SKYSHATTER, 3, "floating", "skylands", "airborne", "aerial");
        words(Archetype.SKYSHATTER, 2, "islands", "island", "sky", "clouds", "cloud", "heavens");
        words(Archetype.THALASSIC, 3, "ocean", "oceans", "sea", "seas", "underwater", "aquatic", "waterworld", "tides", "maritime");
        words(Archetype.THALASSIC, 2, "water", "waves", "marine", "reef", "coral", "archipelago", "deep", "abyssal");
        words(Archetype.PRISMATIC, 3, "crystal", "crystals", "crystalline", "prism", "prismatic", "geode", "amethyst", "gemstone", "gemstones");
        words(Archetype.PRISMATIC, 2, "gem", "gems", "quartz", "diamond", "diamonds", "jewel", "jewels", "shimmering", "refracting");
        words(Archetype.ASHEN, 3, "dead", "desolate", "barren", "wasteland", "apocalypse", "apocalyptic", "lifeless", "postapocalyptic");
        words(Archetype.ASHEN, 2, "ash", "ashes", "graveyard", "bones", "skeleton", "skeletons", "craters", "crater", "ruined", "abandoned", "dusty");
        words(Archetype.ASTRAL, 3, "cosmic", "space", "nebula", "nebulae", "celestial", "astral", "interstellar", "galactic", "supernova", "cosmos");
        words(Archetype.ASTRAL, 2, "galaxy", "galaxies", "stars", "starry", "constellations", "orbit", "planets", "stellar");
        words(Archetype.CORRUPTED, 3, "glitch", "glitched", "glitchy", "corrupted", "error", "simulation", "glitches", "pixelated", "hacked");
        words(Archetype.CORRUPTED, 2, "corrupt", "broken", "digital", "pixel", "pixels", "matrix", "virtual", "data", "computer", "code", "bugged", "virus");
        words(Archetype.ELDER, 3, "ancient", "temple", "temples", "ruins", "civilization", "pyramid", "pyramids", "egyptian", "aztec", "mayan", "ziggurat", "ziggurats");
        words(Archetype.ELDER, 2, "forgotten", "empire", "kingdom", "gods", "sacred", "monument", "monuments", "statues", "relics", "relic", "lost", "pillars", "colossus");
        words(Archetype.ELDER, 1, "golden", "gold", "stone");
        words(Archetype.INVERTED, 3, "gravity", "upside", "inverted", "weightless", "antigravity", "defying", "defy", "zerog", "levitating");
        words(Archetype.INVERTED, 2, "hanging", "suspended", "topsy", "reversed");
        words(Archetype.HOLLOW, 3, "void", "abyss", "emptiness", "darkness", "nothingness", "oblivion");
        words(Archetype.HOLLOW, 2, "shadow", "shadows", "nothing", "empty", "horror", "nightmare", "eerie", "haunted", "silence", "creepy", "sinister");
        words(Archetype.HOLLOW, 1, "dark", "black");
        words(Archetype.SOMNIUM, 3, "dream", "dreamy", "dreamlike", "surreal", "whimsical", "candy", "pastel", "wonderland", "dreamscape", "fairytale");
        words(Archetype.SOMNIUM, 2, "fairy", "fantasy", "magical", "cute", "cozy", "mushroom", "mushrooms", "rainbow", "rainbows", "sweet", "soft");
        words(Archetype.SOMNIUM, 1, "peaceful", "pink");
        words(Archetype.CINDER, 3, "volcano", "volcanoes", "volcanic", "lava", "magma", "hell", "hellish", "inferno", "molten");
        words(Archetype.CINDER, 2, "fire", "fiery", "burning", "sulfur", "embers", "forge", "scorched", "infernal");
        words(Archetype.RIME, 3, "ice", "frozen", "snow", "snowy", "winter", "frost", "glacier", "glaciers", "arctic", "tundra", "blizzard", "icy", "frostbitten");
        words(Archetype.RIME, 2, "cold", "polar", "freezing", "glacial");
        words(Archetype.SUNSCAR, 3, "desert", "deserts", "dune", "dunes", "sahara", "arid", "sandy", "oasis");
        words(Archetype.SUNSCAR, 2, "sand", "scorching", "sun", "sunny", "dry", "mirage");
        words(Archetype.MESA, 3, "mesa", "mesas", "badlands", "canyon", "canyons", "gorge", "western", "hoodoo");
        words(Archetype.MESA, 2, "rust", "rusty", "red", "terracotta", "cliffs", "plateau");
        words(Archetype.CORAL, 3, "coral", "reef", "reefs", "atoll", "lagoon", "tropical", "paradise", "beach", "beaches");
        words(Archetype.CORAL, 2, "shallows", "turquoise", "islands", "sunny");
        words(Archetype.MYCELIA, 3, "fungal", "fungus", "fungi", "spores", "mycelium", "mushroom", "mushrooms", "shroom", "shrooms");
        words(Archetype.MYCELIA, 2, "mold", "rot", "cavern", "underground", "bioluminescent");
        words(Archetype.CLOCKWORK, 3, "clockwork", "steampunk", "gears", "cogs", "brass", "copper", "mechanical", "machine", "machines");
        words(Archetype.CLOCKWORK, 2, "factory", "industrial", "victorian", "automaton", "automatons", "pipes");
        words(Archetype.SANGUINE, 3, "blood", "bloody", "gore", "flesh", "crimson", "scarlet", "carnage", "vampire", "vampires");
        words(Archetype.SANGUINE, 2, "red", "violent", "brutal", "storm", "horror");
        words(Archetype.CONFECTION, 3, "candy", "sweets", "sugar", "dessert", "desserts", "cake", "cakes", "chocolate", "lollipop", "gingerbread");
        words(Archetype.CONFECTION, 2, "sweet", "pastel", "frosting", "cotton");
        words(Archetype.TEMPEST, 3, "storm", "stormy", "thunderstorm", "lightning", "tempest", "hurricane", "tornado", "thunder");
        words(Archetype.TEMPEST, 2, "wind", "windy", "clouds", "electric", "sky");
        words(Archetype.MIRE, 3, "swamp", "swamps", "bog", "marsh", "marshes", "fen", "toxic", "mire", "muddy");
        words(Archetype.MIRE, 2, "mud", "slime", "murky", "poison", "poisonous", "wetland", "foggy");
        words(Archetype.OBSIDIAN, 3, "obsidian", "spikes", "spires", "throne", "darklord", "fortress");
        words(Archetype.OBSIDIAN, 2, "black", "jagged", "sharp", "evil", "purple");
        words(Archetype.VERDIGRIS, 3, "verdigris", "patina", "overgrown", "ruins", "bronze");
        words(Archetype.VERDIGRIS, 2, "moss", "mossy", "teal", "forgotten", "ancient");
        words(Archetype.RADIANCE, 3, "heaven", "heavenly", "divine", "holy", "angelic", "angels", "radiant", "paradise", "celestial");
        words(Archetype.RADIANCE, 2, "light", "bright", "white", "golden", "shining", "pure");
        words(Archetype.FERROUS, 3, "metal", "metallic", "iron", "scrap", "scrapyard", "junkyard", "industrial", "robotic", "mining");
        words(Archetype.FERROUS, 2, "steel", "rust", "machines", "wasteland", "grey", "gray");
        words(Archetype.BLOOM, 3, "flowers", "flower", "blossom", "blossoms", "cherry", "sakura", "petals", "floral", "garden");
        words(Archetype.BLOOM, 2, "pink", "spring", "beautiful", "colorful", "colourful");
        words(Archetype.AURORA, 3, "aurora", "borealis", "arctic", "tundra", "polar", "northern");
        words(Archetype.MAGMA, 3, "magma", "lava", "volcano", "volcanic", "molten", "eruption");
        words(Archetype.PRIMEVAL, 3, "jungle", "dinosaur", "dinosaurs", "prehistoric", "rainforest", "primeval", "tropical");
        words(Archetype.NEBULA, 3, "nebula", "galaxy", "cosmic", "space", "starry", "stars");
        words(Archetype.WASTELAND, 3, "wasteland", "apocalypse", "apocalyptic", "fallout", "nuclear", "radioactive", "ruined");
        words(Archetype.GEODE, 3, "geode", "amethyst", "gem", "gems", "jewel", "crystals");
        words(Archetype.DEEPDARK, 3, "sculk", "warden", "deep", "underground", "cave", "caves", "echo");
        words(Archetype.SAVANNA, 3, "savanna", "safari", "africa", "plains", "grassland", "lions");
        words(Archetype.CHROME, 3, "utopia", "chrome", "futuristic", "clean", "white", "metropolis", "solarpunk");
        words(Archetype.LUNAR, 3, "moon", "lunar", "astronaut", "apollo", "crater", "craters");
        words(Archetype.HIVE, 3, "bee", "bees", "honey", "hive", "honeycomb", "nectar");
        words(Archetype.STARFORGE, 3, "forge", "foundry", "factory", "industrial", "smithy", "anvil", "metal");
        words(Archetype.FROSTGLASS, 3, "frost", "frozen", "icy", "ice", "glacier", "crystal", "winter");
        words(Archetype.ECHO, 3, "abyss", "silence", "silent", "whisper", "whispers", "haunted");
        words(Archetype.MOLTENSEA, 3, "burning", "inferno", "firestorm");
        words(Archetype.CLOUDKINGDOM, 3, "cloud", "clouds", "heaven", "heavenly", "kingdom", "castle", "sky");
        words(Archetype.DROWNED, 3, "atlantis", "sunken", "drowned", "underwater", "shipwreck", "ruins");
        words(Archetype.TOXIC, 3, "toxic", "poison", "poisonous", "acid", "sludge", "plague", "radiation");
        words(Archetype.CRIMSONWEALD, 3, "crimson", "blood", "bloody", "scarlet");
        words(Archetype.WARPEDWEALD, 3, "warped", "teal", "twisted", "weird");
        words(Archetype.GOLDENTEMPLE, 3, "temple", "gold", "golden", "egypt", "pharaoh", "pyramid", "god", "gods");
        words(Archetype.RAINBOW, 3, "rainbow", "rainbows", "colours", "colors", "unicorn", "happy");
        words(Archetype.VOIDGLASS, 3, "void", "nothing", "nothingness", "empty");
        words(Archetype.RUNIC, 3, "rune", "runes", "runic", "magic", "magical", "wizard", "spell", "spells");
        words(Archetype.EMBERSTEPPE, 3, "ember", "embers", "smoulder", "steppe", "burnt", "scorched");
        words(Archetype.PASTEL, 3, "pastel", "soft", "cute", "kawaii", "cozy", "cosy", "fluffy");
        words(Archetype.PETRIFIED, 3, "petrified", "fossil", "fossils", "starwood");
        words(Archetype.CRYSTALOCEAN, 3, "lagoon", "turquoise");
        words(Archetype.DUSKHIGHLANDS, 3, "dusk", "twilight", "sunset", "highlands", "evening");
        words(Archetype.NEONJUNGLE, 3, "glowing", "bioluminescent", "bioluminescence", "avatar", "pandora");
        words(Archetype.CELESTIAL, 3, "celestial", "angel", "angels", "divine", "holy", "paradise", "olympus");
        words(Archetype.LUNARCOLONY, 3, "moonbase", "colony", "sci-fi", "scifi", "outpost");
        words(Archetype.CORALKING, 3, "mermaid", "mermaids", "pearl", "pearls", "shell", "shells");
        words(Archetype.MIRROR, 3, "mirror", "reflection", "reflective", "glass", "salt", "flats");

        color(0xFF2A2A, "red", "scarlet", "ruby");
        color(0xDC143C, "crimson", "blood", "bloody");
        color(0xFF8C1A, "orange", "tangerine");
        color(0xFFBF00, "amber");
        color(0xFFC14D, "gold", "golden");
        color(0xFFE84D, "yellow");
        color(0x9DFF2A, "lime");
        color(0x3DFF6E, "green");
        color(0x2ECC71, "emerald", "jade");
        color(0x1ACFC0, "teal", "turquoise");
        color(0x00E5FF, "cyan", "aqua");
        color(0x2F6BFF, "blue", "cobalt");
        color(0x3DA5FF, "azure", "sapphire", "sky-blue");
        color(0x4B2AFF, "indigo");
        color(0x8F3DFF, "violet");
        color(0x9B30FF, "purple", "lavender");
        color(0xFF2BD6, "magenta", "fuchsia");
        color(0xFF7AC8, "pink", "rose");
        color(0xF4F4FF, "white", "pale", "ivory");
        color(0xC8D0E0, "silver", "chrome", "metallic");
        color(0xC87533, "copper", "bronze", "rust", "rusty");
    }

    private static void words(Archetype a, float weight, String... ws) {
        Map<String, Float> m = ARCHETYPE_WORDS.computeIfAbsent(a, k -> new LinkedHashMap<>());
        for (String w : ws) m.put(w, weight);
    }

    private static void color(int c, String... names) {
        for (String n : names) COLORS.put(n, c);
    }

    private final List<String> tokens = new ArrayList<>();
    private final Set<String> set = new HashSet<>();
    private final List<String> notes = new ArrayList<>();

    private PromptInterpreter(String prompt) {
        String clean = prompt.toLowerCase(Locale.ROOT).replace("zero-g", "zerog").replace("upside-down", "upside down").replaceAll("[^a-z0-9\\- ]", " ");
        for (String t : clean.split("[\\s\\-]+")) {
            if (!t.isBlank()) tokens.add(t);
        }
        set.addAll(tokens);
        for (int i = 0; i + 1 < tokens.size(); i++) set.add(tokens.get(i) + " " + tokens.get(i + 1));
    }

    public static Result interpret(String prompt, UniverseId id, long seed) {
        PromptInterpreter p = new PromptInterpreter(prompt);
        UniverseSpec spec = p.build(id, seed);
        spec.prompt = prompt.trim();
        return new Result(spec, List.copyOf(p.notes));
    }

    private boolean has(String... ws) {
        for (String w : ws) if (set.contains(w)) return true;
        return false;
    }

    private UniverseSpec build(UniverseId id, long seed) {
        Map<Archetype, Float> scores = new EnumMap<>(Archetype.class);
        for (Map.Entry<Archetype, Map<String, Float>> e : ARCHETYPE_WORDS.entrySet()) {
            float score = 0;
            for (String tok : tokens) score += e.getValue().getOrDefault(tok, 0f);
            if (score > 0) scores.put(e.getKey(), score);
        }
        Random r = new Random(seed);
        Archetype primary = null;
        Archetype secondary = null;
        float best = 0, second = 0;
        for (Map.Entry<Archetype, Float> e : scores.entrySet()) {
            float v = e.getValue() + r.nextFloat() * 0.01f;
            if (v > best) {
                second = best;
                secondary = primary;
                best = v;
                primary = e.getKey();
            } else if (v > second) {
                second = v;
                secondary = e.getKey();
            }
        }
        if (primary == null) {
            primary = Archetype.byId(r.nextInt(Archetype.values().length));
            notes.add("No familiar concepts — the rift chose: " + primary.displayName);
        } else {
            notes.add("Core reality: " + primary.displayName);
        }

        UniverseSpec s = new UniverseSpec();
        s.id = id;
        s.seed = seed;
        SpecFactory.applyArchetype(s, primary);

        if (secondary != null && second >= 2f) {
            blend(s, secondary);
            notes.add("Interwoven with: " + secondary.displayName);
        }

        applyTerrain(s);
        applySky(s);
        applyWeatherAndTime(s);
        applyScale(s);
        applyCreatures(s);
        applyPhysics(s);
        applyColors(s);
        applyMusic(s);

        s.name = UniverseNames.generate(seed, primary);
        return s;
    }

    private void blend(UniverseSpec s, Archetype other) {
        UniverseSpec o = new UniverseSpec();
        SpecFactory.applyArchetype(o, other);
        s.mega2 = o.mega;
        s.nebulaB = o.nebulaA;
        s.skyHorizon = ColorUtil.lerp(s.skyHorizon, o.skyHorizon, 0.35f);
        s.fogColor = ColorUtil.lerp(s.fogColor, o.fogColor, 0.35f);
        s.islandDensity = Math.max(s.islandDensity, o.islandDensity * 0.8f);
        for (CreatureKind c : o.creatures) if (!s.creatures.contains(c)) s.creatures.add(c);
        if (o.vacuum && !s.vacuum && has("airless", "vacuum")) s.vacuum = true;
        if (o.terrain == TerrainMode.CITY) s.mega2 = MegaKind.NEON_MEGATOWER;
        if (other == Archetype.PRISMATIC || other == Archetype.RIME || other == Archetype.ELDER) s.materials = s.archetype == Archetype.NEON_SPRAWL ? other : s.materials;
        s.auroraIntensity = Math.max(s.auroraIntensity, o.auroraIntensity * 0.6f);
        s.moons = Math.max(s.moons, o.moons);
        if (o.planetSize > s.planetSize && s.archetype != Archetype.THALASSIC) {
            s.planetSize = o.planetSize * 0.8f;
            s.planetRings |= o.planetRings;
        }
    }

    private void applyTerrain(UniverseSpec s) {
        boolean ocean = has("ocean", "oceans", "sea", "seas", "underwater", "waterworld", "flooded", "aquatic");
        boolean floating = has("floating", "levitating", "sky islands", "skylands", "suspended");
        boolean islands = has("islands", "island", "archipelago", "isles");

        if (has("city", "cities", "metropolis", "megacity", "skyscrapers", "skyscraper", "buildings", "arcology")) {
            if (s.terrain != TerrainMode.CITY) {
                s.terrain = TerrainMode.CITY;
                s.amplitude = 3;
                s.baseHeight = 64;
                notes.add("Terrain: an endless megacity");
            }
            if (s.mega == MegaKind.NONE) s.mega = MegaKind.NEON_MEGATOWER;
            if (ocean) {
                s.hasSea = true;
                s.seaLevel = s.baseHeight + 4;
                notes.add("The streets are flooded");
            }
        } else if (ocean) {
            if (s.terrain == TerrainMode.FLOATING || s.terrain == TerrainMode.ROLLING || s.terrain == TerrainMode.ARCHIPELAGO || s.terrain == TerrainMode.SPIRES) {
                s.terrain = islands && !floating ? TerrainMode.ARCHIPELAGO : TerrainMode.OCEAN;
            }
            s.hasSea = true;
            s.seaLevel = Math.max(s.seaLevel, 84);
            s.baseHeight = Math.min(s.baseHeight, 44);
            if (s.terrain == TerrainMode.ARCHIPELAGO) s.baseHeight = 70;
            notes.add(s.terrain == TerrainMode.ARCHIPELAGO ? "Terrain: an endless archipelago" : "Terrain: a planet-spanning ocean");
        }

        if (floating) {
            s.islandDensity = Math.max(s.islandDensity, 0.8f);
            if (!ocean && s.terrain != TerrainMode.CITY && s.terrain != TerrainMode.INVERTED) {
                s.terrain = TerrainMode.FLOATING;
                s.hasSea = false;
            }
            notes.add("Landmasses: floating islands");
        } else if (islands && s.terrain == TerrainMode.ROLLING) {
            s.terrain = TerrainMode.ARCHIPELAGO;
            s.hasSea = true;
            s.seaLevel = Math.max(s.seaLevel, 64);
        }

        if (s.terrain != TerrainMode.CITY) {
            if (has("mountains", "mountain", "peaks", "cliffs", "highlands", "alpine", "summits")) {
                s.terrain = s.terrain == TerrainMode.OCEAN ? TerrainMode.ARCHIPELAGO : TerrainMode.MOUNTAINS;
                s.amplitude = Math.max(s.amplitude, 65);
                notes.add("Terrain: towering mountains");
            } else if (has("canyon", "canyons", "gorge", "gorges", "chasm", "chasms", "ravines")) {
                s.terrain = TerrainMode.CANYONS;
                s.amplitude = Math.max(s.amplitude, 40);
                notes.add("Terrain: carved canyons");
            } else if (has("crater", "craters", "cratered", "meteor", "meteors", "impact")) {
                s.terrain = TerrainMode.CRATERS;
                notes.add("Terrain: impact craters");
            } else if (has("desert", "deserts", "dunes", "dune", "sand", "sandy", "arid")) {
                s.terrain = TerrainMode.DUNES;
                s.hasSea = false;
                s.amplitude = Math.max(12, s.amplitude * 0.6f);
                notes.add("Terrain: rolling dunes");
            } else if (has("spires", "spikes", "needles", "pinnacles", "obelisks")) {
                s.terrain = TerrainMode.SPIRES;
                notes.add("Terrain: jagged spires");
            } else if (has("caves", "cave", "caverns", "cavern", "underground", "subterranean")) {
                s.terrain = TerrainMode.INVERTED;
                s.islandDensity = Math.max(s.islandDensity, 0.3f);
                notes.add("Terrain: a world-sized cavern");
            } else if (has("shattered", "fractured", "fragmented", "fragments", "splintered")) {
                s.terrain = s.glitch > 0.2f ? TerrainMode.SHATTERED : TerrainMode.FRAGMENTS;
                s.hasSea = false;
                notes.add("Terrain: shattered fragments");
            } else if (has("upside down", "inverted", "upside")) {
                s.terrain = TerrainMode.INVERTED;
                notes.add("Terrain: inverted ground hangs from the sky");
            } else if (has("plains", "flat", "meadow", "meadows", "fields", "grassland", "grasslands", "prairie", "savanna")) {
                if (s.terrain != TerrainMode.OCEAN && s.terrain != TerrainMode.FLOATING) s.terrain = TerrainMode.ROLLING;
                s.amplitude = Math.min(s.amplitude, 9);
                notes.add("Terrain: wide open plains");
            } else if (has("hills", "hilly", "valleys", "valley", "rolling")) {
                if (s.terrain != TerrainMode.OCEAN && s.terrain != TerrainMode.FLOATING) s.terrain = TerrainMode.ROLLING;
                s.amplitude = Math.max(s.amplitude, 26);
            }
        }

        if (has("crystal", "crystals", "crystalline", "gem", "gems") && s.archetype != Archetype.PRISMATIC) {
            s.mega2 = MegaKind.CRYSTAL_SPIRE;
            notes.add("Crystal formations pierce the land");
        }
        if (has("ruins", "ancient", "temple", "temples", "pyramid", "pyramids") && s.archetype != Archetype.ELDER) {
            s.mega2 = has("pyramid", "pyramids", "temple", "temples") ? MegaKind.ZIGGURAT : MegaKind.COLOSSAL_PILLARS;
            notes.add("Ruins of a vanished civilization");
        }
        if (has("trees", "tree", "forest", "forests") && s.archetype != Archetype.XENOFLORA) {
            s.mega2 = MegaKind.GIANT_TREE;
            s.decorDensity = Math.max(s.decorDensity, 1.3f);
        }
        if (has("mushroom", "mushrooms", "fungi", "fungal") && s.mega != MegaKind.GIANT_MUSHROOM) s.mega2 = MegaKind.GIANT_MUSHROOM;
        if (has("rings", "ring", "halo", "halos", "gate", "gates", "stargate") && s.mega != MegaKind.HALO_RING) s.mega2 = MegaKind.HALO_RING;
        if (has("monolith", "monoliths", "obelisk", "obelisks")) s.mega2 = MegaKind.MONOLITH;
        if (has("volcano", "volcanoes") && s.mega != MegaKind.VOLCANO) s.mega2 = MegaKind.VOLCANO;
        if (has("arches", "arch", "bridges")) s.mega2 = MegaKind.ARCH;
        if (has("skeleton", "skeletons", "bones", "ribs", "carcass", "remains")) s.mega2 = MegaKind.RIBCAGE;
        if (has("frozen", "ice", "icy") && s.archetype != Archetype.RIME) {
            if (s.hasSea) s.materials = Archetype.RIME;
            s.mega2 = MegaKind.ICE_SPIRE;
        }
        if (has("lava", "magma", "molten") && s.archetype != Archetype.CINDER) {
            s.materials = Archetype.CINDER;
            notes.add("Rivers of lava");
        }
    }

    private int numberBefore(String noun) {
        for (int i = 1; i < tokens.size(); i++) {
            if (!tokens.get(i).equals(noun)) continue;
            for (int back = 1; back <= 2 && i - back >= 0; back++) {
                int n = parseNumber(tokens.get(i - back));
                if (n > 0) return n;
            }
        }
        return 0;
    }

    private static int parseNumber(String t) {
        return switch (t) {
            case "a", "an", "one", "single", "lone", "solitary" -> 1;
            case "two", "twin", "double", "binary", "pair", "dual" -> 2;
            case "three", "triple", "trinary" -> 3;
            case "four" -> 4;
            case "five", "many", "several", "countless", "dozens", "numerous", "multiple" -> 5;
            default -> {
                try {
                    int v = Integer.parseInt(t);
                    yield v > 0 ? Math.min(v, 6) : 0;
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
        };
    }

    private void applySky(UniverseSpec s) {
        int moons = Math.max(numberBefore("moons"), numberBefore("moon"));
        if (moons == 0 && has("moons")) moons = 2;
        if (moons == 0 && has("moon", "moonlit", "moonlight")) moons = 1;
        if (moons > 0) {
            s.moons = Math.min(moons, 5);
            notes.add("Sky: " + s.moons + (s.moons == 1 ? " moon" : " moons"));
        }
        if (numberBefore("suns") >= 2 || has("binary star", "twin suns", "two suns", "binary suns")) {
            s.binarySun = true;
            notes.add("Sky: binary suns");
        }
        if (has("giant sun", "huge sun", "massive sun", "enormous sun", "red giant", "dying sun", "swollen sun")) {
            s.sunSize = 2.4f;
            notes.add("Sky: a swollen giant sun");
        }
        boolean worldIsPlanet = false;
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i).equals("planet") || tokens.get(i).equals("world")) {
                String prev = tokens.get(i - 1);
                if (ARCHETYPE_WORDS.values().stream().anyMatch(m -> m.containsKey(prev)) || COLORS.containsKey(prev)) worldIsPlanet = true;
            }
        }
        if (has("gas giant", "giant planet", "ringed planet", "huge planet", "massive planet", "planet rising", "planets") || (has("planet") && !worldIsPlanet)) {
            s.planetSize = Math.max(s.planetSize, has("gas giant", "giant planet", "huge planet", "massive planet") ? 0.9f : 0.55f);
            notes.add("Sky: a colossal planet looms overhead");
        }
        if (has("rings", "ringed", "ring system")) {
            s.planetRings = true;
            s.planetSize = Math.max(s.planetSize, 0.5f);
        }
        if (has("galaxy", "galaxies", "milky way", "spiral galaxy")) {
            s.galaxyIntensity = 1f;
            notes.add("Sky: galaxies burn overhead");
        }
        if (has("nebula", "nebulae", "nebulas")) {
            s.nebulaIntensity = 1f;
            notes.add("Sky: vast nebulae");
        }
        if (has("stars", "starry", "starlit", "starfield")) s.starDensity = 1f;
        if (has("starless", "no stars")) s.starDensity = 0f;
        if (has("aurora", "auroras", "northern lights", "aurorae")) {
            s.auroraIntensity = 1f;
            notes.add("Sky: rippling auroras");
        }
        if (has("black hole", "black holes", "singularity", "event horizon", "wormhole", "wormholes")) {
            s.skyBlackHole = true;
            s.naturalBlackHoles = true;
            notes.add("A black hole devours the sky");
        }
    }

    private void applyWeatherAndTime(UniverseSpec s) {
        if (has("thunderstorm", "thunderstorms", "storm", "stormy", "lightning", "thunder", "tempest", "hurricane", "storms")) {
            s.weather = WeatherKind.STORM;
            s.stormIntensity = has("permanent", "eternal", "endless", "constant", "perpetual", "never ending") ? 1f : 0.85f;
            s.fogDensity = Math.max(s.fogDensity, 0.4f);
            notes.add("Weather: " + (s.stormIntensity >= 1f ? "a storm that never ends" : "violent storms"));
        } else if (has("rain", "rainy", "raining", "drizzle", "monsoon", "wet")) {
            s.weather = WeatherKind.RAIN;
            notes.add("Weather: endless rain");
        } else if (has("snow", "snowing", "snowfall", "blizzard", "snowy")) {
            s.weather = WeatherKind.SNOW;
            notes.add("Weather: falling snow");
        } else if (has("ash", "ashes", "smoke", "smog", "soot")) {
            s.weather = WeatherKind.ASH;
        } else if (has("spores", "pollen", "fireflies")) {
            s.weather = WeatherKind.SPORES;
        } else if (has("embers", "sparks", "cinders")) {
            s.weather = WeatherKind.EMBERS;
        } else if (has("petals", "blossom", "blossoms", "sakura", "cherry")) {
            s.weather = WeatherKind.PETALS;
        } else if (has("stardust", "sparkles", "sparkling", "glitter")) {
            s.weather = WeatherKind.STARDUST;
        } else if (has("data", "code", "binary rain")) {
            s.weather = WeatherKind.DATA_RAIN;
        } else if (has("clear", "cloudless")) {
            s.weather = WeatherKind.CLEAR;
            s.stormIntensity = 0;
        }

        if (has("fog", "foggy", "mist", "misty", "haze", "hazy", "murky")) {
            s.fogDensity = Math.max(s.fogDensity, 0.7f);
            notes.add("Atmosphere: thick fog");
        }
        if (has("clear", "crisp", "pristine")) s.fogDensity = Math.min(s.fogDensity, 0.1f);

        if (has("night", "midnight", "nighttime", "nocturnal", "permanent night", "eternal night", "moonlit", "after dark")) {
            s.time = TimeMode.ETERNAL_NIGHT;
            notes.add("Time: eternal night");
        } else if (has("sunset", "dusk", "twilight", "sunrise", "dawn", "golden hour", "evening")) {
            s.time = TimeMode.ETERNAL_DUSK;
            notes.add("Time: frozen at twilight");
        } else if (has("day", "daytime", "sunny", "noon", "daylight", "sunlit", "midday")) {
            s.time = TimeMode.ETERNAL_DAY;
            notes.add("Time: an endless day");
        }
    }

    private void applyScale(UniverseSpec s) {
        if (has("massive", "giant", "huge", "colossal", "enormous", "gigantic", "titanic", "immense", "towering", "vast", "megastructures", "monumental")) {
            s.megaDensity = Math.min(1f, s.megaDensity + 0.3f);
            s.amplitude *= 1.3f;
            notes.add("Scale: colossal");
        }
        if (has("tiny", "small", "miniature", "little")) {
            s.megaDensity *= 0.5f;
            s.amplitude *= 0.7f;
        }
        if (has("dense", "packed", "crowded", "overgrown", "teeming")) s.decorDensity = Math.min(2f, s.decorDensity * 1.6f);
        if (has("sparse", "lonely", "minimal", "minimalist", "bare")) s.decorDensity *= 0.4f;
    }

    private void applyCreatures(UniverseSpec s) {
        if (has("lifeless", "no life", "no creatures", "uninhabited", "deserted")) {
            s.creatures.clear();
            notes.add("Inhabitants: none");
            return;
        }
        List<CreatureKind> add = new ArrayList<>();
        if (has("whale", "whales", "sky whales", "space whales")) add.add(CreatureKind.SKY_WHALE);
        if (has("jellyfish", "jelly", "jellies", "medusae")) add.add(CreatureKind.ASTRAL_JELLY);
        if (has("drones", "drone", "robots", "robot", "machines", "androids", "mechs", "sentries")) add.add(CreatureKind.NEON_DRONE);
        if (has("ghosts", "ghost", "spirits", "wraiths", "phantoms", "specters", "spectres")) add.add(CreatureKind.RIFT_WRAITH);
        if (has("shadows", "stalkers", "demons", "monsters", "horrors")) add.add(CreatureKind.VOID_STALKER);
        if (has("golems", "golem", "guardians", "sentinels", "statues", "constructs", "titans")) add.add(CreatureKind.CRYSTAL_SENTINEL);
        if (has("animals", "creatures", "beasts", "herds", "grazers", "wildlife", "fauna")) add.add(CreatureKind.LUMEN_STRIDER);
        if (has("leviathan", "leviathans", "serpent", "serpents", "kraken", "sea monster", "sea monsters", "dragons")) add.add(CreatureKind.ABYSSAL_LEVIATHAN);
        if (has("glitches", "viruses", "bugs", "anomalies")) add.add(CreatureKind.GLITCHLING);
        for (CreatureKind c : add) {
            s.creatures.add(c);
            s.creatures.add(c);
        }
        if (!add.isEmpty()) notes.add("Inhabitants: " + add.size() + " species drawn from the description");
        if (has("giant creatures", "giant beasts", "giant monsters", "huge creatures", "massive creatures", "colossal creatures", "giant animals", "titans")) {
            s.creatureScale = 2.2f;
            notes.add("Inhabitants are enormous");
        }
        if (has("peaceful", "calm", "serene", "tranquil", "safe", "gentle", "quiet", "relaxing", "cozy", "friendly", "harmonious")) {
            s.hostility = 0.05f;
            s.creatures.removeIf(c -> c.hostile);
            if (s.creatures.isEmpty()) s.creatures.add(CreatureKind.ASTRAL_JELLY);
            notes.add("Mood: peaceful");
        } else if (has("dangerous", "hostile", "deadly", "lethal", "nightmare", "terrifying", "haunted", "horror", "war", "violent", "infested", "savage")) {
            s.hostility = 1.9f;
            notes.add("Mood: lethal");
        }
    }

    private void applyPhysics(UniverseSpec s) {
        if (has("zero gravity", "zerog", "no gravity")) {
            s.gravity = 0.15f;
            notes.add("Physics: near-zero gravity");
        } else if (has("low gravity", "weightless", "floaty", "moon gravity", "antigravity", "light gravity")) {
            s.gravity = 0.32f;
            notes.add("Physics: low gravity");
        } else if (has("high gravity", "heavy gravity", "crushing", "dense gravity", "strong gravity")) {
            s.gravity = 1.6f;
            notes.add("Physics: crushing gravity");
        }
        if (has("no air", "airless", "vacuum", "no atmosphere", "toxic", "poisonous", "suffocating", "unbreathable")) {
            s.vacuum = true;
            notes.add("Atmosphere: unbreathable (Voyager helmet required)");
        } else if (has("breathable", "fresh air", "oxygen")) {
            s.vacuum = false;
        }
        if (has("glitch", "glitched", "glitchy", "corrupted", "error", "bugged")) s.glitch = Math.max(s.glitch, 0.6f);
        else if (has("unstable", "chaotic", "chaos", "warped", "distorted")) {
            s.glitch = Math.max(s.glitch, 0.3f);
            notes.add("Reality is unstable");
        }
        if (has("dream", "dreamy", "dreamlike", "surreal", "floaty")) s.dreamlike = true;
    }

    private void applyColors(UniverseSpec s) {
        List<Integer> found = new ArrayList<>();
        for (String t : tokens) {
            Integer c = COLORS.get(t);
            if (c != null && !found.contains(c)) found.add(c);
        }
        if (!found.isEmpty()) {
            int c1 = found.get(0);
            s.skyHorizon = ColorUtil.lerp(s.skyHorizon, c1, 0.55f);
            s.fogColor = ColorUtil.lerp(s.fogColor, ColorUtil.scale(c1, 0.7f), 0.5f);
            s.nebulaA = c1;
            s.accent = c1;
            s.gradeTint = c1;
            s.gradeStrength = Math.max(s.gradeStrength, 0.14f);
            if (found.size() > 1) {
                int c2 = found.get(1);
                s.nebulaB = c2;
                s.skyTop = ColorUtil.lerp(s.skyTop, ColorUtil.scale(c2, 0.35f), 0.5f);
            }
            notes.add("Palette tuned to " + found.size() + (found.size() == 1 ? " colour" : " colours"));
        }
        if (has("dark", "gloomy", "grim", "bleak", "shadowy", "sinister", "black")) {
            s.skyTop = ColorUtil.scale(s.skyTop, 0.35f);
            s.skyHorizon = ColorUtil.scale(s.skyHorizon, 0.45f);
            s.fogColor = ColorUtil.scale(s.fogColor, 0.45f);
            if (s.time == TimeMode.ETERNAL_DAY) s.time = TimeMode.ETERNAL_DUSK;
        }
        if (has("bright", "vibrant", "colorful", "colourful", "radiant", "vivid", "luminous", "glowing")) {
            s.saturation = Math.max(s.saturation, 1.35f);
            s.nebulaIntensity = Math.min(1f, s.nebulaIntensity + 0.25f);
        }
        if (has("pastel", "soft", "muted", "gentle")) {
            s.saturation = Math.min(s.saturation, 0.85f);
            s.skyHorizon = ColorUtil.lerp(s.skyHorizon, 0xFFFFFF, 0.3f);
        }
        if (has("monochrome", "grey", "gray", "colorless", "colourless", "noir", "black and white")) {
            s.saturation = 0.15f;
            notes.add("Palette: drained of colour");
        }
        if (has("neon")) {
            s.saturation = Math.max(s.saturation, 1.4f);
            s.gradeStrength = Math.max(s.gradeStrength, 0.16f);
        }
    }

    private void applyMusic(UniverseSpec s) {
        if (has("peaceful", "dream", "dreamy", "serene", "whimsical")) s.music = MusicKind.DREAM;
        else if (has("ocean", "sea", "underwater")) s.music = MusicKind.OCEAN;
        else if (has("ancient", "temple", "ruins")) s.music = MusicKind.ANCIENT;
        else if (has("cyberpunk", "neon", "glitch", "digital")) s.music = MusicKind.NEON;
        else if (has("void", "horror", "nightmare", "dark", "dead")) s.music = MusicKind.VOID;
    }

    /** Stable, distinct seed for a prompt manifested for the n-th time. */
    public static long seedFor(String prompt, long worldSeed, int counter) {
        return Hash.of(worldSeed, Hash.hashString(prompt.toLowerCase(Locale.ROOT).trim()), counter);
    }
}

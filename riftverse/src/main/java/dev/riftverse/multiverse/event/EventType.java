package dev.riftverse.multiverse.event;

import org.jetbrains.annotations.Nullable;

/** Every multiverse event. Ids are used by commands, config keys and the saved event ledger. */
public enum EventType {
    REALITY_COLLAPSE("reality_collapse", "REALITY COLLAPSE", "The fabric of this world is coming apart", 0xFF3A5A, 900, 90, 600,
            "Terrain fractures and falls into the void around the epicentre. Stabilize reality or flee."),
    DIMENSIONAL_INVASION("dimensional_invasion", "DIMENSIONAL INVASION", "Hostile rifts are tearing open", 0xFF2440, 500, 45, 1200,
            "Three hostile rifts disgorge waves of void creatures. Defeat every wave for a reward."),
    COSMIC_LEVIATHAN("cosmic_leviathan", "COSMIC LEVIATHAN", "Something vast swims between the stars", 0x40FFE0, 1400, 120, 6000,
            "An Abyssal Leviathan breaches into this reality. The event ends when it is slain or departs."),
    ANCIENT_GUARDIAN("ancient_guardian", "THE ANCIENT GUARDIAN", "A custodian of the boundaries awakens", 0xFFC14D, 1600, 150, 6000,
            "The Rift Warden descends to judge those who travel between worlds."),
    DIMENSIONAL_ANOMALY("dimensional_anomaly", "DIMENSIONAL ANOMALY", "Physics no longer applies here", 0x7AF0D0, 350, 30, 900,
            "A zone of broken physics: gravity flips, matter drifts and creatures blink through space."),
    VOID_WANDERER("void_wanderer", "THE VOID WANDERER", "It walks between worlds, and it is hungry", 0x9B30FF, 900, 90, 2400,
            "A colossal void stalker roams the area, smothering the light around it."),
    UNIVERSE_BIRTH("universe_birth", "A UNIVERSE IS BORN", "A new reality condenses out of nothing", 0xFFF0C8, 1200, 120, 420,
            "A brand-new universe forms overhead and leaves a stable rift leading into it."),
    BLACK_HOLE("black_hole", "SINGULARITY", "A black hole is forming", 0xFF8A3A, 1000, 90, 1800,
            "A natural black hole blooms nearby and grows, pulling in everything around it."),
    RIFT_STORM("rift_storm", "RIFT STORM", "Reality is splitting at the seams", 0x8A3AFF, 450, 40, 1200,
            "Unstable rifts flicker open everywhere, each leading somewhere different."),
    COSMIC_CONVERGENCE("cosmic_convergence", "COSMIC CONVERGENCE", "Every reality briefly touches this one", 0xFF7AF0, 2000, 240, 1600,
            "Rifts to many realities align and their creatures drift through. Witnesses gain deep research."),
    DIMENSIONAL_MIGRATION("dimensional_migration", "DIMENSIONAL MIGRATION", "Another ecosystem is pouring through", 0x5CFF9D, 700, 60, 1400,
            "Two rifts open into a living migration route: alien creatures arrive through one while local life is drawn into the other."),
    COSMIC_DEITY("cosmic_deity", "THE COSMIC DEITY", "A god made of night has come to feed", 0xC070FF, 2500, 240, 9000,
            "A titan wearing the night sky descends. Between star-bolts it inhales, dragging creatures and land into its mouth."),
    METEOR_SHOWER("meteor_shower", "METEOR SHOWER", "Fire is falling from the sky", 0xFF7030, 1500, 45, 900,
            "Burning meteors streak down and crater the land around you.", 1),
    SOLAR_FLARE("solar_flare", "SOLAR FLARE", "The sun lashes out", 0xFFE060, 1500, 45, 900,
            "Blinding radiation scorches anything under open sky.", 1),
    ECLIPSE("eclipse", "THE ECLIPSE", "Darkness at noon", 0x302040, 1500, 45, 900,
            "Day turns to night and the creatures of the dark come out.", 1),
    AURORA_STORM("aurora_storm", "AURORA STORM", "The sky is on fire with colour", 0x40FFB0, 1500, 45, 900,
            "Sheets of aurora ripple overhead, invigorating everyone beneath them.", 1),
    COMET_PASSAGE("comet_passage", "COMET PASSAGE", "A comet grazes this world", 0x80E0FF, 1500, 45, 900,
            "A blazing comet crosses the sky, shedding stellar fragments.", 1),
    STARFALL("starfall", "STARFALL", "Stars are dropping out of the sky", 0xFFF0C8, 1500, 45, 900,
            "Fallen stars land nearby: catch the glowing motes for research.", 1),
    GRAVITY_WELL("gravity_well", "GRAVITY WELL", "Something heavy has arrived", 0x6040C0, 1500, 45, 900,
            "Gravity multiplies around an invisible mass: jumping is impossible.", 1),
    NEBULA_DRIFT("nebula_drift", "NEBULA DRIFT", "A nebula is passing through", 0xC050FF, 1500, 45, 900,
            "Clouds of glowing gas drift through the land, hiding everything.", 1),
    PHASE_SHIFT("phase_shift", "PHASE SHIFT", "Things are slipping out of phase", 0x7AF0D0, 1500, 45, 900,
            "Creatures flicker between visible and invisible as reality phases.", 2),
    MIRROR_INVERSION("mirror_inversion", "MIRROR INVERSION", "Left is right and up is down", 0xE0F0FF, 1500, 45, 900,
            "Your controls of the world invert: creatures swap places with their reflections.", 2),
    ECHO_REALITY("echo_reality", "ECHO REALITY", "Another version of this place overlaps", 0xA0A0FF, 1500, 45, 900,
            "Ghostly echoes of creatures from a parallel world wander through.", 2),
    DIMENSIONAL_BLEED("dimensional_bleed", "DIMENSIONAL BLEED", "Another dimension is leaking in", 0xFF4080, 1500, 45, 900,
            "Blocks of a foreign reality bleed into the terrain.", 2),
    PORTAL_SURGE("portal_surge", "PORTAL SURGE", "Every gateway flares at once", 0xFFC14D, 1500, 45, 900,
            "Short-lived rifts open and close all around, swapping creatures between them.", 2),
    POCKET_COLLAPSE("pocket_collapse", "POCKET COLLAPSE", "A pocket dimension is imploding", 0x8A3AFF, 1500, 45, 900,
            "Space folds inward: everything nearby is dragged toward the collapse point.", 2),
    VOID_TIDE("void_tide", "VOID TIDE", "The void is rising", 0x1A0030, 1500, 45, 900,
            "A tide of nothing creeps up from below, sapping strength.", 2),
    BOUNDARY_FRACTURE("boundary_fracture", "BOUNDARY FRACTURE", "The edge of the world cracked", 0xFF3A5A, 1500, 45, 900,
            "Cracks in reality teleport whoever touches them a short way.", 2),
    TIME_DILATION("time_dilation", "TIME DILATION", "Time is running slow here", 0x7DF9FF, 1500, 45, 900,
            "Everything near the epicentre moves in slow motion.", 3),
    TIME_FREEZE("time_freeze", "TIME FREEZE", "Time stops", 0xC0E8FF, 1500, 45, 900,
            "Creatures freeze mid-step; only travellers can still move.", 3),
    TEMPORAL_LOOP("temporal_loop", "TEMPORAL LOOP", "You have been here before", 0xFFB040, 1500, 45, 900,
            "Every so often, everyone is pulled back to where they were moments ago.", 3),
    CHRONO_STORM("chrono_storm", "CHRONO STORM", "Time is tearing itself apart", 0xFF8A20, 1500, 45, 900,
            "Bursts of fast and slow time strike at random.", 3),
    FUTURE_ECHO("future_echo", "FUTURE ECHO", "Glimpses of what will be", 0x80C0FF, 1500, 45, 900,
            "Visions of the future reveal hidden ores and treasure for a while.", 3),
    PAST_ECHO("past_echo", "PAST ECHO", "The past walks again", 0xC27B4A, 1500, 45, 900,
            "Long-gone creatures of an earlier age return briefly.", 3),
    AGE_SURGE("age_surge", "AGE SURGE", "Time races forward", 0xE0C060, 1500, 45, 900,
            "Days pass in seconds: crops grow and the sun wheels overhead.", 3),
    PARADOX_CASCADE("paradox_cascade", "PARADOX CASCADE", "Cause and effect have come apart", 0xFF4050, 1500, 45, 900,
            "Paradoxes ripple outward. The TSA takes notice.", 3),
    FIRESTORM("firestorm", "FIRESTORM", "The air itself is burning", 0xFF4A10, 1500, 45, 900,
            "Flames rain down and the ground smoulders.", 4),
    BLIZZARD("blizzard", "BLIZZARD", "A killing cold descends", 0xE0FFFF, 1500, 45, 900,
            "Snow and ice bury the land; exposed travellers freeze.", 4),
    TOXIC_FOG("toxic_fog", "TOXIC FOG", "Poison drifts over the land", 0x80FF40, 1500, 45, 900,
            "A choking fog poisons everything that breathes it.", 4),
    CRYSTAL_BLOOM("crystal_bloom", "CRYSTAL BLOOM", "Crystals erupt from the ground", 0xB0E0FF, 1500, 45, 900,
            "Amethyst spires grow out of the earth (they fade when the bloom ends).", 4),
    MAGNETIC_STORM("magnetic_storm", "MAGNETIC STORM", "Iron is pulled skyward", 0x8090A0, 1500, 45, 900,
            "Items are dragged into the air and compasses spin.", 4),
    EARTHQUAKE("earthquake", "EARTHQUAKE", "The ground is shaking apart", 0x9A7050, 1500, 45, 900,
            "Violent tremors throw everything around.", 4),
    ACID_RAIN("acid_rain", "ACID RAIN", "The rain burns", 0xC0FF60, 1500, 45, 900,
            "Corrosive rain eats at anyone under open sky.", 4),
    LIGHTNING_STORM("lightning_storm", "LIGHTNING STORM", "The sky is full of teeth", 0xE0E0FF, 1500, 45, 900,
            "Lightning strikes again and again around the epicentre.", 4),
    SPORE_BLOOM("spore_bloom", "SPORE BLOOM", "The air is thick with spores", 0xB060FF, 1500, 45, 900,
            "Spore clouds fill the air and mushrooms sprout everywhere.", 5),
    SWARM("swarm", "THE SWARM", "Something is buzzing", 0xFFD040, 1500, 45, 900,
            "A swarm of stinging creatures descends on the area.", 5),
    STAMPEDE("stampede", "STAMPEDE", "The herds are running", 0xC09060, 1500, 45, 900,
            "A stampede of beasts thunders through.", 5),
    OVERGROWTH("overgrowth", "OVERGROWTH", "Life runs wild", 0x3ABF3A, 1500, 45, 900,
            "Plants burst from every surface, wrapping the land in green.", 5),
    MUTATION_WAVE("mutation_wave", "MUTATION WAVE", "Life is changing", 0xFF60C0, 1500, 45, 900,
            "Creatures caught in the wave grow, shrink and change.", 5),
    LIFE_SURGE("life_surge", "LIFE SURGE", "Vitality floods the land", 0x60FF90, 1500, 45, 900,
            "Everyone heals quickly and animals multiply.", 5),
    HIVE_AWAKENING("hive_awakening", "HIVE AWAKENING", "The hive has woken", 0xFFB020, 1500, 45, 900,
            "An angry hive pours out its defenders.", 5),
    SKY_WHALE_MIGRATION("sky_whale_migration", "SKY WHALE MIGRATION", "Giants are passing overhead", 0x2F6BFF, 1500, 45, 900,
            "A pod of sky whales migrates across the sky.", 5),
    COLOR_DRAIN("color_drain", "COLOR DRAIN", "The colour is leaving the world", 0x909090, 1500, 45, 900,
            "The world fades to grey around the epicentre.", 6),
    GLITCH_STORM("glitch_storm", "GLITCH STORM", "Reality is failing to render", 0x39FF14, 1500, 45, 900,
            "Glitches tear at the world: blocks flicker and creatures teleport.", 6),
    SILENCE("silence", "THE SILENCE", "Nothing makes a sound", 0x404050, 1500, 45, 900,
            "Sound dies, the world darkens and something listens.", 6),
    DREAM_LEAK("dream_leak", "DREAM LEAK", "Dreams are leaking into the waking world", 0xFF8FD0, 1500, 45, 900,
            "Gravity softens and dream creatures drift through.", 6),
    GEOMETRY_FAILURE("geometry_failure", "GEOMETRY FAILURE", "Space is the wrong shape", 0xFF7AF0, 1500, 45, 900,
            "Distances lie: walking forward may carry you sideways.", 6),
    NULL_ZONE("null_zone", "NULL ZONE", "Nothing works here", 0x202020, 1500, 45, 900,
            "All enchantment and effects are stripped away.", 6),
    REALITY_REWRITE("reality_rewrite", "REALITY REWRITE", "The rules are being rewritten", 0xFFFFFF, 1500, 45, 900,
            "The local laws of physics change every few seconds.", 6),
    THE_WATCHER("the_watcher", "THE WATCHER", "You are being observed", 0xFF2040, 1500, 45, 900,
            "An enormous eye opens in the sky. Do not look at it.", 6);

    public final String id;
    public final String title;
    public final String subtitle;
    public final int color;
    public final int defaultRarity;
    public final int defaultCooldownMinutes;
    public final int durationTicks;
    public final String description;
    private final int category;
    public static final String[] CATEGORIES = {"", "I · COSMIC", "II · DIMENSIONAL", "III · TEMPORAL", "IV · ELEMENTAL", "V · BIOLOGICAL", "VI · REALITY"};

    EventType(String id, String title, String subtitle, int color, int defaultRarity, int defaultCooldownMinutes, int durationTicks, String description) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.color = color;
        this.defaultRarity = defaultRarity;
        this.defaultCooldownMinutes = defaultCooldownMinutes;
        this.durationTicks = durationTicks;
        this.description = description;
        this.category = 0;
    }

    EventType(String id, String title, String subtitle, int color, int defaultRarity, int defaultCooldownMinutes, int durationTicks, String description, int category) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.color = color;
        this.defaultRarity = defaultRarity;
        this.defaultCooldownMinutes = defaultCooldownMinutes;
        this.durationTicks = durationTicks;
        this.description = description;
        this.category = category;
    }

    /** I cosmic, II dimensional, III temporal, IV elemental, V biological, VI reality. */
    public int category() {
        if (category > 0) return category;
        return switch (this) {
            case COSMIC_LEVIATHAN, BLACK_HOLE, COSMIC_CONVERGENCE, COSMIC_DEITY, UNIVERSE_BIRTH -> 1;
            case DIMENSIONAL_INVASION, RIFT_STORM, DIMENSIONAL_MIGRATION, VOID_WANDERER -> 2;
            case ANCIENT_GUARDIAN -> 3;
            default -> 6;
        };
    }

    /** True for the original hand-built events (the rest run on the phenomena engine). */
    public boolean classic() {
        return category == 0;
    }

    @Nullable
    public static EventType byId(String id) {
        for (EventType t : values()) if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
        return null;
    }
}

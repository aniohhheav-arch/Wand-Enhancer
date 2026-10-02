package dev.riftverse;

import dev.riftverse.multiverse.event.EventType;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class RiftverseConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue BLACK_HOLES_BREAK_BLOCKS = BUILDER
            .comment("Whether black holes tear loose blocks from the terrain and swallow them.")
            .define("blackHolesBreakBlocks", true);

    public static final ModConfigSpec.BooleanValue BLACK_HOLES_BREAK_BLOCKS_IN_OVERWORLD = BUILDER
            .comment("Whether player-made black holes may tear blocks in the Overworld, Nether and End.")
            .define("blackHolesBreakBlocksInVanillaDimensions", false);

    public static final ModConfigSpec.IntValue NATURAL_RIFT_RARITY = BUILDER
            .comment("One natural rift per this many chunks in the Overworld (higher = rarer). Requires a world restart.")
            .defineInRange("naturalRiftRarity", 90, 8, 4096);

    public static final ModConfigSpec.IntValue MAX_PROMPT_UNIVERSES_PER_PLAYER = BUILDER
            .comment("How many universes a single player may manifest from text prompts.")
            .defineInRange("maxPromptUniversesPerPlayer", 64, 1, 100000);

    public static final ModConfigSpec.BooleanValue UNIVERSE_CREATURE_SPAWNING = BUILDER
            .comment("Whether universe-specific creatures spawn around players in the Expanse.")
            .define("universeCreatureSpawning", true);

    // ------------------------------------------------------------------ multiverse events

    public static final ModConfigSpec.BooleanValue NATURAL_EVENTS;
    public static final ModConfigSpec.IntValue EVENT_CHECK_SECONDS;
    public static final ModConfigSpec.IntValue EVENT_MIN_GAP_MINUTES;
    public static final ModConfigSpec.BooleanValue EVENTS_ALTER_TERRAIN;
    public static final ModConfigSpec.BooleanValue EVENTS_IN_VANILLA_DIMENSIONS;
    public static final Map<EventType, ModConfigSpec.IntValue> EVENT_RARITY = new EnumMap<>(EventType.class);
    public static final Map<EventType, ModConfigSpec.IntValue> EVENT_COOLDOWN = new EnumMap<>(EventType.class);

    // ------------------------------------------------------------------ reality manipulation

    public static final ModConfigSpec.IntValue REWRITE_RADIUS;
    public static final ModConfigSpec.IntValue REWRITE_COLUMNS_PER_TICK;
    public static final ModConfigSpec.IntValue BACKUPS_PER_UNIVERSE;
    public static final ModConfigSpec.IntValue SNAPSHOT_RADIUS;
    public static final ModConfigSpec.IntValue SNAPSHOT_HEIGHT;
    public static final ModConfigSpec.BooleanValue REMOTE_REQUIRES_RANK;
    public static final ModConfigSpec.BooleanValue PROTECT_PRIME_UNIVERSES;
    public static final ModConfigSpec.BooleanValue ALLOW_ENDING_DIMENSIONS;

    // ------------------------------------------------------------------ cinematics

    public static final Map<dev.riftverse.multiverse.EndProtocol, ModConfigSpec.IntValue> PROTOCOL_SECONDS = new java.util.EnumMap<>(dev.riftverse.multiverse.EndProtocol.class);
    public static final ModConfigSpec.BooleanValue CINEMATIC_CAMERA;
    public static final ModConfigSpec.BooleanValue HEAVY_EFFECTS;
    public static final ModConfigSpec.IntValue GENESIS_SECONDS;
    public static final ModConfigSpec.IntValue RUPTURE_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> RUPTURE_MODE;
    public static final ModConfigSpec.BooleanValue ENTITY_MIGRATION;
    public static final ModConfigSpec.IntValue RIFT_SEEKING_CHANCE;

    static {
        BUILDER.push("events");
        NATURAL_EVENTS = BUILDER.comment("Whether multiverse events (collapses, invasions, leviathans...) happen on their own.")
                .define("naturalEvents", true);
        EVENT_CHECK_SECONDS = BUILDER.comment("How often (seconds) each player's surroundings roll for a natural event.")
                .defineInRange("checkIntervalSeconds", 60, 5, 3600);
        EVENT_MIN_GAP_MINUTES = BUILDER.comment("Minimum minutes between any two natural events on the server.")
                .defineInRange("minMinutesBetweenEvents", 20, 0, 10080);
        EVENTS_ALTER_TERRAIN = BUILDER.comment("Whether events such as Reality Collapse may tear blocks loose inside Riftverse universes.")
                .define("eventsAlterTerrain", true);
        EVENTS_IN_VANILLA_DIMENSIONS = BUILDER.comment("Whether natural events may also occur in the Overworld, Nether and End (never alters vanilla terrain).")
                .define("eventsInVanillaDimensions", true);
        for (EventType type : EventType.values()) {
            BUILDER.push(type.id);
            EVENT_RARITY.put(type, BUILDER.comment("Rarity: one natural occurrence per this many eligible checks (0 = never natural).")
                    .defineInRange("rarity", type.defaultRarity, 0, 1_000_000));
            EVENT_COOLDOWN.put(type, BUILDER.comment("Minutes before this event can occur naturally again.")
                    .defineInRange("cooldownMinutes", type.defaultCooldownMinutes, 0, 100_000));
            BUILDER.pop();
        }
        BUILDER.pop();

        BUILDER.push("reality");
        REWRITE_RADIUS = BUILDER.comment("Radius (blocks) around each affected player / universe centre that erase and rebuild operations rewrite.")
                .defineInRange("rewriteRadius", 96, 16, 512);
        REWRITE_COLUMNS_PER_TICK = BUILDER.comment("Block columns rewritten per server tick by erase/rebuild waves (lower = smoother TPS, slower waves).")
                .defineInRange("rewriteColumnsPerTick", 256, 8, 4096);
        BACKUPS_PER_UNIVERSE = BUILDER.comment("How many definition backups are kept per universe for restoration.")
                .defineInRange("backupsPerUniverse", 5, 1, 64);
        SNAPSHOT_RADIUS = BUILDER.comment("Horizontal radius of block snapshots taken with /multiverse reality snapshot.")
                .defineInRange("snapshotRadius", 24, 4, 64);
        SNAPSHOT_HEIGHT = BUILDER.comment("Height of block snapshots (centred slightly below the player).")
                .defineInRange("snapshotHeight", 64, 8, 256);
        REMOTE_REQUIRES_RANK = BUILDER.comment("Whether the Reality Remote's destructive modes require cosmic research rank (creative players are exempt).")
                .define("remoteRequiresRank", true);
        BUILDER.push("protocols");
        for (dev.riftverse.multiverse.EndProtocol p : dev.riftverse.multiverse.EndProtocol.values()) {
            PROTOCOL_SECONDS.put(p, BUILDER.comment("Length in seconds of the " + p.title + " sequence before the universe is unmade.")
                    .defineInRange(p.id + "Seconds", p.defaultSeconds, 6, 120));
        }
        BUILDER.pop();
        PROTECT_PRIME_UNIVERSES = BUILDER.comment("Whether the Reality Remote refuses to erase the 28 prime realities (commands can still do it).")
                .define("protectPrimeUniverses", false);
        ALLOW_ENDING_DIMENSIONS = BUILDER.comment("Whether the Reality Remote may permanently end whole dimensions (Overworld, Nether, End, modded worlds).")
                .define("allowEndingVanillaDimensions", true);
        BUILDER.pop();

        BUILDER.push("migration");
        ENTITY_MIGRATION = BUILDER.comment("Whether creatures can travel through rifts and be carried off by black holes into other universes.")
                .define("entityMigration", true);
        RIFT_SEEKING_CHANCE = BUILDER.comment("Per mille chance, every 10 seconds, that an idle mob near a rift goes to investigate it (0 disables wandering into rifts).")
                .defineInRange("riftSeekingPerMille", 15, 0, 1000);
        BUILDER.pop();

        BUILDER.push("rupture");
        RUPTURE_RADIUS = BUILDER.comment("Radius of Existence Disassembly and of the Final Rupture's area mode. Disassembled terrain is snapshotted and restorable.")
                .defineInRange("radius", 10, 2, 32);
        RUPTURE_MODE = BUILDER.comment("What THE FINAL RUPTURE does: visual (spectacle only), area (temporary disassembly, auto-restored) or universe (erases the current universe through the normal backed-up erase).")
                .define("ultimateMode", "area");
        BUILDER.pop();

        BUILDER.push("cinematics");
        CINEMATIC_CAMERA = BUILDER.comment("Whether reality cinematics take over the camera (disable for motion sensitivity).")
                .define("cinematicCamera", true);
        HEAVY_EFFECTS = BUILDER.comment("Whether expensive particle effects are used by events and reality rewrites.")
                .define("heavyEffects", true);
        GENESIS_SECONDS = BUILDER.comment("Length in seconds of the Genesis Protocol rebirth cinematic played after a universe is erased.")
                .defineInRange("genesisSeconds", 48, 10, 180);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Reads a config value, falling back to a default when the config has not loaded yet. */
    public static int get(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private RiftverseConfig() {}
}

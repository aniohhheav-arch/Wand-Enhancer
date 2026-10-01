package dev.riftverse;

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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private RiftverseConfig() {}
}

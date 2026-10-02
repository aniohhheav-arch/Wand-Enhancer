package dev.mysticarts;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative balance and world-safety settings ({@code config/mysticarts-common.toml}). */
public final class MaConfig {
    public enum SnapMode { VISUAL_ONLY, HALF_HOSTILE, HALF_ALL_MOBS }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("Energy, cooldowns and ultimates").push("balance");
    }

    public static final ModConfigSpec.DoubleValue COST_MULTIPLIER = B.comment("Multiplier on every ability's energy cost.")
            .defineInRange("costMultiplier", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER = B.comment("Multiplier on every ability's cooldown.")
            .defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER = B.comment("Multiplier on all ability damage.")
            .defineInRange("damageMultiplier", 1.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue MYSTIC_REGEN = B.comment("Mystic energy regenerated per second.")
            .defineInRange("mysticRegenPerSecond", 4.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue COSMIC_REGEN = B.comment("Cosmic energy regenerated per second while holding the gauntlet.")
            .defineInRange("cosmicRegenPerSecond", 6.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue ULTIMATE_GAIN = B.comment("Multiplier on how fast the ultimate meter fills.")
            .defineInRange("ultimateGainMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.BooleanValue CREATIVE_UNLIMITED = B.comment("Creative-mode players ignore energy, cooldowns and ultimate charge.")
            .define("creativeUnlimitedPower", true);
    public static final ModConfigSpec.BooleanValue REQUIRE_TOMES = B.comment("Survival players must read the mystic tomes before casting their spells.")
            .define("requireTomeProgression", true);

    static {
        B.pop().comment("World interaction. Every world-altering ability is either temporary or gated here.").push("world");
    }

    public static final ModConfigSpec.BooleanValue TERRAIN_DESTRUCTION = B.comment("Whether Power Stone destruction and Cosmic Annihilation break blocks permanently.")
            .define("powerStoneBreaksBlocks", false);
    public static final ModConfigSpec.BooleanValue TEMPORARY_BLOCKS = B.comment("Whether Reality Stone and barrier abilities may place temporary blocks (always reverted).")
            .define("temporaryBlockChanges", true);
    public static final ModConfigSpec.IntValue REWIND_SECONDS = B.comment("How far back Time Reversal / Time Stone Rewind can restore block changes.")
            .defineInRange("rewindWindowSeconds", 60, 5, 600);
    public static final ModConfigSpec.IntValue REWIND_MAX_BLOCKS = B.comment("Upper bound of block changes restored by a single rewind.")
            .defineInRange("rewindMaxBlocks", 4096, 16, 100000);
    public static final ModConfigSpec.BooleanValue FREEZE_PLAYERS = B.comment("Whether time stop also freezes other players.")
            .define("timeStopFreezesPlayers", false);
    public static final ModConfigSpec.BooleanValue AFFECT_BOSSES = B.comment("Whether control, banishment, transformation and the Snap can target bosses.")
            .define("abilitiesAffectBosses", false);
    public static final ModConfigSpec.IntValue PORTAL_RANGE = B.comment("Maximum sling ring portal distance in blocks when no anchor is set.")
            .defineInRange("slingRingRange", 96, 8, 4096);
    public static final ModConfigSpec.EnumValue<SnapMode> SNAP_MODE = B.comment("What the Snap does: VISUAL_ONLY, HALF_HOSTILE (default) or HALF_ALL_MOBS. Players are never removed.")
            .defineEnum("snapMode", SnapMode.HALF_HOSTILE);
    public static final ModConfigSpec.IntValue SNAP_RADIUS = B.comment("Radius of the Snap in blocks.")
            .defineInRange("snapRadius", 96, 8, 512);
    public static final ModConfigSpec.BooleanValue STRUCTURES = B.comment("Whether mystic structures generate in new chunks.")
            .define("generateStructures", true);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private MaConfig() {}
}

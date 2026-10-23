package com.wandcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec COMMON;
    public static final ModConfigSpec.DoubleValue FX_INTENSITY;
    public static final ModConfigSpec.DoubleValue MANA_REGEN_PER_SECOND;
    public static final ModConfigSpec.IntValue MAX_ENTITIES_PER_CASTER;
    public static final ModConfigSpec.BooleanValue SPELLS_HURT_PLAYERS;
    public static final ModConfigSpec.IntValue PORTAL_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue RIFT_GATE_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue PORTAL_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue DARK_CLONE_RANGE;
    public static final ModConfigSpec.IntValue DARK_CLONE_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue DARK_CLONE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DARK_CLONE_BLACKLIST;
    public static final ModConfigSpec.BooleanValue POSSESSION_DROPS_LOOT;
    public static final ModConfigSpec.IntValue ASH_DURATION_SECONDS;
    public static final ModConfigSpec.BooleanValue ASH_PERSISTS;
    public static final ModConfigSpec.IntValue ASH_MAX_TOTAL;
    public static final ModConfigSpec.IntValue ASH_MAX_PER_AREA;
    public static final ModConfigSpec CLIENT;
    public static final ModConfigSpec.BooleanValue REDUCED_EFFECTS;
    public static final ModConfigSpec.BooleanValue DISSOLVE_SHADER;
    public static final ModConfigSpec.DoubleValue CLIENT_FX_INTENSITY;
    public static final ModConfigSpec.BooleanValue SCREEN_EFFECTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        FX_INTENSITY = b.comment("Scales particle counts for server-spawned spell effects (0.1 - 1.0).")
                .defineInRange("fxIntensity", 1.0, 0.1, 1.0);
        MANA_REGEN_PER_SECOND = b.comment("Base mana regeneration per second.")
                .defineInRange("manaRegenPerSecond", 4.0, 0.0, 100.0);
        MAX_ENTITIES_PER_CASTER = b.comment("Maximum simultaneous spell projectiles and zones per caster.")
                .defineInRange("maxEntitiesPerCaster", 48, 4, 500);
        SPELLS_HURT_PLAYERS = b.comment("Whether offensive spells can damage other players (also requires server PvP).")
                .define("spellsHurtPlayers", true);
        PORTAL_DURATION_SECONDS = b.comment("How long a Portal Pair stays open, in seconds.").defineInRange("portalDurationSeconds", 45, 5, 600);
        RIFT_GATE_DURATION_SECONDS = b.comment("How long a Rift Gate stays open, in seconds.").defineInRange("riftGateDurationSeconds", 90, 5, 1200);
        PORTAL_MAX_DISTANCE = b.comment("Maximum distance between the two portals of a Portal Pair.").defineInRange("portalMaxDistance", 48, 4, 512);
        DARK_CLONE_RANGE = b.comment("How far (blocks) the Dark Clone searches for a target.").defineInRange("darkCloneRange", 24, 4, 128);
        DARK_CLONE_DURATION_SECONDS = b.comment("How long the Dark Clone hunts before dissolving, in seconds.").defineInRange("darkCloneDurationSeconds", 30, 5, 300);
        DARK_CLONE_COOLDOWN_SECONDS = b.comment("Dark Clone cooldown in seconds.").defineInRange("darkCloneCooldownSeconds", 30, 0, 3600);
        DARK_CLONE_BLACKLIST = b.comment("Entity ids the Dark Clone must never possess. Bosses and players are always excluded.")
                .defineListAllowEmpty("darkCloneBlacklist", java.util.List.of("minecraft:warden", "minecraft:iron_golem", "minecraft:villager", "minecraft:wandering_trader"), () -> "minecraft:pig", o -> o instanceof String);
        POSSESSION_DROPS_LOOT = b.comment("If true a possessed mob drops its normal loot once; otherwise it is consumed completely.").define("possessionDropsLoot", false);
        ASH_DURATION_SECONDS = b.comment("How long ash piles remain, in seconds. 0 keeps them until cleaned up by the limits below.").defineInRange("ashDurationSeconds", 600, 0, 86400);
        ASH_PERSISTS = b.comment("Save ash piles with the world so they survive reloads.").define("ashPersists", true);
        ASH_MAX_TOTAL = b.comment("Maximum ash piles per dimension. The oldest are removed first.").defineInRange("ashMaxTotal", 96, 4, 2000);
        ASH_MAX_PER_AREA = b.comment("Maximum ash piles within 16 blocks of a new pile.").defineInRange("ashMaxPerArea", 10, 1, 200);
        COMMON = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CLIENT_FX_INTENSITY = c.comment("Scales client-side spell particles (0.1 - 1.0). Lower this for better performance.")
                .defineInRange("fxIntensity", 1.0, 0.1, 1.0);
        REDUCED_EFFECTS = c.comment("Reduces portal and shadow ambience (streams, motes, hum particles) for low-end machines.").define("reducedEffects", false);
        DISSOLVE_SHADER = c.comment("Use the animated dissolve shader for possession. If false, a simple shrink-and-fade is used instead.").define("dissolveShader", true);
        SCREEN_EFFECTS = c.comment("Enable screen shake and tint effects.").define("screenEffects", true);
        CLIENT = c.build();
    }

    private Config() {}
}

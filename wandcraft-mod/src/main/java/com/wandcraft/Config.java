package com.wandcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec COMMON;
    public static final ModConfigSpec.DoubleValue FX_INTENSITY;
    public static final ModConfigSpec.DoubleValue MANA_REGEN_PER_SECOND;
    public static final ModConfigSpec.IntValue MAX_ENTITIES_PER_CASTER;
    public static final ModConfigSpec.BooleanValue SPELLS_HURT_PLAYERS;
    public static final ModConfigSpec CLIENT;
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
        COMMON = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CLIENT_FX_INTENSITY = c.comment("Scales client-side spell particles (0.1 - 1.0). Lower this for better performance.")
                .defineInRange("fxIntensity", 1.0, 0.1, 1.0);
        SCREEN_EFFECTS = c.comment("Enable screen shake and tint effects.").define("screenEffects", true);
        CLIENT = c.build();
    }

    private Config() {}
}

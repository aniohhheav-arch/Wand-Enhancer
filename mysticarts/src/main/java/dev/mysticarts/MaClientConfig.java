package dev.mysticarts;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player visual preferences ({@code config/mysticarts-client.toml}). */
public final class MaClientConfig {
    public enum Quality { LOW, MEDIUM, HIGH }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Quality> QUALITY = B.comment("Visual effect quality. LOW disables screen distortion and thins particles.")
            .defineEnum("effectQuality", Quality.HIGH);
    public static final ModConfigSpec.BooleanValue MINIMAL_HUD = B.comment("Show only compact energy bars and the selected ability.")
            .define("minimalHud", false);
    public static final ModConfigSpec.DoubleValue SCREEN_EFFECTS = B.comment("Strength of full-screen effects (flashes, tints, shake). 0 disables them.")
            .defineInRange("screenEffectStrength", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue SNAP_CINEMATIC = B.comment("Play the full Snap cinematic (camera, flashes, white-out).")
            .define("snapCinematic", true);

    public static final ModConfigSpec SPEC = B.build();

    private MaClientConfig() {}

    public static Quality quality() {
        return QUALITY.get();
    }

    /** Particle count scale for the current quality. */
    public static float particles() {
        return switch (quality()) {
            case LOW -> 0.3f;
            case MEDIUM -> 0.65f;
            case HIGH -> 1f;
        };
    }

    public static boolean distortion() {
        return quality() != Quality.LOW;
    }

    public static float screen() {
        return SCREEN_EFFECTS.get().floatValue();
    }
}

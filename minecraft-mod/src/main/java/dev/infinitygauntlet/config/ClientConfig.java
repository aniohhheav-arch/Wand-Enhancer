package dev.infinitygauntlet.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player presentation settings. */
public final class ClientConfig {
    public enum Quality {
        LOW(0.35f), MEDIUM(0.65f), HIGH(1.0f), ULTRA(1.5f);

        public final float density;

        Quality(float density) {
            this.density = density;
        }
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<Quality> VFX_QUALITY;
    public static final ModConfigSpec.IntValue VFX_DISTANCE;
    public static final ModConfigSpec.BooleanValue CAMERA_EFFECTS;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue SCREEN_FLASHES;
    public static final ModConfigSpec.BooleanValue PORTAL_TRANSITION;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.EnumValue<DistortionMode> DISTORTION;

    public enum DistortionMode {
        /** Post shader, or the overlay when a shader-pack mod is installed or the shader fails to load. */
        AUTO,
        SHADER,
        OVERLAY,
        OFF
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("visuals");
        VFX_QUALITY = b.comment("Particle density and mesh detail for all cinematic effects.").defineEnum("vfxQuality", Quality.HIGH);
        VFX_DISTANCE = b.comment("Effects further than this many blocks from the camera are skipped.").defineInRange("vfxDistance", 128, 16, 512);
        DISTORTION = b.comment("Reality distortion: AUTO uses the screen shader, or a shader-pack-safe overlay when Iris/Oculus is installed.")
                .defineEnum("realityDistortion", DistortionMode.AUTO);
        b.pop();
        b.push("camera");
        CAMERA_EFFECTS = b.comment("Camera shake, zoom and slow-motion pushes.").define("cameraEffects", true);
        CAMERA_SHAKE = b.comment("Camera shake intensity multiplier.").defineInRange("cameraShakeIntensity", 1.0, 0.0, 3.0);
        SCREEN_FLASHES = b.define("screenFlashes", true);
        PORTAL_TRANSITION = b.comment("Show the swirling dimensional overlay when travelling through portals.").define("portalTransition", true);
        b.pop();
        b.push("hud");
        SHOW_HUD = b.define("showAbilityHud", true);
        b.pop();
        SPEC = b.build();
    }

    public static Quality quality() {
        return SPEC.isLoaded() ? VFX_QUALITY.get() : Quality.HIGH;
    }

    public static boolean cameraEffects() {
        return !SPEC.isLoaded() || CAMERA_EFFECTS.get();
    }

    private ClientConfig() {
    }
}

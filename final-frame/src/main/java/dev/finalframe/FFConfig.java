package dev.finalframe;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FFConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // Gameplay (server-authoritative, synced to clients so the activation prompt agrees).
    public static final ModConfigSpec.DoubleValue ACTIVATION_RANGE;
    public static final ModConfigSpec.DoubleValue BEHIND_ANGLE;
    public static final ModConfigSpec.BooleanValue REQUIRE_SNEAK;
    public static final ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue ALLOW_PLAYER_TARGETS;
    public static final ModConfigSpec.DoubleValue MAX_TARGET_HEIGHT;
    public static final ModConfigSpec.DoubleValue MAX_TARGET_HEALTH;
    public static final ModConfigSpec.BooleanValue WORLD_SLOW_MOTION;
    public static final ModConfigSpec.DoubleValue SLOW_MOTION_FACTOR;
    public static final ModConfigSpec.BooleanValue INVULNERABLE_DURING_FINISHER;
    public static final ModConfigSpec.DoubleValue SHOT_DAMAGE;

    // Presentation (per player).
    public static final ModConfigSpec.BooleanValue LETTERBOX;
    public static final ModConfigSpec.BooleanValue COLOR_GRADING;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue SHOW_PROMPT;
    public static final ModConfigSpec.BooleanValue RENDER_HOLSTER;
    public static final ModConfigSpec.BooleanValue DUCK_WORLD_AUDIO;
    public static final ModConfigSpec.BooleanValue TITLE_CARD;

    static {
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        s.push("activation");
        ACTIVATION_RANGE = s.comment("Maximum horizontal distance (blocks) between you and the target.")
            .defineInRange("activationRange", 3.0, 1.0, 6.0);
        BEHIND_ANGLE = s.comment("Width of the cone behind the target (degrees) you must stand in.")
            .defineInRange("behindAngle", 110.0, 30.0, 270.0);
        REQUIRE_SNEAK = s.comment("Require sneaking while using the weapon on the target, so ordinary combat never triggers it.")
            .define("requireSneak", true);
        COOLDOWN_TICKS = s.comment("Cooldown applied to the weapon after a finisher (ticks).")
            .defineInRange("cooldownTicks", 120, 0, 72000);
        ALLOW_PLAYER_TARGETS = s.comment("Allow finishers on other players.").define("allowPlayerTargets", false);
        MAX_TARGET_HEIGHT = s.comment("Tallest eligible target (blocks). Keeps the camera choreography framed.")
            .defineInRange("maxTargetHeight", 3.2, 0.5, 16.0);
        MAX_TARGET_HEALTH = s.comment("Highest max-health an eligible target may have. 0 disables the limit.")
            .defineInRange("maxTargetHealth", 0.0, 0.0, 4096.0);
        s.pop();
        s.push("sequence");
        WORLD_SLOW_MOTION = s.comment("Slow the actual game tick rate during the throw. Only applied when you are the only player online.")
            .define("worldSlowMotion", true);
        SLOW_MOTION_FACTOR = s.comment("Tick-rate multiplier used for world slow motion.")
            .defineInRange("slowMotionFactor", 0.5, 0.1, 1.0);
        INVULNERABLE_DURING_FINISHER = s.comment("Protect the performer and the target from outside damage while the sequence plays.")
            .define("invulnerableDuringFinisher", true);
        s.pop();
        s.push("revolver");
        SHOT_DAMAGE = s.comment("Damage of an ordinary revolver shot.").defineInRange("shotDamage", 7.0, 0.0, 100.0);
        s.pop();
        SERVER_SPEC = s.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.push("cinematics");
        LETTERBOX = c.define("letterbox", true);
        COLOR_GRADING = c.comment("Western color grade, desaturated slow motion and muzzle flash post effect.")
            .define("colorGrading", true);
        CAMERA_SHAKE = c.comment("Camera shake multiplier. 0 disables shake.").defineInRange("cameraShake", 1.0, 0.0, 2.0);
        DUCK_WORLD_AUDIO = c.comment("Duck other sounds after the final shot.").define("duckWorldAudio", true);
        TITLE_CARD = c.comment("Show the 'The Last Word' title card at the end of the sequence.").define("titleCard", true);
        c.pop();
        c.push("hud");
        SHOW_PROMPT = c.comment("Show the activation prompt when a finisher is available.").define("showPrompt", true);
        RENDER_HOLSTER = c.comment("Render the leather holster on players carrying the revolver.").define("renderHolster", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private FFConfig() {
    }

    /** Reads a config value, falling back to its default before configs load (e.g. on the title screen). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}

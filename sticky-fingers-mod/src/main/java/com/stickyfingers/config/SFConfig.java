package com.stickyfingers.config;

import com.stickyfingers.ability.AbilityType;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server config (synced to clients so the HUD shows authoritative numbers) and client-only presentation config.
 */
public final class SFConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue GRANT_BY_DEFAULT;
    public static final ModConfigSpec.DoubleValue MAX_ENERGY;
    public static final ModConfigSpec.DoubleValue ENERGY_REGEN;
    public static final ModConfigSpec.IntValue MAX_ZIPPERS;
    public static final ModConfigSpec.IntValue PORTAL_COOLDOWN;
    public static final ModConfigSpec.BooleanValue SEPARATE_BLOCK_ENTITIES;
    public static final ModConfigSpec.BooleanValue RESPECT_PROTECTION;
    public static final ModConfigSpec.DoubleValue MAX_BLOCK_HARDNESS;
    public static final ModConfigSpec.DoubleValue TRAP_RADIUS;
    public static final ModConfigSpec.BooleanValue TRAP_AFFECTS_PLAYERS;
    public static final ModConfigSpec.BooleanValue PVP_EFFECTS;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DODGE_CHANCE;
    public static final ModConfigSpec.IntValue WORLD_ZIPPER_COUNT;

    public static final Map<AbilityType, AbilityConfig> ABILITIES = new EnumMap<>(AbilityType.class);

    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue SCREEN_EFFECTS;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.IntValue PARTICLE_DENSITY;

    public record AbilityConfig(ModConfigSpec.BooleanValue enabled, ModConfigSpec.IntValue cooldown,
                                ModConfigSpec.IntValue cost, ModConfigSpec.IntValue range,
                                ModConfigSpec.IntValue duration) {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        GRANT_BY_DEFAULT = b.comment("Give every player Sticky Fingers on join. When false a Stand Arrow is required.")
                .define("grantStandByDefault", true);
        MAX_ENERGY = b.comment("Maximum Stand energy.").defineInRange("maxEnergy", 100.0, 1.0, 10000.0);
        ENERGY_REGEN = b.comment("Energy regenerated per second.").defineInRange("energyRegenPerSecond", 6.0, 0.0, 1000.0);
        MAX_ZIPPERS = b.comment("Maximum simultaneously active zippers per player. Oldest zippers close first.")
                .defineInRange("maxZippersPerPlayer", 24, 1, 512);
        PORTAL_COOLDOWN = b.comment("Ticks before an entity can travel through a zipper again.").defineInRange("portalCooldownTicks", 30, 0, 1200);
        DAMAGE_MULTIPLIER = b.comment("Multiplier for all Stand damage.").defineInRange("damageMultiplier", 1.0, 0.0, 100.0);
        DODGE_CHANCE = b.comment("Chance to phase through an attack while Zipper Body is active.").defineInRange("bodyDodgeChance", 0.4, 0.0, 1.0);
        PVP_EFFECTS = b.comment("Allow immobilizing effects to be applied to other players.").define("pvpEffects", true);
        WORLD_ZIPPER_COUNT = b.comment("Number of seams spawned by Zipper World (visual only, rendered client side).")
                .defineInRange("zipperWorldSeams", 160, 8, 600);
        b.pop();

        b.push("world");
        RESPECT_PROTECTION = b.comment("Fire block-break events before separating blocks so claim/protection mods can veto.")
                .define("respectProtection", true);
        SEPARATE_BLOCK_ENTITIES = b.comment("Allow separating blocks with block entities (chests, furnaces...). Contents are restored when closed.")
                .define("separateBlockEntities", false);
        MAX_BLOCK_HARDNESS = b.comment("Blocks harder than this cannot be unzipped (obsidian = 50).")
                .defineInRange("maxBlockHardness", 60.0, 0.0, 10000.0);
        b.pop();

        b.push("trap");
        TRAP_RADIUS = b.comment("Activation distance of a Zipper Trap.").defineInRange("activationRadius", 1.6, 0.5, 16.0);
        TRAP_AFFECTS_PLAYERS = b.comment("Whether traps trigger on players other than the owner.").define("affectsPlayers", true);
        b.pop();

        b.push("abilities");
        for (AbilityType type : AbilityType.VALUES) {
            b.push(type.key());
            ABILITIES.put(type, new AbilityConfig(
                    b.define("enabled", true),
                    b.comment("Cooldown in ticks").defineInRange("cooldown", type.defaultCooldown, 0, 72000),
                    b.comment("Energy cost").defineInRange("cost", type.defaultCost, 0, 10000),
                    b.comment("Range in blocks (meaning depends on the ability)").defineInRange("range", type.defaultRange, 0, 256),
                    b.comment("Duration in ticks (0 = ability specific / instant)").defineInRange("duration", type.defaultDuration, 0, 72000)));
            b.pop();
        }
        b.pop();
        SERVER_SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CAMERA_SHAKE = c.comment("Camera shake strength multiplier (0 disables).").defineInRange("cameraShake", 1.0, 0.0, 3.0);
        SCREEN_EFFECTS = c.comment("Zipper transitions, flashes, vignettes and speed lines.").define("screenEffects", true);
        SHOW_HUD = c.define("showHud", true);
        PARTICLE_DENSITY = c.comment("Client particle density percent.").defineInRange("particleDensity", 100, 0, 100);
        CLIENT_SPEC = c.build();
    }

    public static AbilityConfig of(AbilityType type) {
        return ABILITIES.get(type);
    }

    public static int cooldown(AbilityType t) { return safe(() -> of(t).cooldown().get(), t.defaultCooldown); }
    public static int cost(AbilityType t) { return safe(() -> of(t).cost().get(), t.defaultCost); }
    public static int range(AbilityType t) { return safe(() -> of(t).range().get(), t.defaultRange); }
    public static int duration(AbilityType t) { return safe(() -> of(t).duration().get(), t.defaultDuration); }
    public static boolean enabled(AbilityType t) { return safe(() -> of(t).enabled().get(), true); }

    /** Config values are unavailable before the server config loads (e.g. title screen); fall back to defaults. */
    public static <T> T safe(java.util.function.Supplier<T> s, T fallback) {
        try {
            return s.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private SFConfig() {}
}

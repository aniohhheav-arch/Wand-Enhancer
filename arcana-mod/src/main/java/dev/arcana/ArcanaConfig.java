package dev.arcana;

import dev.arcana.magic.Spell;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side gameplay config (every spell is individually tunable) and client-side presentation config. */
public final class ArcanaConfig {
    public static final ModConfigSpec SERVER;
    public static final ModConfigSpec CLIENT;

    public static final ModConfigSpec.DoubleValue BASE_MANA, MANA_REGEN, DAMAGE, COOLDOWN, MANA_COST, PARTICLES;
    public static final ModConfigSpec.IntValue MAX_EFFECTS, AWAKEN_MASTERY, ASCEND_MASTERY, AWAKEN_CRYSTALS, ASCEND_CRYSTALS,
        MIMIC_WINDOW, WARP_BLOCKS, BOSS_HEALTH;
    public static final ModConfigSpec.BooleanValue BLOCK_CHANGES, TERRAIN_DISTORTION, RESPECT_PVP, CREATIVE_FREE;

    public static final Map<Spell, SpellCfg> SPELLS = new EnumMap<>(Spell.class);

    public record SpellCfg(ModConfigSpec.BooleanValue enabled, ModConfigSpec.DoubleValue damage,
                           ModConfigSpec.DoubleValue cooldown, ModConfigSpec.DoubleValue mana) {}

    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE, CINEMATICS;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        BASE_MANA = b.comment("Base maximum mana of a player.").defineInRange("baseMana", 100.0, 10.0, 10000.0);
        MANA_REGEN = b.comment("Mana regenerated per tick.").defineInRange("manaRegenPerTick", 0.12, 0.0, 50.0);
        DAMAGE = b.comment("Global spell damage multiplier.").defineInRange("damageMultiplier", 1.0, 0.0, 100.0);
        COOLDOWN = b.comment("Global cooldown multiplier.").defineInRange("cooldownMultiplier", 1.0, 0.0, 100.0);
        MANA_COST = b.comment("Global mana cost multiplier.").defineInRange("manaCostMultiplier", 1.0, 0.0, 100.0);
        PARTICLES = b.comment("Particle density multiplier for spell visuals (lower for performance).").defineInRange("particleDensity", 1.0, 0.05, 4.0);
        MAX_EFFECTS = b.comment("Maximum number of simultaneously running spell effects per dimension.").defineInRange("maxActiveEffects", 256, 8, 4096);
        BLOCK_CHANGES = b.comment("Allow spells to touch blocks (frosted ice, bone meal growth).").define("blockChanges", true);
        TERRAIN_DISTORTION = b.comment("Allow the Forbidden Wand to temporarily distort terrain. Blocks are always restored.").define("terrainDistortion", true);
        WARP_BLOCKS = b.comment("Maximum number of blocks a single terrain warp may alter.").defineInRange("terrainWarpMaxBlocks", 70, 0, 1000);
        RESPECT_PVP = b.comment("Spells only hurt other players if PvP is enabled.").define("respectPvp", true);
        CREATIVE_FREE = b.comment("Creative players ignore mana and cooldowns.").define("creativeIgnoresCosts", true);
        MIMIC_WINDOW = b.comment("Ticks after casting a school's spell during which the Forbidden Wand can mimic it.").defineInRange("mimicWindowTicks", 12000, 20, 1000000);
        b.pop();
        b.push("evolution");
        AWAKEN_MASTERY = b.defineInRange("awakenMasteryLevel", 3, 0, 10);
        ASCEND_MASTERY = b.defineInRange("ascendMasteryLevel", 6, 0, 10);
        AWAKEN_CRYSTALS = b.defineInRange("awakenCrystals", 4, 0, 64);
        ASCEND_CRYSTALS = b.defineInRange("ascendCrystals", 8, 0, 64);
        b.pop();
        b.push("boss");
        BOSS_HEALTH = b.comment("Hollow Archmage max health.").defineInRange("archmageHealth", 400, 20, 100000);
        b.pop();
        b.push("spells");
        for (Spell s : Spell.values()) {
            b.push(s.id);
            SPELLS.put(s, new SpellCfg(
                b.define("enabled", true),
                b.defineInRange("damageMultiplier", 1.0, 0.0, 100.0),
                b.defineInRange("cooldownMultiplier", 1.0, 0.0, 100.0),
                b.defineInRange("manaMultiplier", 1.0, 0.0, 100.0)));
            b.pop();
        }
        b.pop();
        SERVER = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        SCREEN_SHAKE = c.comment("Camera shake during ultimates.").define("screenShake", true);
        CINEMATICS = c.comment("Full-screen colour flashes during ultimates.").define("cinematics", true);
        HUD_SCALE = c.defineInRange("hudScale", 1.0, 0.5, 2.0);
        CLIENT = c.build();
    }

    private ArcanaConfig() {}
}

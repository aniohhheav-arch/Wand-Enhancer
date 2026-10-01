package com.infinitemultiverse.core.config;

import com.infinitemultiverse.core.MultiverseSystem;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * SERVER config is per world and synced to clients, so gameplay balance lives there and the client can show
 * the real costs/cooldowns. CLIENT config only affects presentation.
 */
public final class MultiverseConfig {
    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private MultiverseConfig() {
    }

    /** Default energy cost and cooldown (ticks) of every Stand ability, keyed by ability id path. Lazily initialised holder. */
    private static final class StandDefaults {
        static final Map<String, double[]> MAP = new LinkedHashMap<>();

        static void add(String id, double cost, int cooldown) {
            MAP.put(id, new double[]{cost, cooldown});
        }

        static {
            add("ora_barrage", 20, 100);
            add("star_finger", 25, 80);
            add("star_guard", 15, 160);
            add("star_platinum_the_world", 60, 600);
            add("muda_barrage", 20, 100);
            add("knife_throw", 15, 60);
            add("za_warudo", 75, 800);
            add("first_bomb", 10, 40);
            add("detonate", 20, 60);
            add("bites_the_dust", 50, 900);
            add("gold_experience_barrage", 20, 100);
            add("life_giver", 25, 300);
            add("healing_field", 30, 500);
            add("epitaph", 20, 400);
            add("time_erase", 45, 500);
            add("king_crimson_chop", 30, 120);
            add("heaven_acceleration", 15, 200);
            add("time_acceleration", 40, 1200);
            add("heaven_blink", 15, 40);
            add("nail_shot", 8, 20);
            add("golden_rotation", 30, 200);
            add("infinite_rotation", 60, 900);
        }
    }

    public static boolean isSystemEnabled(MultiverseSystem system) {
        return !SERVER_SPEC.isLoaded() || SERVER.systemEnabled.get(system).get();
    }

    public record AbilityTuning(ModConfigSpec.DoubleValue energyCost, ModConfigSpec.IntValue cooldownTicks) {
        static AbilityTuning define(ModConfigSpec.Builder builder, double cost, int cooldown) {
            return new AbilityTuning(
                    builder.comment("Energy consumed on activation.").defineInRange("energyCost", cost, 0.0, 10_000.0),
                    builder.comment("Cooldown in ticks (20 ticks = 1 second).").defineInRange("cooldownTicks", cooldown, 0, 72_000));
        }

        public float cost() {
            return energyCost.get().floatValue();
        }

        public int cooldown() {
            return cooldownTicks.get();
        }
    }

    public static final class Server {
        public final ModConfigSpec.DoubleValue maxEnergy;
        public final ModConfigSpec.DoubleValue regenPerSecond;
        public final ModConfigSpec.IntValue regenDelayTicks;
        public final ModConfigSpec.BooleanValue creativeIgnoresEnergy;

        public final Map<MultiverseSystem, ModConfigSpec.BooleanValue> systemEnabled = new EnumMap<>(MultiverseSystem.class);

        public final ModConfigSpec.BooleanValue allowTerrainModification;
        public final ModConfigSpec.IntValue vfxBroadcastRange;

        public final AbilityTuning phaseStep;
        public final ModConfigSpec.IntValue phaseStepRange;

        public final AbilityTuning kineticLeap;
        public final ModConfigSpec.DoubleValue kineticLeapStrength;

        public final AbilityTuning shockwave;
        public final ModConfigSpec.DoubleValue shockwaveRadius;
        public final ModConfigSpec.DoubleValue shockwaveDamage;
        public final ModConfigSpec.DoubleValue shockwaveKnockback;

        public final AbilityTuning aegisField;
        public final ModConfigSpec.DoubleValue aegisUpkeep;
        public final ModConfigSpec.DoubleValue aegisDamageReduction;
        public final ModConfigSpec.DoubleValue aegisEnergyPerDamage;

        public final AbilityTuning temporalDrag;
        public final ModConfigSpec.DoubleValue temporalDragRadius;
        public final ModConfigSpec.IntValue temporalDragDuration;

        public final ModConfigSpec.BooleanValue allowStandReroll;
        public final AbilityTuning standManifest;
        public final ModConfigSpec.DoubleValue standUpkeep;
        public final ModConfigSpec.DoubleValue standReach;

        public final ModConfigSpec.IntValue barrageDuration;
        public final ModConfigSpec.DoubleValue barrageDamage;
        public final ModConfigSpec.DoubleValue precisionDamage;
        public final ModConfigSpec.DoubleValue precisionKnockback;
        public final ModConfigSpec.IntValue guardDuration;
        public final ModConfigSpec.DoubleValue guardReduction;
        public final ModConfigSpec.IntValue timeStopMaxDuration;
        public final ModConfigSpec.DoubleValue timeStopUpkeep;
        public final ModConfigSpec.DoubleValue timeStopCooldownPerTick;
        public final ModConfigSpec.DoubleValue timeStopRadius;
        public final ModConfigSpec.BooleanValue timeStopFreezesPlayers;
        private final Map<String, AbilityTuning> standAbilities = new HashMap<>();

        /** Cost/cooldown of a Stand ability by its id path. */
        public AbilityTuning standTuning(String abilityPath) {
            AbilityTuning tuning = standAbilities.get(abilityPath);
            if (tuning == null) {
                throw new IllegalArgumentException("No config entry for stand ability " + abilityPath);
            }
            return tuning;
        }

        Server(ModConfigSpec.Builder b) {
            b.comment("Shared multiverse energy pool used by every ability.").push("energy");
            maxEnergy = b.comment("Maximum energy a player can hold.").defineInRange("maxEnergy", 100.0, 1.0, 10_000.0);
            regenPerSecond = b.comment("Energy regenerated per second.").defineInRange("regenPerSecond", 6.0, 0.0, 1_000.0);
            regenDelayTicks = b.comment("Ticks after spending energy before regeneration resumes.").defineInRange("regenDelayTicks", 40, 0, 1_200);
            creativeIgnoresEnergy = b.comment("Creative-mode players use abilities without spending energy.").define("creativeIgnoresEnergy", true);
            b.pop();

            b.comment("Enable or disable whole systems. Disabled systems keep their registrations but cannot be used.").push("systems");
            for (MultiverseSystem system : MultiverseSystem.values()) {
                systemEnabled.put(system, b.define(system.id(), true));
            }
            b.pop();

            b.push("safety");
            allowTerrainModification = b.comment(
                    "Master switch for abilities that permanently change terrain. Phase 1 abilities never modify terrain;",
                    "later destructive abilities must check this and default to non-destructive behaviour.")
                    .define("allowTerrainModification", false);
            b.pop();

            b.push("presentation");
            vfxBroadcastRange = b.comment("Players within this many blocks receive ability visual effects.").defineInRange("vfxBroadcastRange", 64, 16, 256);
            b.pop();

            b.push("abilities");

            b.push("phase_step");
            phaseStep = AbilityTuning.define(b, 18.0, 40);
            phaseStepRange = b.comment("Maximum blink distance in blocks.").defineInRange("range", 12, 2, 64);
            b.pop();

            b.push("kinetic_leap");
            kineticLeap = AbilityTuning.define(b, 12.0, 30);
            kineticLeapStrength = b.comment("Launch velocity multiplier.").defineInRange("strength", 1.6, 0.2, 5.0);
            b.pop();

            b.push("shockwave");
            shockwave = AbilityTuning.define(b, 30.0, 120);
            shockwaveRadius = b.comment("Radius in blocks.").defineInRange("radius", 5.0, 1.0, 16.0);
            shockwaveDamage = b.comment("Damage at the centre (falls off to 40% at the edge).").defineInRange("damage", 6.0, 0.0, 100.0);
            shockwaveKnockback = b.comment("Horizontal knockback strength.").defineInRange("knockback", 1.4, 0.0, 5.0);
            b.pop();

            b.push("aegis_field");
            aegisField = AbilityTuning.define(b, 10.0, 100);
            aegisUpkeep = b.comment("Energy drained per second while the field is active.").defineInRange("upkeepPerSecond", 8.0, 0.0, 100.0);
            aegisDamageReduction = b.comment("Fraction of incoming damage absorbed by the field.").defineInRange("damageReduction", 0.5, 0.0, 1.0);
            aegisEnergyPerDamage = b.comment("Extra energy spent per point of damage absorbed.").defineInRange("energyPerDamage", 2.0, 0.0, 50.0);
            b.pop();

            b.push("temporal_drag");
            temporalDrag = AbilityTuning.define(b, 25.0, 200);
            temporalDragRadius = b.comment("Radius in blocks.").defineInRange("radius", 7.0, 1.0, 24.0);
            temporalDragDuration = b.comment("Slow duration in ticks.").defineInRange("durationTicks", 100, 10, 1_200);
            b.pop();

            b.pop();

            b.comment("Stand system (Phase 2).").push("stands");
            allowStandReroll = b.comment("Allow an Awakening Arrowhead to replace an existing Stand.").define("allowReroll", false);
            standReach = b.comment("How far in front of its user a Stand can strike, in blocks.").defineInRange("reach", 5.0, 2.0, 16.0);

            b.push("manifest");
            standManifest = AbilityTuning.define(b, 10.0, 40);
            standUpkeep = b.comment("Energy drained per second while the Stand is manifested.").defineInRange("upkeepPerSecond", 1.5, 0.0, 100.0);
            b.pop();

            b.push("barrage");
            barrageDuration = b.comment("Barrage length in ticks.").defineInRange("durationTicks", 30, 5, 200);
            barrageDamage = b.comment("Damage per hit (a hit lands every 2 ticks).").defineInRange("damagePerHit", 1.2, 0.0, 50.0);
            b.pop();

            b.push("heavy_strike");
            precisionDamage = b.comment("Base damage of single heavy strikes (Star Finger, King Crimson's chop scales it).").defineInRange("damage", 9.0, 0.0, 200.0);
            precisionKnockback = b.comment("Knockback strength.").defineInRange("knockback", 2.0, 0.0, 6.0);
            b.pop();

            b.push("guard");
            guardDuration = b.comment("Guard length in ticks.").defineInRange("durationTicks", 60, 10, 400);
            guardReduction = b.comment("Fraction of melee damage blocked while guarding.").defineInRange("damageReduction", 0.4, 0.0, 1.0);
            b.pop();

            b.push("time_stop");
            timeStopMaxDuration = b.comment("Time stop is a toggle; this is the hard cap in ticks before time resumes on its own (The World holds it longer).").defineInRange("maxDurationTicks", 400, 20, 6000);
            timeStopUpkeep = b.comment("Energy drained per second while time is stopped (survival).").defineInRange("upkeepPerSecond", 10.0, 0.0, 200.0);
            timeStopCooldownPerTick = b.comment("Survival: extra cooldown ticks per tick time was held stopped, on top of the base cooldown. Creative has no cooldown.").defineInRange("cooldownPerStoppedTick", 4.0, 0.0, 100.0);
            timeStopRadius = b.comment("Radius of frozen time around the user, in blocks.").defineInRange("radius", 24.0, 4.0, 64.0);
            timeStopFreezesPlayers = b.comment("Also freeze other survival/adventure players.").define("freezePlayers", true);
            b.pop();

            b.comment("Energy cost and cooldown of every Stand ability.").push("abilities");
            StandDefaults.MAP.forEach((id, defaults) -> {
                b.push(id);
                standAbilities.put(id, AbilityTuning.define(b, defaults[0], (int) defaults[1]));
                b.pop();
            });
            b.pop();

            b.pop();
        }
    }

    public enum HudAnchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public enum VfxQuality {
        LOW(0.3f), MEDIUM(0.65f), HIGH(1.0f);

        private final float particleMultiplier;

        VfxQuality(float particleMultiplier) {
            this.particleMultiplier = particleMultiplier;
        }

        public float particleMultiplier() {
            return particleMultiplier;
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue hudEnabled;
        public final ModConfigSpec.EnumValue<HudAnchor> hudAnchor;
        public final ModConfigSpec.IntValue hudOffsetX;
        public final ModConfigSpec.IntValue hudOffsetY;

        public final ModConfigSpec.EnumValue<VfxQuality> vfxQuality;
        public final ModConfigSpec.IntValue vfxMaxDistance;
        public final ModConfigSpec.IntValue maxParticlesPerEffect;

        Client(ModConfigSpec.Builder b) {
            b.push("hud");
            hudEnabled = b.comment("Show the energy bar and ability loadout.").define("enabled", true);
            hudAnchor = b.comment("Screen corner the HUD is anchored to.").defineEnum("anchor", HudAnchor.TOP_LEFT);
            hudOffsetX = b.comment("Horizontal distance from the anchor corner.").defineInRange("offsetX", 6, 0, 2_000);
            hudOffsetY = b.comment("Vertical distance from the anchor corner.").defineInRange("offsetY", 6, 0, 2_000);
            b.pop();

            b.push("vfx");
            vfxQuality = b.comment("Particle density preset. Vanilla's particle setting is applied on top of this.").defineEnum("quality", VfxQuality.HIGH);
            vfxMaxDistance = b.comment("Effects further than this many blocks from the camera are skipped.").defineInRange("maxDistance", 48, 8, 256);
            maxParticlesPerEffect = b.comment("Hard cap on particles spawned by a single effect.").defineInRange("maxParticlesPerEffect", 160, 8, 2_000);
            b.pop();
        }
    }
}

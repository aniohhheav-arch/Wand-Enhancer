package dev.infinitygauntlet.config;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.Abilities;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.Map;

/** Gameplay settings. SERVER config: lives in the world's serverconfig folder and is synced to clients. */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue AFFECT_PLAYERS;
    public static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue PORTAL_RADIUS;
    public static final ModConfigSpec.IntValue PORTAL_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue MAX_PORTAL_PAIRS;
    public static final ModConfigSpec.BooleanValue CROSS_DIMENSION_PORTALS;
    public static final ModConfigSpec.BooleanValue PORTALS_TRANSPORT_MOBS;
    public static final ModConfigSpec.BooleanValue PORTALS_TRANSPORT_PROJECTILES;
    public static final ModConfigSpec.BooleanValue PORTALS_TRANSPORT_ITEMS;

    public static final ModConfigSpec.IntValue BLINK_DISTANCE;
    public static final ModConfigSpec.IntValue RIFT_EXIT_HEIGHT;
    public static final ModConfigSpec.DoubleValue BARRAGE_BOLT_DAMAGE;
    public static final ModConfigSpec.DoubleValue COMPRESSION_CORE_DAMAGE;
    public static final ModConfigSpec.DoubleValue COMPRESSION_BURST_DAMAGE;
    public static final ModConfigSpec.IntValue PRISON_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue GATEWAY_HEIGHT;
    public static final ModConfigSpec.DoubleValue GATEWAY_RADIUS;
    public static final ModConfigSpec.IntValue GATEWAY_DURATION_SECONDS;
    public static final ModConfigSpec.DoubleValue GROUP_TELEPORT_RADIUS;
    public static final ModConfigSpec.IntValue GROUP_TELEPORT_RANGE;

    public static final ModConfigSpec.DoubleValue FREEZE_RADIUS;
    public static final ModConfigSpec.IntValue FREEZE_SECONDS;
    public static final ModConfigSpec.DoubleValue SLOW_FIELD_SCALE;
    public static final ModConfigSpec.IntValue SLOW_FIELD_SECONDS;
    public static final ModConfigSpec.DoubleValue BUBBLE_SCALE;
    public static final ModConfigSpec.DoubleValue BUBBLE_RADIUS;
    public static final ModConfigSpec.IntValue BUBBLE_SECONDS;
    public static final ModConfigSpec.IntValue ACCELERATION_SECONDS;
    public static final ModConfigSpec.DoubleValue ACCELERATION_SPEED_BONUS;
    public static final ModConfigSpec.IntValue REWIND_HISTORY_TICKS;
    public static final ModConfigSpec.DoubleValue REWIND_RADIUS;
    public static final ModConfigSpec.IntValue ECHO_RECORD_TICKS;
    public static final ModConfigSpec.IntValue LOOP_SECONDS;
    public static final ModConfigSpec.IntValue LOOP_PERIOD_TICKS;
    public static final ModConfigSpec.IntValue BARRIER_SECONDS;
    public static final ModConfigSpec.IntValue FRACTURE_SECONDS;

    public static final ModConfigSpec.IntValue POSSESSION_RANGE;
    public static final ModConfigSpec.IntValue POSSESSION_SECONDS;
    public static final ModConfigSpec.IntValue POSSESSION_MAX_FEEDS;
    public static final ModConfigSpec.BooleanValue POSSESSION_CREEPER_DETONATION;
    public static final ModConfigSpec.IntValue NEURAL_OVERRIDE_SECONDS;

    private static final Map<String, ModConfigSpec.IntValue> COOLDOWNS = new HashMap<>();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        AFFECT_PLAYERS = b.comment("Whether offensive abilities (prison, rift, compression, barrage) can target other players.")
                .define("offensiveAbilitiesAffectPlayers", false);
        COOLDOWN_MULTIPLIER = b.comment("Multiplier applied to every ability cooldown.")
                .defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);
        b.pop();

        b.push("space_stone");
        PORTAL_RADIUS = b.comment("Radius in blocks of a Dimensional Portal rupture.").defineInRange("portalRadius", 2.0, 0.75, 6.0);
        PORTAL_DURATION_SECONDS = b.comment("Seconds a portal pair stays open. 0 keeps it open until closed manually (sneak + cast).")
                .defineInRange("portalDurationSeconds", 300, 0, 86400);
        MAX_PORTAL_PAIRS = b.comment("Maximum open portal pairs per player; the oldest pair collapses when exceeded.")
                .defineInRange("maxPortalPairs", 3, 1, 32);
        CROSS_DIMENSION_PORTALS = b.comment("Allow linking portals placed in different dimensions.").define("crossDimensionPortals", true);
        PORTALS_TRANSPORT_MOBS = b.define("portalsTransportMobs", true);
        PORTALS_TRANSPORT_PROJECTILES = b.define("portalsTransportProjectiles", true);
        PORTALS_TRANSPORT_ITEMS = b.define("portalsTransportItems", true);
        BLINK_DISTANCE = b.comment("Maximum Blink distance in blocks.").defineInRange("blinkDistance", 12, 2, 64);
        RIFT_EXIT_HEIGHT = b.comment("How far above a Spatial Rift swallowed entities reappear.").defineInRange("riftExitHeight", 14, 2, 64);
        BARRAGE_BOLT_DAMAGE = b.defineInRange("barrageBoltDamage", 4.0, 0.0, 100.0);
        COMPRESSION_CORE_DAMAGE = b.comment("Damage per half-second to entities crushed at the Spatial Compression core.")
                .defineInRange("compressionCoreDamage", 2.0, 0.0, 100.0);
        COMPRESSION_BURST_DAMAGE = b.defineInRange("compressionBurstDamage", 6.0, 0.0, 100.0);
        PRISON_DURATION_SECONDS = b.defineInRange("prisonDurationSeconds", 8, 1, 120);
        GATEWAY_HEIGHT = b.comment("Height of the Cosmic Gateway above its target.").defineInRange("gatewayHeight", 22, 6, 96);
        GATEWAY_RADIUS = b.defineInRange("gatewayRadius", 6.0, 2.0, 16.0);
        GATEWAY_DURATION_SECONDS = b.defineInRange("gatewayDurationSeconds", 45, 5, 600);
        GROUP_TELEPORT_RADIUS = b.comment("Radius around the caster that Universal Teleportation gathers allies from.")
                .defineInRange("groupTeleportRadius", 6.0, 1.0, 32.0);
        GROUP_TELEPORT_RANGE = b.comment("Maximum range of an aimed Universal Teleportation without a waypoint.")
                .defineInRange("groupTeleportRange", 160, 16, 1024);
        b.pop();

        b.push("time_stone");
        FREEZE_RADIUS = b.comment("Radius of Time Freeze around the caster.").defineInRange("freezeRadius", 12.0, 2.0, 48.0);
        FREEZE_SECONDS = b.defineInRange("freezeSeconds", 8, 1, 120);
        SLOW_FIELD_SCALE = b.comment("Time scale inside a Time Slow field (1 = normal speed, 0 = frozen).")
                .defineInRange("slowFieldScale", 0.25, 0.0, 1.0);
        SLOW_FIELD_SECONDS = b.defineInRange("slowFieldSeconds", 15, 1, 300);
        BUBBLE_SCALE = b.defineInRange("bubbleScale", 0.12, 0.0, 1.0);
        BUBBLE_RADIUS = b.defineInRange("bubbleRadius", 8.0, 2.0, 32.0);
        BUBBLE_SECONDS = b.defineInRange("bubbleSeconds", 12, 1, 300);
        ACCELERATION_SECONDS = b.defineInRange("accelerationSeconds", 12, 1, 300);
        ACCELERATION_SPEED_BONUS = b.comment("Movement speed bonus of Time Acceleration (1.0 = +100%).")
                .defineInRange("accelerationSpeedBonus", 0.8, 0.0, 4.0);
        REWIND_HISTORY_TICKS = b.comment("How many ticks of movement history Rewind can play back.").defineInRange("rewindHistoryTicks", 100, 20, 600);
        REWIND_RADIUS = b.comment("Radius of an untargeted Rewind.").defineInRange("rewindRadius", 10.0, 1.0, 48.0);
        ECHO_RECORD_TICKS = b.comment("Length of the movement a Temporal Echo replays.").defineInRange("echoRecordTicks", 80, 20, 600);
        LOOP_SECONDS = b.defineInRange("loopSeconds", 12, 2, 300);
        LOOP_PERIOD_TICKS = b.comment("How often a Temporal Loop snaps captured entities back.").defineInRange("loopPeriodTicks", 50, 10, 400);
        BARRIER_SECONDS = b.defineInRange("barrierSeconds", 15, 2, 300);
        FRACTURE_SECONDS = b.defineInRange("fractureSeconds", 12, 3, 120);
        b.pop();

        b.push("consciousness_nexus");
        POSSESSION_RANGE = b.comment("Maximum distance between a possessed creature and its Nexus; stability falls toward it and the link breaks beyond it.")
                .defineInRange("possessionRange", 64, 8, 160);
        POSSESSION_SECONDS = b.comment("Maximum duration of one possession link.").defineInRange("possessionSeconds", 300, 10, 7200);
        POSSESSION_MAX_FEEDS = b.comment("How many creatures one Nexus can hold at once (multi-feed).").defineInRange("maxFeeds", 3, 1, 3);
        POSSESSION_CREEPER_DETONATION = b.comment("Whether a possessed creeper can be detonated (explosions follow the mobGriefing rule).")
                .define("creeperDetonation", true);
        NEURAL_OVERRIDE_SECONDS = b.defineInRange("neuralOverrideSeconds", 10, 1, 120);
        b.pop();

        b.comment("Ability cooldowns in ticks (20 ticks = 1 second).").push("cooldowns");
        for (Ability ability : Abilities.all()) {
            COOLDOWNS.put(ability.id().getPath(), b.defineInRange(ability.id().getPath(), ability.defaultCooldown(), 0, 72000));
        }
        b.pop();

        SPEC = b.build();
    }

    public static int cooldown(Ability ability) {
        ModConfigSpec.IntValue value = COOLDOWNS.get(ability.id().getPath());
        int base = value == null || !SPEC.isLoaded() ? ability.defaultCooldown() : value.get();
        return (int) Math.round(base * (SPEC.isLoaded() ? COOLDOWN_MULTIPLIER.get() : 1.0));
    }

    private ServerConfig() {
    }
}

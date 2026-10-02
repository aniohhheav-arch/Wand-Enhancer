package com.infinitemultiverse.power;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.ability.AreaAbility;
import com.infinitemultiverse.power.ability.BeamAbility;
import com.infinitemultiverse.power.ability.BuffToggleAbility;
import com.infinitemultiverse.power.ability.ProjectileWardAbility;
import com.infinitemultiverse.power.cursed.BlackFlashAbility;
import com.infinitemultiverse.power.cursed.BlueAbility;
import com.infinitemultiverse.power.cursed.CursedSpirits;
import com.infinitemultiverse.power.cursed.DomainAbility;
import com.infinitemultiverse.power.cursed.DomainType;
import com.infinitemultiverse.power.cursed.ShadowStorageAbility;
import com.infinitemultiverse.power.cursed.SummonShadowsAbility;
import com.infinitemultiverse.power.ability.TelekineticGripAbility;
import com.infinitemultiverse.power.gear.ModGear;
import com.infinitemultiverse.power.mutant.MutantAbilities;
import com.infinitemultiverse.power.mystic.MysticArts;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every Phase 2 power set and its abilities. */
public final class PowerSets {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MultiverseRegistries.ABILITY_KEY, InfiniteMultiverse.MOD_ID);
    public static final DeferredRegister<PowerSet> POWER_SETS = DeferredRegister.create(MultiverseRegistries.POWER_SET_KEY, InfiniteMultiverse.MOD_ID);

    private static final MultiverseSystem CURSED = MultiverseSystem.CURSED_TECHNIQUES;
    private static final String SHOUT = "message.infinitemultiverse.shout.";

    private static DeferredHolder<Ability, Ability> ability(String id, Supplier<? extends Ability> factory) {
        return ABILITIES.register(id, factory);
    }

    // ---- shared cursed energy basics ----
    public static final DeferredHolder<Ability, Ability> CURSED_REINFORCEMENT = ability("cursed_reinforcement",
            () -> BuffToggleAbility.builder(CURSED, 3f).buff(MobEffects.DAMAGE_BOOST, 0).buff(MobEffects.DAMAGE_RESISTANCE, 0).aura(0x4A6BFF).build());
    public static final DeferredHolder<Ability, Ability> BLACK_FLASH = ability("black_flash", BlackFlashAbility::new);
    public static final DeferredHolder<Ability, Ability> REVERSED_CURSED_TECHNIQUE = ability("reversed_cursed_technique",
            () -> BuffToggleAbility.builder(CURSED, 10f).aura(0xE8F4FF)
                    .perSecond((ctx, seconds) -> {
                        ctx.player().heal(2f);
                        ctx.player().removeEffect(MobEffects.POISON);
                        ctx.player().removeEffect(MobEffects.WITHER);
                    }).build());

    // ---- Limitless ----
    public static final DeferredHolder<Ability, Ability> INFINITY = ability("infinity",
            () -> new ProjectileWardAbility(CURSED, 6f, ProjectileWardAbility.Mode.STOP, 3.0, 1.0f, false, 2.5f, 0xBFD4FF, null, null));
    public static final DeferredHolder<Ability, Ability> BLUE = ability("blue", BlueAbility::new);
    public static final DeferredHolder<Ability, Ability> RED = ability("red",
            () -> BeamAbility.builder(CURSED).length(20).radius(1.4).hits(8).damage(9).knockback(3.5).color(0xFF2A3A).endBurst()
                    .shout(SHOUT + "red", ChatFormatting.RED).sound(ModSounds.SHOCKWAVE, 1.3f).build());
    public static final DeferredHolder<Ability, Ability> HOLLOW_PURPLE = ability("hollow_purple",
            () -> BeamAbility.builder(CURSED).length(48).radius(2.5).hits(20).damage(30).magic().heavy().charge(20).color(0xA040FF).endBurst()
                    .shout(SHOUT + "hollow_purple", ChatFormatting.DARK_PURPLE).sound(ModSounds.SHOCKWAVE, 0.6f).build());
    public static final DeferredHolder<Ability, Ability> DOMAIN_UNLIMITED_VOID = ability("domain_unlimited_void", () -> new DomainAbility(DomainType.UNLIMITED_VOID));

    // ---- Shrine ----
    public static final DeferredHolder<Ability, Ability> DISMANTLE = ability("dismantle",
            () -> BeamAbility.builder(CURSED).length(24).radius(1.0).hits(3).damage(10).magic().color(0xFF3040).hitVfx(VfxIds.SLASH)
                    .sound(ModSounds.STAND_SWING, 0.6f).build());
    public static final DeferredHolder<Ability, Ability> CLEAVE = ability("cleave",
            () -> BeamAbility.builder(CURSED).length(6).radius(1.0).hits(1).damage(6).maxHealthFraction(0.2).magic().color(0xB0101A).hitVfx(VfxIds.SLASH)
                    .sound(ModSounds.STAND_HEAVY, 0.8f).build());
    public static final DeferredHolder<Ability, Ability> FUGA = ability("fuga",
            () -> BeamAbility.builder(CURSED).length(32).radius(1.2).hits(5).damage(14).fire(10).color(0xFF7A1A).endBurst().charge(10)
                    .shout(SHOUT + "fuga", ChatFormatting.GOLD).sound(ModSounds.SHOCKWAVE, 1.1f).build());
    public static final DeferredHolder<Ability, Ability> DOMAIN_MALEVOLENT_SHRINE = ability("domain_malevolent_shrine", () -> new DomainAbility(DomainType.MALEVOLENT_SHRINE));

    // ---- Ten Shadows ----
    public static final DeferredHolder<Ability, Ability> DIVINE_DOGS = ability("divine_dogs", SummonShadowsAbility::new);
    public static final DeferredHolder<Ability, Ability> NUE = ability("nue",
            () -> BeamAbility.builder(CURSED).length(28).radius(1.2).hits(1).damage(9).lightning().color(0xFFF07A)
                    .shout(SHOUT + "nue", ChatFormatting.YELLOW).sound(ModSounds.STAND_SUMMON, 1.4f).build());
    public static final DeferredHolder<Ability, Ability> SHADOW_STORAGE = ability("shadow_storage", ShadowStorageAbility::new);
    public static final DeferredHolder<Ability, Ability> DOMAIN_CHIMERA_SHADOW_GARDEN = ability("domain_chimera_shadow_garden", () -> new DomainAbility(DomainType.CHIMERA_SHADOW_GARDEN));

    // ---- Cursed Spirit Manipulation ----
    public static final DeferredHolder<Ability, Ability> ABSORB_SPIRIT = ability("absorb_spirit", CursedSpirits.Absorb::new);
    public static final DeferredHolder<Ability, Ability> RELEASE_SPIRIT = ability("release_spirit", CursedSpirits.Release::new);
    public static final DeferredHolder<Ability, Ability> MAXIMUM_UZUMAKI = ability("maximum_uzumaki", CursedSpirits.Uzumaki::new);

    // ---- Blood Manipulation ----
    public static final DeferredHolder<Ability, Ability> PIERCING_BLOOD = ability("piercing_blood",
            () -> BeamAbility.builder(CURSED).length(32).radius(0.6).hits(3).damage(12).selfDamage(2).color(0xB00018)
                    .shout(SHOUT + "piercing_blood", ChatFormatting.DARK_RED).sound(ModSounds.STAND_NAIL, 0.7f).build());
    public static final DeferredHolder<Ability, Ability> SUPERNOVA = ability("supernova",
            () -> AreaAbility.builder(CURSED).radius(7).damage(7).color(0xB00018).vfx(VfxIds.DOMAIN_OPEN).selfDamage(2)
                    .shout(SHOUT + "supernova", ChatFormatting.DARK_RED).sound(ModSounds.SHOCKWAVE, 1.4f).build());
    public static final DeferredHolder<Ability, Ability> FLOWING_RED_SCALE = ability("flowing_red_scale",
            () -> BuffToggleAbility.builder(CURSED, 5f).buff(MobEffects.DAMAGE_BOOST, 1).buff(MobEffects.MOVEMENT_SPEED, 0).buff(MobEffects.JUMP, 0).aura(0xB00018).build());

    // ---- Cursed Speech ----
    public static final DeferredHolder<Ability, Ability> DONT_MOVE = ability("dont_move",
            () -> AreaAbility.builder(CURSED).radius(16).color(0xE6E6FF).calm().selfDamage(1)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6))
                    .effect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 60, 2))
                    .shout(SHOUT + "dont_move", ChatFormatting.WHITE).sound(ModSounds.STAND_EPITAPH, 0.6f).build());
    public static final DeferredHolder<Ability, Ability> BLAST_AWAY = ability("blast_away",
            () -> AreaAbility.builder(CURSED).radius(12).cone(0.4).damage(4).knockback(3.0).color(0xE6E6FF).selfDamage(1)
                    .shout(SHOUT + "blast_away", ChatFormatting.WHITE).sound(ModSounds.SHOCKWAVE, 1.2f).build());
    public static final DeferredHolder<Ability, Ability> TWIST = ability("twist",
            () -> BeamAbility.builder(CURSED).length(16).radius(1.0).hits(1).damage(12).magic().selfDamage(2).color(0xE6E6FF).hitVfx(VfxIds.SLASH)
                    .shout(SHOUT + "twist", ChatFormatting.WHITE).sound(ModSounds.STAND_HEAVY, 0.6f).build());

    // ================= Superheroes: mutant powers =================
    private static final MultiverseSystem HEROES = MultiverseSystem.SUPERHEROES;
    private static final String NEEDS_SUIT = "message.infinitemultiverse.needs_suit";
    private static final Predicate<ServerPlayer> MARK = p -> ModGear.wearingSuit(p, ModGear.MARK_ARMOR);
    private static final Predicate<ServerPlayer> VIGILANTE = p -> ModGear.wearingSuit(p, ModGear.VIGILANTE_SUIT);
    private static final Predicate<ServerPlayer> AMAZON = p -> ModGear.wearingSuit(p, ModGear.AMAZONIAN_ARMOR);

    public static final DeferredHolder<Ability, Ability> TELEKINETIC_GRIP = ability("telekinetic_grip", () -> new TelekineticGripAbility(HEROES, 4f, 0xD070FF));
    public static final DeferredHolder<Ability, Ability> TELEKINETIC_PUSH = ability("telekinetic_push",
            () -> AreaAbility.builder(HEROES).radius(10).cone(0.5).damage(4).knockback(3.2).color(0xD070FF).sound(ModSounds.SHOCKWAVE, 1.3f).build());
    public static final DeferredHolder<Ability, Ability> KINETIC_BARRIER = ability("kinetic_barrier",
            () -> new ProjectileWardAbility(HEROES, 5f, ProjectileWardAbility.Mode.STOP, 3.0, 0.5f, false, 2f, 0xD070FF, null, null));
    public static final DeferredHolder<Ability, Ability> MIND_SCAN = ability("mind_scan", MutantAbilities.MindScan::new);
    public static final DeferredHolder<Ability, Ability> PACIFY = ability("pacify",
            () -> AreaAbility.builder(HEROES).radius(14).calm().color(0xFF9AE6).vfx(VfxIds.MANDALA)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3))
                    .effect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 100, 1)).sound(ModSounds.STAND_EPITAPH, 1.3f).build());
    public static final DeferredHolder<Ability, Ability> PSYCHIC_BLAST = ability("psychic_blast",
            () -> BeamAbility.builder(HEROES).length(24).radius(1.0).hits(4).damage(9).magic().color(0xFF5AD0)
                    .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 100, 0)).sound(ModSounds.SHOCKWAVE, 1.6f).build());
    public static final DeferredHolder<Ability, Ability> CHAIN_LIGHTNING = ability("chain_lightning", MutantAbilities.ChainLightning::new);
    public static final DeferredHolder<Ability, Ability> THUNDER_STRIKE = ability("thunder_strike",
            () -> BeamAbility.builder(HEROES).length(36).radius(1.5).hits(6).damage(12).lightning().heavy().endBurst().color(0x9FD8FF)
                    .sound(ModSounds.SHOCKWAVE, 0.8f).build());
    public static final DeferredHolder<Ability, Ability> STATIC_FIELD = ability("static_field",
            () -> BuffToggleAbility.builder(HEROES, 5f).buff(MobEffects.MOVEMENT_SPEED, 0).aura(0x9FD8FF).perSecond(MutantAbilities::staticPulse).build());
    public static final DeferredHolder<Ability, Ability> ICE_SHARD = ability("ice_shard", MutantAbilities.IceShard::new);
    public static final DeferredHolder<Ability, Ability> FLASH_FREEZE = ability("flash_freeze",
            () -> AreaAbility.builder(HEROES).radius(8).damage(4).freeze(200).color(0xA8E8FF).vfx(VfxIds.FROST)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3)).sound(ModSounds.STAND_TIME_ERASE, 1.6f).build());
    public static final DeferredHolder<Ability, Ability> ICE_PATH = ability("ice_path", MutantAbilities.IcePath::new);
    public static final DeferredHolder<Ability, Ability> FIRE_BLAST = ability("fire_blast",
            () -> BeamAbility.builder(HEROES).length(22).radius(1.0).hits(3).damage(7).fire(6).endBurst().color(0xFF7A1A).sound(ModSounds.SHOCKWAVE, 1.4f).build());
    public static final DeferredHolder<Ability, Ability> FLAME_WAVE = ability("flame_wave",
            () -> AreaAbility.builder(HEROES).radius(9).cone(0.35).damage(6).fire(8).knockback(1.0).color(0xFF5A1A).sound(ModSounds.SHOCKWAVE, 1.0f).build());
    public static final DeferredHolder<Ability, Ability> HEAT_AURA = ability("heat_aura",
            () -> BuffToggleAbility.builder(HEROES, 5f).buff(MobEffects.FIRE_RESISTANCE, 0).aura(0xFF7A1A).perSecond(MutantAbilities::heatPulse).build());
    public static final DeferredHolder<Ability, Ability> MAGNETIC_PULL = ability("magnetic_pull", MutantAbilities.MagneticPull::new);
    public static final DeferredHolder<Ability, Ability> MAGNETIC_REPULSE = ability("magnetic_repulse",
            () -> AreaAbility.builder(HEROES).radius(10).damage(5).knockback(3.5).color(0xC02040).vfx(VfxIds.DOMAIN_OPEN).sound(ModSounds.SHOCKWAVE, 0.9f).build());
    public static final DeferredHolder<Ability, Ability> MAGNETIC_SHIELD = ability("magnetic_shield",
            () -> new ProjectileWardAbility(HEROES, 5f, ProjectileWardAbility.Mode.REFLECT, 3.5, 0.7f, false, 1.5f, 0xC02040, null, null));
    public static final DeferredHolder<Ability, Ability> KRYPTONIAN_FLIGHT = ability("kryptonian_flight",
            () -> BuffToggleAbility.builder(HEROES, 2f).flight().aura(0x3A6BFF).build());
    public static final DeferredHolder<Ability, Ability> HEAT_VISION = ability("heat_vision",
            () -> BeamAbility.builder(HEROES).length(40).radius(0.6).hits(4).damage(10).fire(5).color(0xFF2020).sound(ModSounds.STAND_NAIL, 1.6f).build());
    public static final DeferredHolder<Ability, Ability> SUPER_STRENGTH = ability("super_strength",
            () -> BuffToggleAbility.builder(HEROES, 4f).buff(MobEffects.DAMAGE_BOOST, 2).buff(MobEffects.DAMAGE_RESISTANCE, 1).buff(MobEffects.DIG_SPEED, 2).aura(0x3A6BFF).build());
    public static final DeferredHolder<Ability, Ability> SUPER_SPEED = ability("super_speed",
            () -> BuffToggleAbility.builder(HEROES, 3f).buff(MobEffects.MOVEMENT_SPEED, 4).buff(MobEffects.JUMP, 1).aura(0xFFD84A).build());
    public static final DeferredHolder<Ability, Ability> SPEED_FORCE_DASH = ability("speed_force_dash", MutantAbilities.SpeedForceDash::new);
    public static final DeferredHolder<Ability, Ability> LIGHTNING_THROW = ability("lightning_throw",
            () -> BeamAbility.builder(HEROES).length(30).radius(1.0).hits(3).damage(10).lightning().color(0xFFD84A).sound(ModSounds.SHOCKWAVE, 1.5f).build());

    // ================= Superheroes: suits (gear-gated, unlocked for everyone) =================
    public static final DeferredHolder<Ability, Ability> REPULSOR_BLAST = ability("repulsor_blast",
            () -> BeamAbility.builder(HEROES).length(26).radius(0.8).hits(2).damage(7).knockback(1.5).color(0xA8F0FF)
                    .requires(MARK, NEEDS_SUIT).sound(ModSounds.AEGIS_IMPACT, 1.6f).build());
    public static final DeferredHolder<Ability, Ability> UNIBEAM = ability("unibeam",
            () -> BeamAbility.builder(HEROES).length(44).radius(2.0).hits(12).damage(20).heavy().charge(15).endBurst().color(0xC8F8FF)
                    .requires(MARK, NEEDS_SUIT).sound(ModSounds.SHOCKWAVE, 0.7f).build());
    public static final DeferredHolder<Ability, Ability> THRUSTERS = ability("thrusters",
            () -> BuffToggleAbility.builder(HEROES, 3f).flight().aura(0xFFB040).requires(MARK, NEEDS_SUIT).build());
    public static final DeferredHolder<Ability, Ability> GRAPPLE = ability("grapple", () -> new MutantAbilities.Grapple(VIGILANTE));
    public static final DeferredHolder<Ability, Ability> SMOKE_BOMB = ability("smoke_bomb",
            () -> AreaAbility.builder(HEROES).radius(6).calm().color(0x505050).vfx(VfxIds.BURST)
                    .effect(() -> new MobEffectInstance(MobEffects.BLINDNESS, 100, 0))
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1))
                    .selfEffect(() -> new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0))
                    .requires(VIGILANTE, NEEDS_SUIT).sound(ModSounds.STAND_BOMB_MARK, 0.6f).build());
    public static final DeferredHolder<Ability, Ability> GLIDE = ability("glide",
            () -> BuffToggleAbility.builder(HEROES, 1f).buff(MobEffects.SLOW_FALLING, 0).buff(MobEffects.MOVEMENT_SPEED, 1).requires(VIGILANTE, NEEDS_SUIT).build());
    public static final DeferredHolder<Ability, Ability> LASSO = ability("lasso",
            () -> BeamAbility.builder(HEROES).length(18).radius(0.8).hits(1).damage(2).pull(1.6).color(0xFFD84A)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4))
                    .effect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 80, 2))
                    .requires(AMAZON, NEEDS_SUIT).sound(ModSounds.STAND_NAIL, 1.2f).build());
    public static final DeferredHolder<Ability, Ability> BRACER_DEFLECT = ability("bracer_deflect",
            () -> new ProjectileWardAbility(HEROES, 4f, ProjectileWardAbility.Mode.REFLECT, 2.5, 0.4f, true, 0.5f, 0xFFE07A, AMAZON, NEEDS_SUIT));
    public static final DeferredHolder<Ability, Ability> DIVINE_LEAP = ability("divine_leap", () -> new MutantAbilities.Leap(2.4, AMAZON));

    // ================= Mystic Arts =================
    private static final MultiverseSystem MYSTIC = MultiverseSystem.MYSTIC_ARTS;
    public static final DeferredHolder<Ability, Ability> ELDRITCH_WHIP = ability("eldritch_whip",
            () -> BeamAbility.builder(MYSTIC).length(14).radius(1.0).hits(3).damage(8).magic().pull(0.8).color(0xFF9A2A).hitVfx(VfxIds.SLASH)
                    .sound(ModSounds.STAND_SWING, 1.3f).build());
    public static final DeferredHolder<Ability, Ability> SERAPHIM_SHIELD = ability("seraphim_shield",
            () -> new ProjectileWardAbility(MYSTIC, 4f, ProjectileWardAbility.Mode.STOP, 2.5, 0.75f, true, 1.0f, 0xFFB347, null, null));
    public static final DeferredHolder<Ability, Ability> SLING_RING_PORTAL = ability("sling_ring_portal", MysticArts.SlingRingPortal::new);
    public static final DeferredHolder<Ability, Ability> ASTRAL_PROJECTION = ability("astral_projection", MysticArts.AstralProjection::new);
    public static final DeferredHolder<Ability, Ability> TIME_REVERSAL = ability("time_reversal", MysticArts.TimeReversal::new);
    public static final DeferredHolder<Ability, Ability> MIRROR_DIMENSION = ability("mirror_dimension", MysticArts.MirrorDimension::new);
    public static final DeferredHolder<Ability, Ability> CLOAK_LEVITATION = ability("cloak_levitation",
            () -> BuffToggleAbility.builder(MYSTIC, 1f).flight().aura(0xC0182A).requires(ModGear::wearingCloak, "message.infinitemultiverse.needs_cloak").build());
    public static final DeferredHolder<Ability, Ability> MYSTIC_GRIP = ability("mystic_grip", () -> new TelekineticGripAbility(MYSTIC, 3f, 0xFF9A2A));

    private static DeferredHolder<PowerSet, PowerSet> set(String id, MultiverseSystem system, int color, List<Supplier<? extends Ability>> abilities) {
        return POWER_SETS.register(id, () -> new PowerSet(system, color, abilities));
    }

    public static final DeferredHolder<PowerSet, PowerSet> LIMITLESS = set("limitless", CURSED, 0x7FB0FF,
            List.of(INFINITY, BLUE, RED, HOLLOW_PURPLE, DOMAIN_UNLIMITED_VOID, BLACK_FLASH, CURSED_REINFORCEMENT, REVERSED_CURSED_TECHNIQUE));
    public static final DeferredHolder<PowerSet, PowerSet> SHRINE = set("shrine", CURSED, 0xD0102A,
            List.of(DISMANTLE, CLEAVE, FUGA, DOMAIN_MALEVOLENT_SHRINE, BLACK_FLASH, CURSED_REINFORCEMENT, REVERSED_CURSED_TECHNIQUE));
    public static final DeferredHolder<PowerSet, PowerSet> TEN_SHADOWS = set("ten_shadows", CURSED, 0x3A3A5A,
            List.of(DIVINE_DOGS, NUE, SHADOW_STORAGE, DOMAIN_CHIMERA_SHADOW_GARDEN, BLACK_FLASH, CURSED_REINFORCEMENT, REVERSED_CURSED_TECHNIQUE));
    public static final DeferredHolder<PowerSet, PowerSet> CURSED_SPIRIT_MANIPULATION = set("cursed_spirit_manipulation", CURSED, 0x6A3FA0,
            List.of(ABSORB_SPIRIT, RELEASE_SPIRIT, MAXIMUM_UZUMAKI, BLACK_FLASH, CURSED_REINFORCEMENT, REVERSED_CURSED_TECHNIQUE));
    public static final DeferredHolder<PowerSet, PowerSet> BLOOD_MANIPULATION = set("blood_manipulation", CURSED, 0xB00018,
            List.of(PIERCING_BLOOD, SUPERNOVA, FLOWING_RED_SCALE, BLACK_FLASH, CURSED_REINFORCEMENT, REVERSED_CURSED_TECHNIQUE));
    public static final DeferredHolder<PowerSet, PowerSet> CURSED_SPEECH = set("cursed_speech", CURSED, 0xE6E6FF,
            List.of(DONT_MOVE, BLAST_AWAY, TWIST, BLACK_FLASH, CURSED_REINFORCEMENT, REVERSED_CURSED_TECHNIQUE));

    public static final DeferredHolder<PowerSet, PowerSet> TELEKINESIS = set("telekinesis", HEROES, 0xD070FF, List.of(TELEKINETIC_GRIP, TELEKINETIC_PUSH, KINETIC_BARRIER));
    public static final DeferredHolder<PowerSet, PowerSet> TELEPATHY = set("telepathy", HEROES, 0xFF5AD0, List.of(MIND_SCAN, PACIFY, PSYCHIC_BLAST));
    public static final DeferredHolder<PowerSet, PowerSet> ELECTROKINESIS = set("electrokinesis", HEROES, 0x9FD8FF, List.of(CHAIN_LIGHTNING, THUNDER_STRIKE, STATIC_FIELD));
    public static final DeferredHolder<PowerSet, PowerSet> CRYOKINESIS = set("cryokinesis", HEROES, 0xA8E8FF, List.of(ICE_SHARD, FLASH_FREEZE, ICE_PATH));
    public static final DeferredHolder<PowerSet, PowerSet> PYROKINESIS = set("pyrokinesis", HEROES, 0xFF7A1A, List.of(FIRE_BLAST, FLAME_WAVE, HEAT_AURA));
    public static final DeferredHolder<PowerSet, PowerSet> MAGNETISM = set("magnetism", HEROES, 0xC02040, List.of(MAGNETIC_PULL, MAGNETIC_REPULSE, MAGNETIC_SHIELD));
    public static final DeferredHolder<PowerSet, PowerSet> KRYPTONIAN = set("kryptonian", HEROES, 0x3A6BFF, List.of(KRYPTONIAN_FLIGHT, HEAT_VISION, SUPER_STRENGTH));
    public static final DeferredHolder<PowerSet, PowerSet> SPEEDSTER = set("speedster", HEROES, 0xFFD84A, List.of(SUPER_SPEED, SPEED_FORCE_DASH, LIGHTNING_THROW));
    public static final DeferredHolder<PowerSet, PowerSet> MYSTIC_ARTS = set("mystic_arts", MYSTIC, 0xFFB347,
            List.of(ELDRITCH_WHIP, SERAPHIM_SHIELD, SLING_RING_PORTAL, MYSTIC_GRIP, ASTRAL_PROJECTION, TIME_REVERSAL, MIRROR_DIMENSION, CLOAK_LEVITATION));

    private PowerSets() {
    }
}

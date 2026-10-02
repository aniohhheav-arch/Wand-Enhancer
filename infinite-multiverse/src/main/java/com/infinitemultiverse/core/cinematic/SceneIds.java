package com.infinitemultiverse.core.cinematic;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.resources.ResourceLocation;

/** Ids of every rendered scene. Shared by server (which sends them) and client (which renders them). */
public final class SceneIds {
    private SceneIds() {
    }

    private static ResourceLocation id(String path) {
        return InfiniteMultiverse.id(path);
    }

    // ---- generic beams (origin = start, dir = start→end, param = width) ----
    public static final ResourceLocation BEAM = id("beam");
    public static final ResourceLocation BEAM_HELIX = id("beam_helix");
    public static final ResourceLocation BEAM_LIGHTNING = id("beam_lightning");
    public static final ResourceLocation BEAM_TWIN = id("beam_twin");
    public static final ResourceLocation BEAM_WHIP = id("beam_whip");
    public static final ResourceLocation BEAM_ROPE = id("beam_rope");
    public static final ResourceLocation BEAM_SONIC = id("beam_sonic");
    public static final ResourceLocation BEAM_FIRE = id("beam_fire");
    public static final ResourceLocation BEAM_PSYCHIC = id("beam_psychic");

    // ---- generic area bursts (origin = centre, dir = look, param = radius) ----
    public static final ResourceLocation WAVE = id("wave");
    public static final ResourceLocation WAVE_CONE = id("wave_cone");
    public static final ResourceLocation WAVE_FIRE = id("wave_fire");
    public static final ResourceLocation WAVE_FROST = id("wave_frost");
    public static final ResourceLocation WAVE_MAGNET = id("wave_magnet");
    public static final ResourceLocation WAVE_SMOKE = id("wave_smoke");
    public static final ResourceLocation WAVE_MIND = id("wave_mind");
    public static final ResourceLocation WAVE_VOICE = id("wave_voice");
    public static final ResourceLocation WAVE_BLOOD = id("wave_blood");
    public static final ResourceLocation WAVE_LANDING = id("wave_landing");

    // ---- auras (follow the caster, re-sent while active; param = intensity) ----
    public static final ResourceLocation AURA = id("aura");
    public static final ResourceLocation AURA_ELECTRIC = id("aura_electric");
    public static final ResourceLocation AURA_FIRE = id("aura_fire");
    public static final ResourceLocation AURA_HEAL = id("aura_heal");
    public static final ResourceLocation AURA_BLOOD = id("aura_blood");
    public static final ResourceLocation AURA_SPEED = id("aura_speed");
    public static final ResourceLocation AURA_FLIGHT = id("aura_flight");
    public static final ResourceLocation AURA_THRUSTERS = id("aura_thrusters");
    public static final ResourceLocation AURA_CLOAK = id("aura_cloak");
    public static final ResourceLocation AURA_GLIDE = id("aura_glide");
    public static final ResourceLocation AURA_STRENGTH = id("aura_strength");

    // ---- wards (follow the caster facing their look; param = radius) ----
    public static final ResourceLocation WARD_INFINITY = id("ward_infinity");
    public static final ResourceLocation WARD_KINETIC = id("ward_kinetic");
    public static final ResourceLocation WARD_MAGNET = id("ward_magnet");
    public static final ResourceLocation WARD_SERAPHIM = id("ward_seraphim");
    public static final ResourceLocation WARD_BRACER = id("ward_bracer");
    public static final ResourceLocation WARD_HIT = id("ward_hit");

    // ---- impacts ----
    public static final ResourceLocation IMPACT = id("impact");
    public static final ResourceLocation IMPACT_SLASH = id("impact_slash");
    /** Energy gathering at the caster's hand before a charged technique (follows the caster; param = size). */
    public static final ResourceLocation CHARGE = id("charge");

    // ---- cursed techniques ----
    public static final ResourceLocation BLACK_FLASH = id("black_flash");
    public static final ResourceLocation BLUE = id("blue");
    public static final ResourceLocation RED = id("red");
    public static final ResourceLocation HOLLOW_PURPLE = id("hollow_purple");
    public static final ResourceLocation DOMAIN_CUTSCENE = id("domain_cutscene");
    public static final ResourceLocation UNLIMITED_VOID = id("unlimited_void");
    public static final ResourceLocation MALEVOLENT_SHRINE = id("malevolent_shrine");
    public static final ResourceLocation CHIMERA_GARDEN = id("chimera_garden");
    public static final ResourceLocation DISMANTLE = id("dismantle");
    public static final ResourceLocation CLEAVE = id("cleave");
    public static final ResourceLocation WORLD_SLASH = id("world_slash");
    public static final ResourceLocation FUGA = id("fuga");
    public static final ResourceLocation SHADOW_SUMMON = id("shadow_summon");
    public static final ResourceLocation NUE = id("nue");
    public static final ResourceLocation SPIRIT_ABSORB = id("spirit_absorb");
    public static final ResourceLocation SPIRIT_RELEASE = id("spirit_release");
    public static final ResourceLocation UZUMAKI = id("uzumaki");
    public static final ResourceLocation SUPERNOVA = id("supernova");
    public static final ResourceLocation TWIST = id("twist");

    // ---- heroes ----
    public static final ResourceLocation CHAIN_LIGHTNING = id("chain_lightning");
    public static final ResourceLocation THUNDER = id("thunder");
    public static final ResourceLocation ICE_SPIKES = id("ice_spikes");
    public static final ResourceLocation ICE_PATH = id("ice_path");
    public static final ResourceLocation GRIP = id("grip");
    public static final ResourceLocation MIND_SCAN = id("mind_scan");
    public static final ResourceLocation DASH = id("dash");
    public static final ResourceLocation UNIBEAM = id("unibeam");
    public static final ResourceLocation GRAPPLE = id("grapple");
    public static final ResourceLocation ICE_SHARD = id("ice_shard");
    public static final ResourceLocation MAGNET_PULL = id("magnet_pull");

    // ---- mystic arts ----
    public static final ResourceLocation SLING_PORTAL = id("sling_portal");
    public static final ResourceLocation ASTRAL_EXIT = id("astral_exit");
    public static final ResourceLocation ASTRAL_RETURN = id("astral_return");
    public static final ResourceLocation ASTRAL_SPIRIT = id("astral_spirit");
    public static final ResourceLocation TIME_EYE = id("time_eye");
    public static final ResourceLocation MIRROR_ENTER = id("mirror_enter");
    public static final ResourceLocation MIRROR_WORLD = id("mirror_world");
    public static final ResourceLocation MIRROR_EXIT = id("mirror_exit");
    public static final ResourceLocation MYSTIC_BANDS = id("mystic_bands");
    public static final ResourceLocation SPELL_CIRCLE = id("spell_circle");

    // ---- Phase 3: Infinity Gauntlet ----
    public static final ResourceLocation STONE_SET = id("stone_set");
    public static final ResourceLocation STONE_CHARGE = id("stone_charge");
    public static final ResourceLocation SPACE_WARP = id("space_warp");
    public static final ResourceLocation TESSERACT = id("tesseract");
    public static final ResourceLocation MIND_THRALL = id("mind_thrall");
    public static final ResourceLocation PSIONIC_STORM = id("psionic_storm");
    public static final ResourceLocation REALITY_WARP = id("reality_warp");
    public static final ResourceLocation REALITY_SHATTER = id("reality_shatter");
    public static final ResourceLocation TIME_REWIND = id("time_rewind");
    public static final ResourceLocation TIME_FREEZE = id("time_freeze");
    public static final ResourceLocation SOUL_DRAIN = id("soul_drain");
    public static final ResourceLocation SOUL_HARVEST = id("soul_harvest");
    public static final ResourceLocation SNAP = id("snap");
    public static final ResourceLocation DUST = id("dust");

    // ---- Phase 3: portals, rifts, space, chronokinesis ----
    public static final ResourceLocation PORTAL_SHOT = id("portal_shot");
    public static final ResourceLocation PORTAL_TRANSIT = id("portal_transit");
    public static final ResourceLocation RIFT_OPEN = id("rift_open");
    public static final ResourceLocation RIFT_TRANSIT = id("rift_transit");
    public static final ResourceLocation LAUNCH = id("launch");
    public static final ResourceLocation REENTRY = id("reentry");
    public static final ResourceLocation TIME_FIELD = id("time_field");
    public static final ResourceLocation STASIS = id("stasis");
}

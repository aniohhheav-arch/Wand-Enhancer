package com.wandcraft.magic;

import java.util.Map;

/** Spell icon assignments: every spell gets a glyph that hints at what it does. */
final class Icons {
    private static final Map<String, Glyph> MAP = Map.ofEntries(
            Map.entry("arcane_bolt", Glyph.BOLT), Map.entry("arcane_barrage", Glyph.BEAM), Map.entry("arcane_burst", Glyph.BURST),
            Map.entry("arcane_beam", Glyph.BEAM), Map.entry("rune_mine", Glyph.RUNE), Map.entry("arcane_orb", Glyph.ORB),
            Map.entry("spell_echo", Glyph.RING), Map.entry("dispel", Glyph.CROSS), Map.entry("arcane_mark", Glyph.TARGET), Map.entry("mana_pulse", Glyph.WAVE),
            Map.entry("firebolt", Glyph.FLAME), Map.entry("flame_lance", Glyph.SWORD), Map.entry("firestorm", Glyph.TOWER), Map.entry("phoenix_wings", Glyph.WINGS),
            Map.entry("magma_grasp", Glyph.WALL), Map.entry("frost_shard", Glyph.SPIKE), Map.entry("glacial_prison", Glyph.SNOW), Map.entry("blizzard", Glyph.CLOUD),
            Map.entry("ice_bridge", Glyph.WAVE), Map.entry("lightning_bolt", Glyph.BOLT), Map.entry("chain_lightning", Glyph.CHAIN), Map.entry("thunderstep", Glyph.WINGS),
            Map.entry("stormcall", Glyph.CLOUD), Map.entry("stone_spike", Glyph.SPIKE), Map.entry("earth_wall", Glyph.WALL), Map.entry("seismic_wave", Glyph.CRACK),
            Map.entry("sand_vortex", Glyph.TORNADO),
            Map.entry("force_push", Glyph.HAND), Map.entry("force_wave", Glyph.WAVE), Map.entry("force_pull", Glyph.SWIRL), Map.entry("levitation", Glyph.FEATHER),
            Map.entry("gravity_well", Glyph.ORB), Map.entry("repulsion_field", Glyph.RING), Map.entry("kinetic_slam", Glyph.FIST), Map.entry("force_jump", Glyph.WINGS),
            Map.entry("kinetic_catch", Glyph.SHIELD),
            Map.entry("vine_lash", Glyph.LEAF), Map.entry("thorn_lash", Glyph.SPIKE), Map.entry("root_snare", Glyph.CHAIN), Map.entry("thorn_volley", Glyph.SPIKE),
            Map.entry("healing_bloom", Glyph.FLOWER), Map.entry("guardian_vine", Glyph.TOWER), Map.entry("pollen_veil", Glyph.CLOUD), Map.entry("natures_step", Glyph.WINGS),
            Map.entry("blooming_ground", Glyph.FLOWER),
            Map.entry("magic_shield", Glyph.SHIELD), Map.entry("aegis_shield", Glyph.SHIELD), Map.entry("aegis_dome", Glyph.ORB), Map.entry("counterspell", Glyph.CROSS),
            Map.entry("mirror_ward", Glyph.MIRROR), Map.entry("phase_guard", Glyph.EYE), Map.entry("cleansing_light", Glyph.SUN), Map.entry("rescue_blink", Glyph.WINGS),
            Map.entry("shadow_bolt", Glyph.BOLT), Map.entry("void_bolt", Glyph.ORB), Map.entry("shadow_grasp", Glyph.HAND), Map.entry("abyssal_chains", Glyph.CHAIN),
            Map.entry("soul_drain", Glyph.BEAM), Map.entry("umbral_cloak", Glyph.EYE), Map.entry("black_flame", Glyph.FLAME), Map.entry("nightfall", Glyph.MOON),
            Map.entry("shadow_clone", Glyph.MIRROR), Map.entry("curse_mark", Glyph.TARGET), Map.entry("hex", Glyph.SKULL), Map.entry("withering_wave", Glyph.WAVE),
            Map.entry("shadowstep", Glyph.WINGS), Map.entry("dark_nova", Glyph.BURST), Map.entry("eclipse_nova", Glyph.MOON), Map.entry("void_rift", Glyph.SWIRL),
            Map.entry("corruption_orb", Glyph.ORB), Map.entry("devouring_shadow", Glyph.DROP), Map.entry("dark_pact", Glyph.BOOK), Map.entry("eclipsing_beam", Glyph.BEAM),
            Map.entry("mirror_image", Glyph.MIRROR), Map.entry("phantom_double", Glyph.MIRROR), Map.entry("veil_of_shadows", Glyph.EYE), Map.entry("false_footsteps", Glyph.FEATHER),
            Map.entry("hall_of_mirrors", Glyph.MIRROR), Map.entry("phantom_wall", Glyph.WALL), Map.entry("dread_vision", Glyph.SKULL),
            Map.entry("blink", Glyph.WINGS), Map.entry("rift_step", Glyph.PORTAL), Map.entry("portal_pair", Glyph.PORTAL), Map.entry("spatial_anchor", Glyph.TARGET),
            Map.entry("gravity_swap", Glyph.SWIRL), Map.entry("rift_snare", Glyph.CHAIN), Map.entry("dimensional_slash", Glyph.CRACK), Map.entry("pocket_rift", Glyph.BOOK),
            Map.entry("radiant_bolt", Glyph.BOLT), Map.entry("radiant_lance", Glyph.BEAM), Map.entry("sunburst", Glyph.SUN), Map.entry("holy_chains", Glyph.CHAIN),
            Map.entry("beacon", Glyph.STAR), Map.entry("sanctuary", Glyph.TOWER),
            Map.entry("arcane_overload", Glyph.STAR), Map.entry("black_hole", Glyph.SWIRL), Map.entry("time_fracture", Glyph.HOURGLASS), Map.entry("reality_tear", Glyph.CRACK),
            Map.entry("spectral_legion", Glyph.SKULL), Map.entry("cataclysm", Glyph.BURST), Map.entry("void_collapse", Glyph.ORB));

    private Icons() {}

    static void apply() {
        for (Spell s : Spells.all()) {
            Glyph g = MAP.get(s.id);
            if (g != null) s.icon = g;
        }
    }
}

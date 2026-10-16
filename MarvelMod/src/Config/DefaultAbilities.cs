using System.Collections.Generic;

namespace MarvelMod.Config;

/// <summary>Default tuning values for every Loki ability.</summary>
public static class DefaultAbilities
{
    // Range is in world units (a crewmate is ~0.8 wide; the vanilla kill range is ~1-2.5).
    // Duration doubles as the stun time for Green Bolt / Enchanted Dagger / Asgardian Wrath.
    private static readonly Dictionary<string, AbilityConfig> _defaults = new()
    {
        // ── Illusion Magic ──────────────────────────────────────────────
        ["mirror_image"]          = new() { Cooldown=12, Duration=20 },
        ["phantom_squad"]         = new() { Cooldown=20, Duration=15, MaxTargets=4 },
        ["false_emergency"]       = new() { Cooldown=30, Duration=3,  Range=7 },
        ["illusion_trap"]         = new() { Cooldown=18, Duration=6,  Range=3.5f },
        ["hall_of_loki"]          = new() { Cooldown=60, Duration=15, MaxTargets=8, CinematicDur=2.5f },

        // ── Shapeshifting ───────────────────────────────────────────────
        ["disguise"]              = new() { Cooldown=20, Duration=20, Range=3.5f },
        ["perfect_impersonation"] = new() { Cooldown=25, Duration=20, Range=3.5f },
        ["creature_form"]         = new() { Cooldown=15, Duration=10 },
        ["identity_swap"]         = new() { Cooldown=30, Duration=12, Range=3.5f },
        ["the_other_you"]         = new() { Cooldown=60, Duration=15, Range=3.5f, CinematicDur=2f },

        // ── Trickster Mobility ──────────────────────────────────────────
        ["mystic_blink"]          = new() { Cooldown=6,  Range=4 },
        ["swap_trick"]            = new() { Cooldown=18, Range=6 },
        ["decoy_escape"]          = new() { Cooldown=20, Duration=8 },
        ["shadow_step"]           = new() { Cooldown=15, Duration=5 },
        ["master_of_misdirection"]= new() { Cooldown=60, Duration=10, MaxTargets=5, CinematicDur=2f },

        // ── Sorcery & Combat ────────────────────────────────────────────
        ["green_bolt"]            = new() { Cooldown=5,  Duration=1.2f, Range=6,   Damage=25 },
        ["binding_ribbons"]       = new() { Cooldown=14, Duration=3,    Range=3.5f },
        ["mystic_shield"]         = new() { Cooldown=25, Duration=30 },
        ["enchanted_dagger"]      = new() { Cooldown=8,  Duration=2,    Range=1.8f, Damage=30 },
        ["asgardian_wrath"]       = new() { Cooldown=60, Duration=2,    Range=4,   Damage=50, MaxTargets=5, CinematicDur=2f },

        // ── God of Mischief ─────────────────────────────────────────────
        ["fake_body"]             = new() { Cooldown=20, Duration=30 },
        ["mind_whisper"]          = new() { Cooldown=30, Duration=4,  Range=6 },
        ["enchanted_witness"]     = new() { Cooldown=25, Duration=8,  Range=6 },
        ["chaos_contract"]        = new() { Cooldown=35, Duration=8,  Range=3.5f },
        ["the_grand_deception"]   = new() { Cooldown=90, Duration=12, Range=4, MaxTargets=6, CinematicDur=2.5f },

        // ── Cinematic finishers (real kills). Duration 0 = default length (8-13 s) ──
        ["infinite_deception"]    = new() { Cooldown=35, Duration=0, Range=2.5f },
        ["kings_last_dance"]      = new() { Cooldown=35, Duration=0, Range=2.5f },
        ["betrayal_paradox"]      = new() { Cooldown=40, Duration=0, Range=2.5f },
        ["puppet_master"]         = new() { Cooldown=40, Duration=0, Range=3f },
        ["kneel_before_trickster"]= new() { Cooldown=75, Duration=0, Range=3f },

        // ── Mind ────────────────────────────────────────────────────────
        ["possession"]            = new() { Cooldown=45, Duration=20 },
        ["loki_contract"]         = new() { Cooldown=60 },
        ["ultimate_betrayal"]     = new() { Cooldown=20 },
        ["possess_stop"]          = new() { Cooldown=0 },
        ["possess_frame"]         = new() { Cooldown=0, Range=2f },
        ["possess_kill"]          = new() { Cooldown=0 },

        // ── THANOS: Power ───────────────────────────────────────────────
        ["power_blast"]           = new() { Cooldown=6,  Duration=1.5f, Range=6 },
        ["cosmic_beam"]           = new() { Cooldown=14, Duration=1.5f, Range=7 },
        ["shockwave"]             = new() { Cooldown=16, Duration=1f,   Range=3 },
        ["power_overload"]        = new() { Cooldown=30, Duration=6 },
        ["planetbreaker"]         = new() { Cooldown=90, Range=5, Radius=2, Damage=100, CinematicDur=2.5f },
        // ── Space ──
        ["space_blink"]           = new() { Cooldown=6,  Range=4 },
        ["space_rift"]            = new() { Cooldown=25, Duration=12 },
        ["gravity_well"]          = new() { Cooldown=20, Duration=3, Range=3.5f },
        ["spatial_prison"]        = new() { Cooldown=20, Duration=4, Range=4 },
        ["cosmic_exile"]          = new() { Cooldown=70, Duration=6, Range=4, CinematicDur=1.5f },
        // ── Reality ──
        ["reality_warp"]          = new() { Cooldown=18, Duration=4, Range=4 },
        ["transmutation"]         = new() { Cooldown=25, Duration=5, Range=3 },
        ["reality_shatter"]       = new() { Cooldown=14, Duration=1.2f, Radius=2 },
        ["false_world"]           = new() { Cooldown=35, Duration=8, Range=5 },
        ["reality_collapse"]      = new() { Cooldown=90, Duration=1.5f, Range=25, CinematicDur=2.5f },
        // ── Soul ──
        ["soul_sight"]            = new() { Cooldown=30, Duration=8 },
        ["soul_drain"]            = new() { Cooldown=25, Duration=3, Range=3 },
        ["spirit_projection"]     = new() { Cooldown=35, Duration=8 },
        ["soul_chains"]           = new() { Cooldown=18, Duration=3.5f, Range=4 },
        ["soul_world"]            = new() { Cooldown=90, Duration=7, Range=4, CinematicDur=2f },
        // ── Time ──
        ["time_freeze"]           = new() { Cooldown=22, Duration=3, Range=4 },
        ["rewind"]                = new() { Cooldown=15, Duration=3 },
        ["temporal_echo"]         = new() { Cooldown=20, Duration=6 },
        ["age_manipulation"]      = new() { Cooldown=25, Duration=5, Range=4 },
        ["eternal_loop"]          = new() { Cooldown=80, Duration=6, Range=4, CinematicDur=1.5f },
        // ── Mind ──
        ["thanos_mind_control"]   = new() { Cooldown=25, Duration=3, Range=4 },
        ["psychic_pulse"]         = new() { Cooldown=20, Duration=3, Range=4 },
        ["memory_erasure"]        = new() { Cooldown=30, Duration=5, Range=4 },
        ["illusion_army"]         = new() { Cooldown=25, Duration=12, MaxTargets=4 },
        ["absolute_domination"]   = new() { Cooldown=90, Duration=10, MaxTargets=8, CinematicDur=2.5f },
        // ── Infinity (all six stones) ──
        ["infinity_surge"]        = new() { Cooldown=45, Duration=2, Range=6 },
        ["the_snap"]              = new() { Cooldown=180 },
        ["titans_judgment"]       = new() { Cooldown=120, Range=12, Damage=100, CinematicDur=2.5f },

        // ── Meeting abilities (Damage on read_their_minds: 1 = show who they voted for, 0 = only "voted / skipped") ──
        ["read_their_minds"]      = new() { Cooldown=0, UsesPerMeeting=1, Damage=1 },
        ["rewrite_the_vote"]      = new() { Cooldown=0, UsesPerMeeting=1, Duration=8, MeetingPhase="voting" },
        ["second_chance"]         = new() { Cooldown=0, UsesPerMeeting=1, MeetingPhase="voting" },
        ["soul_verdict"]          = new() { Cooldown=0, UsesPerMeeting=1 },
        ["snap_vote"]             = new() { Cooldown=0, UsesPerMeeting=1, MeetingPhase="voting" },
        ["illusionary_testimony"] = new() { Cooldown=0, UsesPerMeeting=2 },
        ["shapeshifters_alibi"]   = new() { Cooldown=0, UsesPerMeeting=1, Duration=15 },
        ["meeting_whisper"]       = new() { Cooldown=0, UsesPerMeeting=2 },
        ["vote_illusion"]         = new() { Cooldown=0, UsesPerMeeting=1, Duration=10, MeetingPhase="voting" },
        ["council_of_variants"]   = new() { Cooldown=0, UsesPerMeeting=1, Duration=5 },

        // ── DOCTOR STRANGE (crewmate role) ──
        ["sling_ring"]            = new() { Cooldown=8 },
        ["portal_link"]           = new() { Cooldown=4 },
        ["portal_redirect"]       = new() { Cooldown=25, Range=2.5f },
        ["portal_snare"]          = new() { Cooldown=30, Duration=40 },
        ["multiversal_gateway"]   = new() { Cooldown=90, Range=4, CinematicDur=2f },
        ["crimson_bands"]         = new() { Cooldown=20, Duration=3.5f, Range=4 },
        ["eldritch_whip"]         = new() { Cooldown=15, Range=5 },
        ["eldritch_shield"]       = new() { Cooldown=30, Duration=15 },
        ["mystic_blades"]         = new() { Cooldown=10, Duration=1.3f, Range=6 },
        ["crimson_judgment"]      = new() { Cooldown=90, Range=3 },
        ["mirror_shard"]          = new() { Cooldown=8,  Duration=1.2f, Range=6 },
        ["fractured_reality"]     = new() { Cooldown=22, Duration=4, Range=4 },
        ["mirror_clone"]          = new() { Cooldown=20, Duration=12 },
        ["dimensional_prison"]    = new() { Cooldown=40, Duration=6, Range=3.5f },
        ["mirror_world_collapse"] = new() { Cooldown=90, Duration=7, Range=4, CinematicDur=2f },
        ["temporal_rewind"]       = new() { Cooldown=14, Duration=3 },
        ["time_bubble"]           = new() { Cooldown=22, Duration=6, Radius=2 },
        ["strange_echo"]          = new() { Cooldown=18, Duration=6 },
        ["time_loop"]             = new() { Cooldown=28, Duration=5, Range=4 },
        ["eye_of_agamotto"]       = new() { Cooldown=80, Duration=4, Range=4, CinematicDur=1.5f },
        ["astral_projection"]     = new() { Cooldown=30, Duration=8, Range=8 },
        ["astral_sight"]          = new() { Cooldown=30, Duration=8 },
        ["astral_bind"]           = new() { Cooldown=18, Duration=3, Range=4 },
        ["dimension_step"]        = new() { Cooldown=7,  Range=3.5f },
        ["astral_exile"]          = new() { Cooldown=80, Duration=6, Range=4, CinematicDur=1.5f },
        ["portal_maze"]           = new() { Cooldown=40, Duration=0, Range=2.5f },
        ["mirror_dimension_finisher"] = new() { Cooldown=45, Duration=0, Range=2.5f },
        ["crimson_bands_finisher"]= new() { Cooldown=40, Duration=0, Range=2.5f },
        ["temporal_fracture"]     = new() { Cooldown=45, Duration=0, Range=2.5f },
        ["sorcerer_supreme"]      = new() { Cooldown=90, Duration=0, Range=3f },
        ["assign_strange"]        = new() { Cooldown=0 },

        // ── Hidden ──────────────────────────────────────────────────────
        ["map_teleport"]          = new() { Cooldown=0 },
    };

    public static AbilityConfig Get(string id) =>
        _defaults.TryGetValue(id, out var v) ? CloneOf(v) : new AbilityConfig();

    public static IEnumerable<KeyValuePair<string, AbilityConfig>> All()
    {
        foreach (var kv in _defaults)
            yield return new KeyValuePair<string, AbilityConfig>(kv.Key, CloneOf(kv.Value));
    }

    private static AbilityConfig CloneOf(AbilityConfig src) => new()
    {
        Enabled      = src.Enabled,
        Cooldown     = src.Cooldown,
        Duration     = src.Duration,
        CastTime     = src.CastTime,
        Range        = src.Range,
        Radius       = src.Radius,
        Damage       = src.Damage,
        MaxTargets   = src.MaxTargets,
        VfxIntensity = src.VfxIntensity,
        CinematicDur = src.CinematicDur,
        PracticeOnly = src.PracticeOnly,
        UsesPerMeeting = src.UsesPerMeeting,
        MeetingPhase = src.MeetingPhase,
    };
}

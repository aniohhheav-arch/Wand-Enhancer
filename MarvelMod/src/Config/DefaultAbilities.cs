using System.Collections.Generic;

namespace MarvelMod.Config;

/// <summary>Default tuning values for every Loki ability.</summary>
public static class DefaultAbilities
{
    private static readonly Dictionary<string, AbilityConfig> _defaults = new()
    {
        // ── Illusion Magic ──────────────────────────────────────────────
        ["mirror_image"]        = new() { Cooldown=12, Duration=20, Range=0,  MaxTargets=1 },
        ["phantom_squad"]       = new() { Cooldown=20, Duration=25, Range=0,  MaxTargets=4 },
        ["false_emergency"]     = new() { Cooldown=30, Duration=5,  Range=20, MaxTargets=3, PracticeOnly=false },
        ["illusion_trap"]       = new() { Cooldown=18, Duration=8,  Range=15, MaxTargets=1 },
        ["hall_of_loki"]        = new() { Cooldown=60, Duration=15, Range=0,  MaxTargets=8, CinematicDur=4 },

        // ── Shapeshifting ───────────────────────────────────────────────
        ["disguise"]            = new() { Cooldown=20, Duration=30, Range=10, MaxTargets=1 },
        ["perfect_impersonation"]= new(){ Cooldown=25, Duration=20, Range=8,  MaxTargets=1 },
        ["creature_form"]       = new() { Cooldown=15, Duration=10, Range=0,  MaxTargets=1 },
        ["identity_swap"]       = new() { Cooldown=30, Duration=12, Range=12, MaxTargets=2 },
        ["the_other_you"]       = new() { Cooldown=60, Duration=15, Range=15, MaxTargets=1, CinematicDur=4 },

        // ── Trickster Mobility ──────────────────────────────────────────
        ["mystic_blink"]        = new() { Cooldown=6,  Duration=0,  Range=8,  MaxTargets=1 },
        ["swap_trick"]          = new() { Cooldown=18, Duration=0,  Range=12, MaxTargets=1 },
        ["decoy_escape"]        = new() { Cooldown=20, Duration=8,  Range=10, MaxTargets=1 },
        ["shadow_step"]         = new() { Cooldown=15, Duration=5,  Range=0,  MaxTargets=1 },
        ["master_of_misdirection"]= new(){ Cooldown=60, Duration=10, Range=0, MaxTargets=1, CinematicDur=5 },

        // ── Sorcery & Combat ────────────────────────────────────────────
        ["green_bolt"]          = new() { Cooldown=5,  Duration=0,  Range=15, Damage=25, MaxTargets=1 },
        ["binding_ribbons"]     = new() { Cooldown=14, Duration=4,  Range=10, MaxTargets=1 },
        ["mystic_shield"]       = new() { Cooldown=25, Duration=0,  Range=0,  MaxTargets=1 },
        ["enchanted_dagger"]    = new() { Cooldown=8,  Duration=0,  Range=3,  Damage=30, MaxTargets=1 },
        ["asgardian_wrath"]     = new() { Cooldown=60, Duration=6,  Range=20, Damage=50, MaxTargets=5, CinematicDur=5 },

        // ── God of Mischief ─────────────────────────────────────────────
        ["fake_body"]           = new() { Cooldown=20, Duration=30, Range=0,  MaxTargets=1 },
        ["mind_whisper"]        = new() { Cooldown=30, Duration=0,  Range=20, MaxTargets=1, PracticeOnly=false },
        ["enchanted_witness"]   = new() { Cooldown=25, Duration=8,  Range=20, MaxTargets=1 },
        ["chaos_contract"]      = new() { Cooldown=35, Duration=20, Range=15, MaxTargets=1 },
        ["the_grand_deception"] = new() { Cooldown=90, Duration=12, Range=0,  MaxTargets=6, CinematicDur=6 },
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
    };
}

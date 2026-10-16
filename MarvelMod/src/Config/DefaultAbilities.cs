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

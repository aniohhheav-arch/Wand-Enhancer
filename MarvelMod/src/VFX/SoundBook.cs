using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Characters;
using UnityEngine;

namespace MarvelMod.VFX;

/// <summary>
/// Gives every ability its own activation sound. Each page (stone / discipline) has a palette of five
/// distinct main sounds, so no two abilities on a page share one, plus a quieter background texture
/// that marks the page. Pitch is shaped per ability; ultimates get a deep extra layer.
/// </summary>
public static class SoundBook
{
    private sealed record Palette(SfxKind[] Main, SfxKind Texture, float BasePitch);

    private static readonly Dictionary<string, Palette> Palettes = new()
    {
        // Thanos
        ["Power"]    = new(new[] { SfxKind.Boom, SfxKind.Rise, SfxKind.Thud, SfxKind.Crackle, SfxKind.Gong }, SfxKind.Rumble, 0.9f),
        ["Space"]    = new(new[] { SfxKind.Whoosh, SfxKind.Warp, SfxKind.Rise, SfxKind.Shimmer, SfxKind.Gong }, SfxKind.Hum, 1.1f),
        ["Reality"]  = new(new[] { SfxKind.Warp, SfxKind.Glass, SfxKind.Crackle, SfxKind.Zap, SfxKind.Gong }, SfxKind.Rumble, 0.8f),
        ["Soul"]     = new(new[] { SfxKind.Heartbeat, SfxKind.Hum, SfxKind.Whoosh, SfxKind.Creak, SfxKind.Choir }, SfxKind.Shimmer, 1.2f),
        ["Time"]     = new(new[] { SfxKind.Tick, SfxKind.Warp, SfxKind.Chime, SfxKind.Hum, SfxKind.Bell }, SfxKind.Tick, 1f),
        ["Mind"]     = new(new[] { SfxKind.Shimmer, SfxKind.Heartbeat, SfxKind.Warp, SfxKind.Chime, SfxKind.Choir }, SfxKind.Hum, 1.4f),
        ["Infinity"] = new(new[] { SfxKind.Rise, SfxKind.Snap, SfxKind.Gong }, SfxKind.Choir, 1f),
        // Loki
        ["Illusion Magic"]     = new(new[] { SfxKind.Shimmer, SfxKind.Chime, SfxKind.Zap, SfxKind.Warp, SfxKind.Choir }, SfxKind.Whoosh, 1.2f),
        ["Shapeshifting"]      = new(new[] { SfxKind.Warp, SfxKind.Whoosh, SfxKind.Creak, SfxKind.Shimmer, SfxKind.Gong }, SfxKind.Hum, 1.1f),
        ["Trickster Mobility"] = new(new[] { SfxKind.Whoosh, SfxKind.Warp, SfxKind.Shimmer, SfxKind.Tick, SfxKind.Rise }, SfxKind.Whoosh, 1.3f),
        ["Sorcery & Combat"]   = new(new[] { SfxKind.Zap, SfxKind.Creak, SfxKind.Hum, SfxKind.Slash, SfxKind.Gong }, SfxKind.Crackle, 1f),
        ["God of Mischief"]    = new(new[] { SfxKind.Thud, SfxKind.Heartbeat, SfxKind.Shimmer, SfxKind.Warp, SfxKind.Choir }, SfxKind.Hum, 1f),
        ["Executions"]         = new(new[] { SfxKind.Hum, SfxKind.Slash, SfxKind.Glass, SfxKind.Creak, SfxKind.Choir }, SfxKind.Rumble, 0.9f),
        ["Mind_Loki"]          = new(new[] { SfxKind.Heartbeat, SfxKind.Chime, SfxKind.Gong }, SfxKind.Hum, 0.9f),
        // Doctor Strange
        ["Sling Ring"]       = new(new[] { SfxKind.Whoosh, SfxKind.Shimmer, SfxKind.Warp, SfxKind.Crackle, SfxKind.Gong }, SfxKind.Crackle, 1.1f),
        ["Mystic Combat"]    = new(new[] { SfxKind.Crackle, SfxKind.Slash, SfxKind.Hum, SfxKind.Shimmer, SfxKind.Gong }, SfxKind.Crackle, 1f),
        ["Mirror Dimension"] = new(new[] { SfxKind.Glass, SfxKind.Warp, SfxKind.Shimmer, SfxKind.Glass, SfxKind.Gong }, SfxKind.Shimmer, 1.2f),
        ["Time Stone"]       = new(new[] { SfxKind.Tick, SfxKind.Warp, SfxKind.Chime, SfxKind.Tick, SfxKind.Bell }, SfxKind.Tick, 0.9f),
        ["Astral"]           = new(new[] { SfxKind.Hum, SfxKind.Shimmer, SfxKind.Heartbeat, SfxKind.Whoosh, SfxKind.Choir }, SfxKind.Shimmer, 1.3f),
        // Scarlet Witch / Thor / team-ups
        ["Chaos Magic"]      = new(new[] { SfxKind.Sizzle, SfxKind.Reverse, SfxKind.Glass, SfxKind.Heartbeat, SfxKind.Stab }, SfxKind.Reverse, 1.1f),
        ["Wanda Finishers"]  = new(new[] { SfxKind.Reverse, SfxKind.Choir, SfxKind.Creak, SfxKind.Glass, SfxKind.Stab }, SfxKind.Hum, 0.9f),
        ["Thunder"]          = new(new[] { SfxKind.Clang, SfxKind.Crackle, SfxKind.Thud, SfxKind.Whoosh, SfxKind.Thunder }, SfxKind.Sizzle, 1f),
        ["Thor Finishers"]   = new(new[] { SfxKind.Clang, SfxKind.Gong, SfxKind.Crackle, SfxKind.Rumble, SfxKind.Thunder }, SfxKind.Rumble, 0.85f),
        ["Team-Up"]          = new(new[] { SfxKind.Thunder, SfxKind.Glass, SfxKind.Stab }, SfxKind.Reverse, 0.9f),
    };

    private static readonly Palette Fallback = new(new[] { SfxKind.Chime, SfxKind.Whoosh, SfxKind.Zap, SfxKind.Hum, SfxKind.Gong }, SfxKind.Shimmer, 1f);
    private static readonly Dictionary<string, int> IndexCache = new();

    /// <summary>Position of an ability on its page (0-4), so each slot gets a different main sound.</summary>
    private static int IndexOf(AbilityBase a)
    {
        if (IndexCache.TryGetValue(a.Id, out var i)) return i;
        int found = 0;
        foreach (var c in CharacterRegistry.All)
            foreach (var tab in c.Tabs)
            {
                int idx = tab.Abilities.IndexOf(a);
                if (idx >= 0) { found = idx; goto done; }
            }
        done:
        return IndexCache[a.Id] = found;
    }

    public static void PlayActivation(AbilityBase a, Vector2 at)
    {
        if (a.Silent || a.Category == "Hidden" || a.Category == "Meeting") return;
        string key = a.Category == "Mind" && a.Id is "possession" or "loki_contract" or "ultimate_betrayal" ? "Mind_Loki" : a.Category;
        var pal = Palettes.TryGetValue(key, out var p) ? p : Fallback;
        int idx = IndexOf(a);
        var main = a.IsUltimate ? pal.Main[pal.Main.Length - 1] : pal.Main[idx % pal.Main.Length];
        float pitch = pal.BasePitch * (0.92f + 0.07f * idx) * (a.IsUltimate ? 0.8f : 1f);
        Sfx.Play(main, pitch, at, 0.8f, cinematic: a.IsUltimate);
        Sfx.Play(pal.Texture, pal.BasePitch * 0.9f, at, 0.3f);                // page texture
        if (a.IsUltimate) Sfx.Play(SfxKind.Rumble, 0.7f, at, 0.6f, cinematic: true);
    }
}

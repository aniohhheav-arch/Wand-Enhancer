using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>
/// Pocket dimensions (Cosmic Exile, Soul World): players are moved far outside the map to a
/// decorated area and brought back to where they were when the time is up (or at a meeting).
/// </summary>
public static class Dimensions
{
    public static readonly Vector2 Cosmic = new(260f, 260f);
    public static readonly Vector2 Soul = new(-260f, 260f);

    private static readonly Dictionary<byte, (Vector2 home, float until)> Away = new();

    public static bool IsAway(PlayerControl pc) => Away.ContainsKey(pc.PlayerId);

    /// <summary>Call on every client. Only the controlling client moves the player.</summary>
    public static void Send(PlayerControl pc, Vector2 center, Vector2 offset, float duration, Color tint, string title)
    {
        if (!Players.IsAlive(pc)) return;
        if (pc == PlayerControl.LocalPlayer)
        {
            Fx.ScreenTint(new Color(tint.r, tint.g, tint.b, 0.45f), duration);
            Fx.Toast(title, tint, 2.5f, 4.5f, 1.6f);
        }
        Away[pc.PlayerId] = (pc.transform.position, Time.time + duration);
        Fx.Burst(pc.transform.position, tint, 1.8f, 0.4f);
        Players.Teleport(pc, center + offset);
    }

    /// <summary>Decoration around a dimension's centre (stars, rune rings, floating souls).</summary>
    public static void Scenery(Vector2 center, Color color, float duration, int seed)
    {
        var rng = new System.Random(seed);
        for (int i = 0; i < 40; i++)
        {
            var p = center + new Vector2((float)(rng.NextDouble() * 14 - 7), (float)(rng.NextDouble() * 10 - 5));
            var star = Fx.MakeSprite(i % 3 == 0 ? Sprites.Glow : Sprites.Star, p, Color.Lerp(color, Color.white, (float)rng.NextDouble() * 0.6f), 0.15f + (float)rng.NextDouble() * 0.3f);
            float phase = (float)rng.NextDouble() * 6f;
            var basePos = star.transform.position;
            Fx.Animate(star, duration, k =>
            {
                star.transform.position = basePos + new Vector3(0f, Mathf.Sin(Time.time + phase) * 0.15f, 0f);
                star.transform.Rotate(0, 0, 30f * Time.deltaTime);
            });
        }
        for (int i = 0; i < 3; i++)
        {
            var ring = Fx.MakeSprite(Sprites.Runes, center, new Color(color.r, color.g, color.b, 0.4f), 3f + i * 2.5f);
            float spin = i % 2 == 0 ? 12f : -9f;
            Fx.Animate(ring, duration, k => ring.transform.Rotate(0, 0, spin * Time.deltaTime));
        }
    }

    /// <summary>Every frame on every client.</summary>
    public static void Tick()
    {
        if (Away.Count == 0) return;
        bool meeting = MeetingHud.Instance != null;
        var done = new List<byte>();
        foreach (var kv in Away)
            if (meeting || Time.time >= kv.Value.until) done.Add(kv.Key);
        foreach (var id in done)
        {
            var home = Away[id].home;
            Away.Remove(id);
            var pc = Players.ById(id);
            if (pc == null || meeting) continue;   // meetings move everyone to the table anyway
            Fx.Burst(home, Color.white, 1.5f, 0.4f);
            Players.Teleport(pc, home);
        }
    }

    public static void Clear() => Away.Clear();
}

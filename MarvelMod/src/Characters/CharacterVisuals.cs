using System.Collections.Generic;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>
/// Single owner of every temporary change to a player's visible body (lift, tilt, scale, alpha).
///
/// Root cause of the Planetbreaker cosmetic glitch: poses wrote absolute values into the
/// cosmetics transform using a height cached once per PlayerId (ids are reused across players and
/// games, and the real height differs per body type / disguise), overwrote the rotation, and the
/// restore lived in a callback that is skipped when the wind-up is cut short. Effects that overlapped
/// (finishers, Creature Form, Planetbreaker) also overwrote each other.
///
/// Now: the original transform is captured fresh when the first modifier is applied, modifiers are
/// keyed and combined, and the exact original is restored when the last one is removed — or
/// automatically on death, meetings, the lobby, or after each modifier's safety timeout.
/// </summary>
public static class CharacterVisuals
{
    private sealed class Mod
    {
        public float Lift, Tilt, Scale = 1f, Alpha = 1f, Expires;
    }

    private sealed class State
    {
        public PlayerControl Player = null!;
        public Vector3 Pos;
        public Quaternion Rot;
        public Vector3 Scale;
        public bool AlphaTouched;
        public readonly Dictionary<string, Mod> Mods = new();
    }

    private static readonly Dictionary<byte, State> States = new();

    /// <summary>Apply (or update) a named modifier. It is removed automatically after maxSeconds.</summary>
    public static void Set(PlayerControl pc, string key, float lift = 0f, float tilt = 0f, float scale = 1f, float alpha = 1f, float maxSeconds = 20f)
    {
        if (pc == null || pc.cosmetics == null) return;
        if (!States.TryGetValue(pc.PlayerId, out var st) || st.Player != pc)
        {
            if (st != null) Restore(st);
            var t = pc.cosmetics.transform;
            st = new State { Player = pc, Pos = t.localPosition, Rot = t.localRotation, Scale = t.localScale };
            States[pc.PlayerId] = st;
        }
        st.Mods[key] = new Mod { Lift = lift, Tilt = tilt, Scale = scale, Alpha = alpha, Expires = Time.time + maxSeconds };
        Apply(st);
    }

    public static void Clear(PlayerControl pc, string key)
    {
        if (pc == null || !States.TryGetValue(pc.PlayerId, out var st)) return;
        st.Mods.Remove(key);
        if (st.Mods.Count == 0) { Restore(st); States.Remove(pc.PlayerId); }
        else Apply(st);
    }

    public static bool Has(PlayerControl pc, string key) => States.TryGetValue(pc.PlayerId, out var st) && st.Mods.ContainsKey(key);

    private static void Apply(State st)
    {
        var pc = st.Player;
        if (pc == null || pc.cosmetics == null) return;
        float lift = 0f, tilt = 0f, scale = 1f, alpha = 1f;
        foreach (var m in st.Mods.Values) { lift += m.Lift; tilt += m.Tilt; scale *= m.Scale; alpha = Mathf.Min(alpha, m.Alpha); }
        var t = pc.cosmetics.transform;
        t.localPosition = st.Pos + new Vector3(0f, lift, 0f);
        t.localRotation = st.Rot * Quaternion.Euler(0f, 0f, tilt);
        t.localScale = new Vector3(st.Scale.x * scale, st.Scale.y * scale, st.Scale.z);
        // only touch transparency while a fade is active (don't fight Shadow Step / Transmutation)
        if (alpha < 0.999f) { pc.cosmetics.SetPhantomRoleAlpha(alpha); st.AlphaTouched = true; }
        else if (st.AlphaTouched) { pc.cosmetics.SetPhantomRoleAlpha(1f); st.AlphaTouched = false; }
    }

    private static void Restore(State st)
    {
        var pc = st.Player;
        if (pc == null || pc.cosmetics == null) return;
        var t = pc.cosmetics.transform;
        t.localPosition = st.Pos;
        t.localRotation = st.Rot;
        t.localScale = st.Scale;
        if (st.AlphaTouched && Players.IsAlive(pc)) pc.cosmetics.SetPhantomRoleAlpha(1f);
    }

    public static void RestoreAll()
    {
        foreach (var st in States.Values) Restore(st);
        States.Clear();
    }

    /// <summary>Every frame: drop expired modifiers and anything on dead/missing players.</summary>
    public static void Tick()
    {
        if (States.Count == 0) return;
        if (MeetingHud.Instance != null) { RestoreAll(); return; }
        var done = new List<byte>();
        foreach (var kv in States)
        {
            var st = kv.Value;
            if (st.Player == null || st.Player.Data == null || st.Player.Data.IsDead || st.Player.Data.Disconnected)
            {
                Restore(st);
                done.Add(kv.Key);
                continue;
            }
            var expired = new List<string>();
            foreach (var m in st.Mods) if (Time.time >= m.Value.Expires) expired.Add(m.Key);
            if (expired.Count == 0) continue;
            foreach (var k in expired) st.Mods.Remove(k);
            if (st.Mods.Count == 0) { Restore(st); done.Add(kv.Key); }
            else Apply(st);
        }
        foreach (var id in done) States.Remove(id);
    }
}

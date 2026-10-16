using System;
using System.Collections;
using Reactor.Utilities;
using System.Collections.Generic;
using MarvelMod.VFX;
using Reactor.Utilities.Attributes;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>
/// Appearance changes for Loki, using the game's own Shapeshifted outfit slot — the player's
/// real (Default) outfit is never modified. Runs on every client, so everyone sees it.
/// </summary>
[RegisterInIl2Cpp]
public class ShapeshiftManager : MonoBehaviour
{
    public ShapeshiftManager(IntPtr ptr) : base(ptr) { }

    public static ShapeshiftManager? Instance { get; private set; }
    private static readonly BepInEx.Logging.ManualLogSource Log = BepInEx.Logging.Logger.CreateLogSource("MarvelMod.Shapeshift");

    private readonly Dictionary<byte, ShapeshiftState> _active = new();

    public void Awake() => Instance = this;

    /// <summary>source takes target's look. copyNameplate also takes the name.</summary>
    public void Shapeshift(PlayerControl source, PlayerControl target, float duration, bool copyNameplate = false)
    {
        if (source.Data == null || target.Data == null) return;
        var state = GetOrCreate(source);
        state.RestoreAt = Time.time + duration;
        state.Outfit = true;

        PlayShapeshift(source, target);
        if (!copyNameplate)
            Coroutines.Start(CoKeepOwnName(source, 1.6f));
    }

    /// <summary>The game's real shapeshift animation (poof + morph); falls back to an instant swap.</summary>
    private static void PlayShapeshift(PlayerControl source, PlayerControl target)
    {
        try
        {
            source.Shapeshift(target, true);
        }
        catch (Exception e)
        {
            Log.LogWarning($"Animated shapeshift failed, using instant swap: {e.Message}");
            if (target == source) source.RawSetOutfit(source.Data.DefaultOutfit, PlayerOutfitType.Default);
            else source.RawSetOutfit(target.Data.DefaultOutfit, PlayerOutfitType.Shapeshifted);
        }
    }

    /// <summary>Disguise copies the look but keeps your name (re-applied after the morph finishes).</summary>
    private static IEnumerator CoKeepOwnName(PlayerControl pc, float duration)
    {
        for (float t = 0; t < duration; t += Time.deltaTime)
        {
            if (pc == null || pc.Data == null) yield break;
            pc.cosmetics.SetName(pc.Data.DefaultOutfit.PlayerName);
            yield return null;
        }
    }

    /// <summary>Shrinks Loki into a small creature.</summary>
    public void ApplyCreatureForm(PlayerControl source, float duration)
    {
        var state = GetOrCreate(source);
        state.RestoreAt = Time.time + duration;
        if (!state.Creature)
        {
            state.Creature = true;
            state.OriginalScale = source.cosmetics.transform.localScale;
            source.cosmetics.transform.localScale = state.OriginalScale * 0.55f;
        }
    }

    /// <summary>Two players swap looks (both directions use their untouched Default outfits).</summary>
    public void SwapAppearances(PlayerControl a, PlayerControl b, float duration)
    {
        Shapeshift(a, b, duration, copyNameplate: true);
        Shapeshift(b, a, duration, copyNameplate: true);
    }

    private ShapeshiftState GetOrCreate(PlayerControl pc)
    {
        if (!_active.TryGetValue(pc.PlayerId, out var state))
            _active[pc.PlayerId] = state = new ShapeshiftState { PlayerId = pc.PlayerId };
        return state;
    }

    private static void Restore(PlayerControl pc, ShapeshiftState state)
    {
        if (state.Outfit && pc.Data != null)
        {
            if (MeetingHud.Instance != null) pc.RawSetOutfit(pc.Data.DefaultOutfit, PlayerOutfitType.Default);
            else PlayShapeshift(pc, pc);   // shapeshifting into yourself = animated revert
        }
        if (state.Creature && pc.cosmetics != null)
            pc.cosmetics.transform.localScale = state.OriginalScale;
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, pc.transform.position);
    }

    public void RestoreAll()
    {
        foreach (var kv in _active)
        {
            var pc = Players.ById(kv.Key);
            if (pc != null) Restore(pc, kv.Value);
        }
        _active.Clear();
    }

    public void Update()
    {
        if (_active.Count == 0) return;
        var now = Time.time;
        var done = new List<byte>();
        foreach (var kv in _active)
            if (now >= kv.Value.RestoreAt) done.Add(kv.Key);

        foreach (var id in done)
        {
            var pc = Players.ById(id);
            if (pc != null) Restore(pc, _active[id]);
            _active.Remove(id);
        }
    }

    private class ShapeshiftState
    {
        public byte    PlayerId;
        public float   RestoreAt;
        public bool    Outfit;
        public bool    Creature;
        public Vector3 OriginalScale = Vector3.one;
    }
}

using System;
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
            source.cosmetics.SetName(source.Data.DefaultOutfit.PlayerName);
    }

    /// <summary>Instant outfit change (the Shapeshifter morph animation is intentionally not used).</summary>
    private static void PlayShapeshift(PlayerControl source, PlayerControl target)
    {
        if (target == source) source.RawSetOutfit(source.Data.DefaultOutfit, PlayerOutfitType.Default);
        else source.RawSetOutfit(target.Data.DefaultOutfit, PlayerOutfitType.Shapeshifted);
    }

    /// <summary>Shrinks Loki into a small creature.</summary>
    public void ApplyCreatureForm(PlayerControl source, float duration)
    {
        var state = GetOrCreate(source);
        state.RestoreAt = Time.time + duration;
        state.Creature = true;
        CharacterVisuals.Set(source, "creature", scale: 0.55f, maxSeconds: duration + 1f);
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
            PlayShapeshift(pc, pc);
        }
        if (state.Creature) CharacterVisuals.Clear(pc, "creature");
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

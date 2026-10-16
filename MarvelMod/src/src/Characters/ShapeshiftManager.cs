using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>
/// Manages cosmetic shapeshifting for Loki.
/// IMPORTANT: Never touches PlayerControl.Data.Role, task completion, or server-side state.
/// All changes are local cosmetic overrides only.
/// </summary>
public class ShapeshiftManager : MonoBehaviour
{
    public static ShapeshiftManager? Instance { get; private set; }

    private readonly Dictionary<byte, ShapeshiftState> _active = new();

    private void Awake() => Instance = this;

    public void Shapeshift(PlayerControl source, PlayerControl target, float duration, bool copyNameplate = false)
    {
        if (_active.TryGetValue(source.PlayerId, out var old))
            RestoreAppearance(source, old);

        var state = new ShapeshiftState
        {
            PlayerId       = source.PlayerId,
            OriginalHat    = source.Data?.Outfits[PlayerOutfitType.Default]?.HatId ?? "",
            OriginalSkin   = source.Data?.Outfits[PlayerOutfitType.Default]?.SkinId ?? "",
            OriginalColor  = source.Data?.Outfits[PlayerOutfitType.Default]?.ColorId ?? 0,
            OriginalName   = source.Data?.PlayerName ?? "",
            RestoreAt      = Time.realtimeSinceStartup + duration,
            CopyNameplate  = copyNameplate,
        };

        // Apply target's cosmetics locally
        ApplyCosmeticsLocally(source, target, copyNameplate);
        _active[source.PlayerId] = state;
    }

    public void ApplyCreatureForm(PlayerControl source, float duration)
    {
        var state = new ShapeshiftState
        {
            PlayerId      = source.PlayerId,
            OriginalHat   = source.Data?.Outfits[PlayerOutfitType.Default]?.HatId ?? "",
            OriginalSkin  = source.Data?.Outfits[PlayerOutfitType.Default]?.SkinId ?? "",
            OriginalColor = source.Data?.Outfits[PlayerOutfitType.Default]?.ColorId ?? 0,
            OriginalName  = source.Data?.PlayerName ?? "",
            RestoreAt     = Time.realtimeSinceStartup + duration,
            IsCreature    = true,
        };
        // Scale down to simulate creature
        source.transform.localScale = new Vector3(0.5f, 0.5f, 1f);
        _active[source.PlayerId] = state;
    }

    public void SwapAppearances(PlayerControl a, PlayerControl b, float duration)
    {
        Shapeshift(a, b, duration);
        Shapeshift(b, a, duration);
    }

    private void ApplyCosmeticsLocally(PlayerControl source, PlayerControl target, bool copyNameplate)
    {
        var tOutfit = target.Data?.Outfits[PlayerOutfitType.Default];
        if (tOutfit == null) return;
        var sOutfit = source.Data?.Outfits[PlayerOutfitType.Default];
        if (sOutfit == null) return;
        sOutfit.HatId  = tOutfit.HatId;
        sOutfit.SkinId = tOutfit.SkinId;
        sOutfit.ColorId = tOutfit.ColorId;
        if (copyNameplate && source.Data != null)
            source.Data.PlayerName = $"[~]{target.Data?.PlayerName}"; // marked fake
        source.UpdateFromPlayerData(source.Data!, PlayerOutfitType.Default, false, true);
    }

    private void RestoreAppearance(PlayerControl source, ShapeshiftState state)
    {
        var outfit = source.Data?.Outfits[PlayerOutfitType.Default];
        if (outfit != null)
        {
            outfit.HatId   = state.OriginalHat;
            outfit.SkinId  = state.OriginalSkin;
            outfit.ColorId = state.OriginalColor;
        }
        if (state.CopyNameplate && source.Data != null)
            source.Data.PlayerName = state.OriginalName;
        if (state.IsCreature)
            source.transform.localScale = Vector3.one;
        source.UpdateFromPlayerData(source.Data!, PlayerOutfitType.Default, false, true);
    }

    private void Update()
    {
        var now = Time.realtimeSinceStartup;
        var toRestore = new List<byte>();
        foreach (var kv in _active)
            if (now >= kv.Value.RestoreAt) toRestore.Add(kv.Key);

        foreach (var id in toRestore)
        {
            var pc = PlayerControl.AllPlayerControls.ToArray().FirstOrDefault(p => p.PlayerId == id);
            if (pc != null) RestoreAppearance(pc, _active[id]);
            _active.Remove(id);
        }
    }

    private class ShapeshiftState
    {
        public byte   PlayerId;
        public string OriginalHat   = "";
        public string OriginalSkin  = "";
        public int    OriginalColor;
        public string OriginalName  = "";
        public float  RestoreAt;
        public bool   CopyNameplate;
        public bool   IsCreature;
    }
}

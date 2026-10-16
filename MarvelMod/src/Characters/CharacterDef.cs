using System;
using System.Collections.Generic;
using AmongUs.GameOptions;
using MarvelMod.Abilities;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>One page of up to five ability buttons.</summary>
public sealed class AbilityTab
{
    public string Name { get; }
    public string IconId { get; }
    public List<AbilityBase> Abilities { get; }
    /// <summary>Optional lock (e.g. Thanos stones not collected yet).</summary>
    public Func<PlayerControl, bool>? IsUnlocked { get; }

    public AbilityTab(string name, string iconId, List<AbilityBase> abilities, Func<PlayerControl, bool>? isUnlocked = null)
    {
        Name = name;
        IconId = iconId;
        Abilities = abilities;
        IsUnlocked = isUnlocked;
    }

    public bool Unlocked(PlayerControl pc) => IsUnlocked == null || IsUnlocked(pc);
}

/// <summary>A playable Marvel character (Loki, Thanos).</summary>
public abstract class CharacterDef
{
    public abstract string Id { get; }
    public abstract string DisplayName { get; }
    public abstract Color Color { get; }
    /// <summary>Crewmate roles are handed out by the host; impostor characters are rolled by each impostor.</summary>
    public virtual bool IsCrewmateRole => false;
    public abstract IReadOnlyList<AbilityTab> Tabs { get; }
    /// <summary>Abilities without a button (map teleport, internal events).</summary>
    public virtual IEnumerable<AbilityBase> Hidden => Array.Empty<AbilityBase>();
    /// <summary>Abilities shown in the meeting panel.</summary>
    public virtual IReadOnlyList<AbilityBase> MeetingAbilities => Array.Empty<AbilityBase>();

    public IEnumerable<AbilityBase> AllAbilities()
    {
        foreach (var tab in Tabs)
            foreach (var a in tab.Abilities) yield return a;
        foreach (var a in Hidden) yield return a;
        foreach (var a in MeetingAbilities) yield return a;
    }

    /// <summary>A page that temporarily replaces the normal tabs (e.g. while possessing someone).</summary>
    public virtual AbilityTab? OverrideTab(PlayerControl lp) => null;

    /// <summary>Per-frame hook for the local player playing this character.</summary>
    public virtual void LocalUpdate(PlayerControl lp, HudManager hud) { }
    /// <summary>Called when the local player becomes / stops being this character.</summary>
    public virtual void OnLocalAssigned(PlayerControl lp) { }
    public virtual void OnLocalUnassigned() { }
}

/// <summary>
/// Which character the local player is.
///  - Online/local games: impostors only. "Natural selection": each impostor randomly becomes
///    Loki or Thanos at the start of the game (config ImpostorCharacter can force one).
///  - Freeplay and lobby: the character picked on the freeplay laptop / with F3 (config ActiveCharacter).
/// </summary>
public static class CharacterRegistry
{
    private static readonly List<CharacterDef> _all = new();
    private static CharacterDef? _local;
    private static bool _rolledThisGame;
    private static bool _hostAssigned;
    private static readonly Dictionary<byte, string> Assigned = new();

    /// <summary>Crewmate role handed out by the host (synced by a cast).</summary>
    public static void Assign(byte playerId, string characterId)
    {
        Assigned[playerId] = characterId;
        var def = ById(characterId);
        if (def != null && playerId == PlayerControl.LocalPlayer?.PlayerId)
            VFX.Fx.Toast($"YOU ARE {def.DisplayName.ToUpperInvariant()}", def.Color, 3.5f, 5f, 1.6f);
    }

    /// <summary>Host only, once per match: choose StrangeCount crewmates to be Doctor Strange.</summary>
    private static void HostAssignCrewRoles(PlayerControl lp)
    {
        if (_hostAssigned || AmongUsClient.Instance == null || !AmongUsClient.Instance.AmHost) return;
        var hud = HudManager.Instance;
        if (hud == null || hud.IsIntroDisplayed) return;
        var crew = new List<PlayerControl>();
        foreach (var pc in Players.All())
        {
            if (pc.Data == null || pc.Data.Role == null) return;   // roles not set yet
            if (!pc.Data.Role.IsImpostor && Players.IsAlive(pc)) crew.Add(pc);
        }
        _hostAssigned = true;
        int n = Mathf.Clamp(Config.ModConfig.Data.StrangeCount, 0, crew.Count);
        for (int i = 0; i < n; i++)
        {
            int idx = UnityEngine.Random.Range(0, crew.Count);
            Networking.LokiRpc.RpcCast(lp, "assign_strange", crew[idx].PlayerId, 0f, 0f, 0f, 0f, 0);
            crew.RemoveAt(idx);
        }
    }

    public static IReadOnlyList<CharacterDef> All => _all;

    public static void Register(CharacterDef def) => _all.Add(def);

    public static CharacterDef? ById(string? id)
    {
        if (string.IsNullOrEmpty(id)) return null;
        foreach (var c in _all)
            if (string.Equals(c.Id, id, StringComparison.OrdinalIgnoreCase)) return c;
        return null;
    }

    /// <summary>Ability lookup across every character (for the network cast).</summary>
    public static AbilityBase? Ability(string id)
    {
        foreach (var c in _all)
            foreach (var a in c.AllAbilities())
                if (a.Id == id) return a;
        return null;
    }

    public static IEnumerable<AbilityBase> AllAbilities()
    {
        foreach (var c in _all)
            foreach (var a in c.AllAbilities()) yield return a;
    }

    public static bool IsFreeplay =>
        AmongUsClient.Instance != null && AmongUsClient.Instance.NetworkMode == NetworkModes.FreePlay;

    public static bool InLobby => LobbyBehaviour.Instance != null;

    /// <summary>The character the local player currently plays, or null (crewmate / none).</summary>
    public static CharacterDef? Local => _local;

    /// <summary>Re-evaluated every frame from the HUD.</summary>
    public static void UpdateLocal(PlayerControl lp)
    {
        CharacterDef? want;
        if (IsFreeplay || InLobby)
        {
            _rolledThisGame = false;
            _hostAssigned = false;
            Assigned.Clear();
            want = ById(ModConfig.Data.ActiveCharacter);
        }
        else if (ShipStatus.Instance == null)
        {
            _rolledThisGame = false;
            _hostAssigned = false;
            Assigned.Clear();
            want = null;
        }
        else
        {
            HostAssignCrewRoles(lp);
            bool impostor = lp.Data != null && lp.Data.Role != null && lp.Data.Role.IsImpostor;
            if (!impostor) want = Assigned.TryGetValue(lp.PlayerId, out var crewRole) ? ById(crewRole) : null;
            else
            {
                if (!_rolledThisGame || _local == null)
                {
                    _rolledThisGame = true;
                    var forced = ById(ModConfig.Data.ImpostorCharacter);
                    var evil = _all.FindAll(c => !c.IsCrewmateRole);
                    want = forced != null && !forced.IsCrewmateRole ? forced : evil.Count > 0 ? evil[UnityEngine.Random.Range(0, evil.Count)] : null;
                    if (want != null)
                        VFX.Fx.Toast($"YOU ARE {want.DisplayName.ToUpperInvariant()}", want.Color, 3.5f, 5f, 1.6f);
                }
                else want = _local;
            }
        }

        if (want == _local) return;
        _local?.OnLocalUnassigned();
        _local = want;
        _local?.OnLocalAssigned(lp);
    }

    /// <summary>Freeplay/lobby: pick a character (null id = none).</summary>
    public static void Choose(string? id)
    {
        ModConfig.Data.ActiveCharacter = id ?? "none";
        ModConfig.Save();
    }

    /// <summary>Cycles none → Loki → Thanos → none (freeplay / lobby only).</summary>
    public static string CycleFreeplayCharacter()
    {
        var ids = new List<string> { "none" };
        foreach (var c in _all) ids.Add(c.Id);
        int i = ids.FindIndex(x => string.Equals(x, ModConfig.Data.ActiveCharacter, StringComparison.OrdinalIgnoreCase));
        var next = ids[(i + 1) % ids.Count];
        Choose(next);
        return next;
    }
}

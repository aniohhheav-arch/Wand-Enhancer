using System.Collections.Generic;
using System;
using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.Networking;
using UnityEngine;

namespace MarvelMod.Abilities;

public enum TargetMode
{
    /// <summary>No target needed.</summary>
    None,
    /// <summary>Button only lights up with a player in range.</summary>
    Required,
    /// <summary>Uses the closest player in range if there is one (aim assist).</summary>
    Optional,
    /// <summary>Pick any player from the Shapeshifter menu (no range limit).</summary>
    Menu,
}

/// <summary>Everything an ability needs when it runs (on every client).</summary>
public sealed class CastContext
{
    public PlayerControl Caster { get; }
    public PlayerControl? Target { get; }
    /// <summary>Ability-specific point chosen by the caster (destination, impact point, ...).</summary>
    public Vector2 Point { get; }
    /// <summary>Same sequence on every client, so random spawn points match.</summary>
    public System.Random Rng { get; }
    /// <summary>Second chosen point (two-point map picks, e.g. Space Rift).</summary>
    public Vector2 Point2 { get; init; }
    public AbilityConfig Cfg { get; }

    public CastContext(PlayerControl caster, PlayerControl? target, Vector2 point, int seed, AbilityConfig cfg)
    {
        Caster = caster;
        Target = target;
        Point = point;
        Rng = new System.Random(seed);
        Cfg = cfg;
    }

    public Vector2 CasterPos => Caster.transform.position;
    public bool IsLocalCaster => Caster.AmOwner;
    public bool IsLocalTarget => Target != null && Target.AmOwner;
    public bool LocalIs(PlayerControl? pc) => pc != null && pc == PlayerControl.LocalPlayer;
}

/// <summary>Base class every ability inherits from.</summary>
public abstract class AbilityBase
{
    public abstract string Id   { get; }
    public abstract string Name { get; }
    public abstract string Category { get; }

    /// <summary>Short text under the HUD button.</summary>
    public virtual string Label => Name.ToUpperInvariant();
    public virtual TargetMode Targeting => TargetMode.None;
    public virtual bool IsUltimate => false;
    /// <summary>No cast flash or sound for anyone (stealth abilities).</summary>
    public virtual bool Silent => false;
    /// <summary>&gt; 0: clicking the button opens the map and you pick this many points on it first.</summary>
    public virtual int MapPoints => 0;
    /// <summary>Points picked on the map (set right before TryActivate).</summary>
    public List<Vector2> PickedPoints { get; } = new();
    /// <summary>Set in Prepare to send a second point.</summary>
    protected Vector2 SecondPoint;
    /// <summary>Colour of the cast flash everyone sees on the caster.</summary>
    public virtual Color CastColor => new(0.3f, 1f, 0.45f, 1f);

    private float _lastUsed = -9999f;

    public AbilityConfig Cfg => ModConfig.GetAbility(Id);

    public float CooldownRemaining =>
        Mathf.Max(0f, PracticeModeUnlimited ? 0f : _lastUsed + Cfg.Cooldown - Time.realtimeSinceStartup);

    public bool IsReady => Cfg.Enabled && CooldownRemaining <= 0f;

    private static bool PracticeModeUnlimited => ModConfig.Data.PracticeUnlimited;

    /// <summary>Extra availability rule (e.g. "you have someone to sever"). Checked by the HUD and on cast.</summary>
    public virtual bool CanUse(PlayerControl caster) => true;

    /// <summary>Extra rule for the chosen target (menu picks). Show your own message when refusing.</summary>
    public virtual bool IsValidTarget(PlayerControl caster, PlayerControl target) => true;

    /// <summary>Picks the target this ability would use right now (for HUD state and casting).</summary>
    public PlayerControl? FindTarget(PlayerControl caster) =>
        Targeting is TargetMode.None or TargetMode.Menu ? null : Players.ClosestTarget(caster, Cfg.Range);

    /// <summary>Validate, then broadcast the cast to every client. Returns true on success.</summary>
    public bool TryActivate(PlayerControl? caster, PlayerControl? target = null)
    {
        if (caster == null || !IsReady || !Players.IsAlive(caster) || !CanUse(caster)) return false;
        if (Targeting is TargetMode.Required or TargetMode.Optional && target == null) target = FindTarget(caster);
        if (Targeting is TargetMode.Required or TargetMode.Menu && !Players.IsAlive(target)) return false;
        if (target != null && !IsValidTarget(caster, target)) return false;

        Vector2 point = caster.transform.position;
        SecondPoint = Vector2.zero;
        Prepare(caster, ref target, ref point);

        LokiRpc.RpcCast(caster, Id, target != null ? target.PlayerId : LokiRpc.NoTarget,
            point.x, point.y, SecondPoint.x, SecondPoint.y, UnityEngine.Random.Range(int.MinValue, int.MaxValue));

        if (!PracticeModeUnlimited)
            _lastUsed = Time.realtimeSinceStartup;
        PracticeLog($"[{Name}] activated" + (target != null ? $" -> {target.Data?.PlayerName}" : ""));
        return true;
    }

    /// <summary>Runs on the casting client only, before broadcasting: pick aim point / final target.</summary>
    protected virtual void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point) { }

    /// <summary>Called on every client when the cast arrives.</summary>
    public void Execute(CastContext ctx)
    {
        try
        {
            BeforeCast(ctx);
            OnCast(ctx);
        }
        catch (Exception e)
        {
            PracticeLog($"[{Name}] ERROR: {e}");
        }
    }

    /// <summary>Shared cast presentation (e.g. Thanos stone glow + sound). Runs on every client.</summary>
    protected virtual void BeforeCast(CastContext ctx) { }

    /// <summary>Lower the remaining cooldown by a fraction (Soul Drain).</summary>
    public void ReduceCooldown(float fraction)
    {
        float remaining = CooldownRemaining;
        if (remaining > 0f) _lastUsed -= remaining * fraction;
    }

    /// <summary>Ability logic. Runs on every client — only move players you own (see Players.Teleport).</summary>
    protected abstract void OnCast(CastContext ctx);

    public void ResetCooldown() => _lastUsed = -9999f;

    private static void PracticeLog(string msg)
    {
        Practice.PracticeModeManager.Instance?.TestLog(msg);
    }
}

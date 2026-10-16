using System;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.Abilities;

/// <summary>Base class every ability inherits from.</summary>
public abstract class AbilityBase
{
    public abstract string Id   { get; }
    public abstract string Name { get; }
    public abstract string Category { get; }

    private float _lastUsed = -9999f;

    public AbilityConfig Cfg => ModConfig.GetAbility(Id);

    public float CooldownRemaining =>
        Mathf.Max(0f, (Cfg.Enabled && !PracticeModeUnlimited)
            ? _lastUsed + Cfg.Cooldown - Time.realtimeSinceStartup
            : 0f);

    public bool IsReady => Cfg.Enabled && CooldownRemaining <= 0f;

    private static bool PracticeModeUnlimited =>
        ModConfig.Data.PracticeUnlimited;

    /// <summary>Try to activate the ability. Returns true on success.</summary>
    public bool TryActivate(PlayerControl? caster, PlayerControl? target = null)
    {
        if (!IsReady) return false;
        if (!Cfg.Enabled) return false;

        try
        {
            OnActivate(caster, target);
            if (!PracticeModeUnlimited)
                _lastUsed = Time.realtimeSinceStartup;
            PracticeLog($"[{Name}] activated" + (target != null ? $" → {target.Data?.PlayerName}" : ""));
            return true;
        }
        catch (Exception e)
        {
            PracticeLog($"[{Name}] ERROR: {e.Message}");
            return false;
        }
    }

    /// <summary>Implement actual ability logic here.</summary>
    protected abstract void OnActivate(PlayerControl? caster, PlayerControl? target);

    public void ResetCooldown() => _lastUsed = -9999f;

    private static void PracticeLog(string msg)
    {
        if (ModConfig.Data.DebugMode)
            BepInEx.Logging.Logger.CreateLogSource("MarvelMod").LogInfo(msg);
        Practice.PracticeModeManager.Instance?.TestLog(msg);
    }
}

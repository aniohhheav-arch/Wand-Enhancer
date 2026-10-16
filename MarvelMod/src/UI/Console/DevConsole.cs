using Reactor.Utilities.Attributes;
using System;
using System.Collections.Generic;
using System.Linq;
using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.Practice;
using UnityEngine;

namespace MarvelMod.UI.Console;

/// <summary>
/// Live developer console.  Open with F6 (configurable).
/// Validates all input — never executes OS commands or arbitrary code.
/// </summary>
[RegisterInIl2Cpp]
public class DevConsole : MonoBehaviour
{
    public DevConsole(IntPtr ptr) : base(ptr) { }

    private bool   _open;
    public static bool IsOpen { get; private set; }
    private string _input       = "";
    private string _lastOutput  = "";
    private readonly List<string> _history = new();
    private int    _historyIdx  = -1;
    private readonly List<string> _log     = new();
    private Vector2 _scroll;

    // ── Unity lifecycle ───────────────────────────────────────────────────────

    public void Update()
    {
        var key = ModConfig.Data.ConsoleKey;
        bool toggle = key switch
        {
            "F6"  => Input.GetKeyDown(KeyCode.F6),
            "F7"  => Input.GetKeyDown(KeyCode.F7),
            "F8"  => Input.GetKeyDown(KeyCode.F8),
            "F9"  => Input.GetKeyDown(KeyCode.F9),
            "F10" => Input.GetKeyDown(KeyCode.F10),
            _     => Input.GetKeyDown(KeyCode.F6),
        };
        if (toggle) IsOpen = _open = !_open;
    }

    public void OnGUI()
    {
        if (!_open) return;
        DrawConsole();
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    private void DrawConsole()
    {
        var bg = new Rect(20, 20, Screen.width - 40, Screen.height * 0.55f);
        GUI.Box(bg, "MarvelMod Dev Console  [" + ModConfig.Data.ConsoleKey + " to close]");

        // Log area
        var logRect = new Rect(bg.x + 5, bg.y + 22, bg.width - 10, bg.height - 60);
        _scroll = GUI.BeginScrollView(logRect, _scroll, new Rect(0, 0, logRect.width - 20, _log.Count * 18));
        for (int i = 0; i < _log.Count; i++)
            GUI.Label(new Rect(0, i * 18, logRect.width, 18), _log[i]);
        GUI.EndScrollView();

        // Input field
        GUI.SetNextControlName("ConsoleInput");
        _input = GUI.TextField(new Rect(bg.x + 5, bg.yMax - 32, bg.width - 70, 26), _input);
        if (GUI.Button(new Rect(bg.xMax - 62, bg.yMax - 32, 60, 26), "Run") || EnterPressed())
        {
            if (!string.IsNullOrWhiteSpace(_input))
                Execute(_input.Trim());
            _input = "";
        }
        if (ModConfig.Data.DebugMode)
            GUI.Label(new Rect(bg.x + 5, bg.yMax - 52, 200, 16), "⚙ Debug overrides active");
    }

    private bool EnterPressed() =>
        Event.current?.type == EventType.KeyDown && Event.current.keyCode == KeyCode.Return;

    // ── Command dispatch ──────────────────────────────────────────────────────

    private void Execute(string raw)
    {
        AddLog($"> {raw}");
        _history.Insert(0, raw);
        if (_history.Count > 50) _history.RemoveAt(_history.Count - 1);

        var parts = raw.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length == 0) return;

        try
        {
            var result = Dispatch(parts);
            AddLog(result);
        }
        catch (ConsoleException ex) { AddLog($"ERROR: {ex.Message}"); }
        catch (Exception ex)        { AddLog($"INTERNAL: {ex.Message}"); }
    }

    private string Dispatch(string[] p) => p[0].ToLowerInvariant() switch
    {
        "help"       => CmdHelp(p),
        "clear"      => CmdClear(),
        "status"     => CmdStatus(),
        "config"     => CmdConfig(p),
        "character"  => CmdCharacter(p),
        "ability"    => CmdAbility(p),
        "illusion"   => CmdIllusion(p),
        "practice"   => CmdPractice(p),
        "vfx"        => CmdVfx(p),
        "cinematic"  => CmdCinematic(p),
        "debug"      => CmdDebug(p),
        _            => throw new ConsoleException($"Unknown command '{p[0]}'. Type 'help' for commands."),
    };

    // ── Commands ──────────────────────────────────────────────────────────────

    private string CmdHelp(string[] p)
    {
        if (p.Length > 1) return GetDetailedHelp(p[1]);
        return
            "Commands: help, clear, status, config, character, ability, illusion, practice, vfx, cinematic, debug\n" +
            "Use 'help <command>' for details.";
    }

    private string GetDetailedHelp(string cmd) => cmd.ToLowerInvariant() switch
    {
        "ability"   => "ability cooldown <id> <s> | duration <id> <s> | casttime | range | damage | radius | enabled <id> <on|off> | reset <id> | reset_all",
        "illusion"  => "illusion spawn <n> | behavior <idle|wander|follow|task|random> | despawn_all | movement_speed <v> | lifetime <s> | nameplates <on|off>",
        "practice"  => "practice reset | dummies <n> | spawn_dummy | clear_dummies | invincible <on|off> | unlimited <on|off>",
        "vfx"       => "vfx enabled <on|off> | intensity <0-100> | particles <on|off> | screen_shake <on|off> | quality <low|medium|high|ultra>",
        "character" => "character loki | info | abilities",
        "config"    => "config list | save | reload | reset",
        "debug"     => "debug on | off | abilities | targets | cooldowns | performance | errors",
        _           => $"No help for '{cmd}'.",
    };

    private string CmdClear()  { _log.Clear(); return ""; }

    private string CmdStatus() =>
        $"Character: {ModConfig.Data.ActiveCharacter} | " +
        $"VFX: {ModConfig.Data.VfxEnabled} | " +
        $"Debug: {ModConfig.Data.DebugMode} | " +
        $"Unlimited: {ModConfig.Data.PracticeUnlimited} | " +
        $"Illusions: {IllusionManager.Instance?.ActiveCount ?? 0} | " +
        $"Dummies: {PracticeModeManager.Instance?.GetDummies().Count ?? 0}";

    private string CmdConfig(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: config <list|save|reload|reset>");
        return p[1].ToLowerInvariant() switch
        {
            "list"   => FormatConfigList(),
            "save"   => Commit(() => ModConfig.Save(),   "Config saved."),
            "reload" => Commit(() => ModConfig.Load(),   "Config reloaded."),
            "reset"  => Commit(() => ModConfig.Reset(),  "Config reset to defaults."),
            _        => throw new ConsoleException($"Unknown config subcommand '{p[1]}'."),
        };
    }

    private string FormatConfigList()
    {
        var d = ModConfig.Data;
        return $"ActiveCharacter={d.ActiveCharacter} VfxEnabled={d.VfxEnabled} " +
               $"VfxIntensity={d.VfxIntensity} CinematicSkip={d.CinematicSkip} " +
               $"PracticeUnlimited={d.PracticeUnlimited} PracticeInvincible={d.PracticeInvincible} " +
               $"PracticeDummies={d.PracticeDummies} DebugMode={d.DebugMode} ConsoleKey={d.ConsoleKey}";
    }

    private string CmdCharacter(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: character <loki|info|abilities>");
        switch (p[1].ToLowerInvariant())
        {
            case "loki":
                ModConfig.Data.ActiveCharacter = "loki";
                ModConfig.Save();
                return "Character set to Loki.";
            case "info":
                return $"Active: {ModConfig.Data.ActiveCharacter} | Loki abilities: {LokiCharacter.Instance.All.Count}";
            case "abilities":
                return string.Join("\n", LokiCharacter.Instance.All.Select(a =>
                    $"  [{a.Id}] {a.Name} ({a.Category}) — CD:{a.Cfg.Cooldown}s Dur:{a.Cfg.Duration}s"));
            default:
                throw new ConsoleException($"Unknown character '{p[1]}'.");
        }
    }

    private string CmdAbility(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: ability <subcommand> ...");
        if (p[1] == "reset_all")
        {
            LokiCharacter.Instance.ResetAllCooldowns();
            return "All cooldowns reset.";
        }
        if (p.Length < 3) throw new ConsoleException("Usage: ability <subcommand> <id> [value]");

        var sub = p[1].ToLowerInvariant();
        var id  = p[2].ToLowerInvariant();

        if (sub == "reset")
        {
            var def = DefaultAbilities.Get(id);
            ModConfig.SetAbility(id, def);
            LokiCharacter.Instance.GetById(id)?.ResetCooldown();
            return $"[{id}] reset to defaults.";
        }

        if (sub == "enabled")
        {
            bool on = ParseBool(p, 3);
            var cfg = ModConfig.GetAbility(id);
            cfg.Enabled = on;
            ModConfig.SetAbility(id, cfg);
            return $"[{id}] enabled = {on}.";
        }

        float val = ParseFloat(p, 3);
        var ability = ModConfig.GetAbility(id);

        switch (sub)
        {
            case "cooldown":  ability.Cooldown     = val; break;
            case "duration":  ability.Duration     = val; break;
            case "casttime":  ability.CastTime     = val; break;
            case "range":     ability.Range        = val; break;
            case "damage":    ability.Damage       = val; break;
            case "radius":    ability.Radius       = val; break;
            default: throw new ConsoleException($"Unknown ability subcommand '{sub}'.");
        }

        ModConfig.SetAbility(id, ability);
        return $"[{id}] {sub} = {val} (applied immediately).";
    }

    private string CmdIllusion(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: illusion <subcommand> ...");
        switch (p[1].ToLowerInvariant())
        {
            case "spawn":
            {
                int n = p.Length > 2 ? int.Parse(p[2]) : 1;
                var caster = PlayerControl.LocalPlayer;
                for (int i = 0; i < n; i++)
                    IllusionManager.Instance?.SpawnIllusion(caster, caster.transform.position + Vector3.right * i);
                return $"Spawned {n} illusions.";
            }
            case "behavior":
                if (p.Length < 3) throw new ConsoleException("Usage: illusion behavior <idle|wander|follow|task|random>");
                ModConfig.Data.IllusionBehavior = p[2].ToLowerInvariant();
                ModConfig.Save();
                return $"Illusion behavior set to '{p[2]}'.";
            case "despawn_all":
                IllusionManager.Instance?.DespawnAll();
                return "All illusions despawned.";
            case "movement_speed":
                ModConfig.Data.IllusionSpeed = ParseFloat(p, 2);
                ModConfig.Save();
                return $"Illusion speed set to {ModConfig.Data.IllusionSpeed}.";
            case "lifetime":
                ModConfig.Data.IllusionLifetime = ParseFloat(p, 2);
                ModConfig.Save();
                return $"Illusion lifetime set to {ModConfig.Data.IllusionLifetime}s.";
            case "nameplates":
                ModConfig.Data.IllusionNameplates = ParseBool(p, 2);
                ModConfig.Save();
                return $"Illusion nameplates: {ModConfig.Data.IllusionNameplates}.";
            default:
                throw new ConsoleException($"Unknown illusion subcommand '{p[1]}'.");
        }
    }

    private string CmdPractice(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: practice <subcommand> ...");
        var mgr = PracticeModeManager.Instance;
        switch (p[1].ToLowerInvariant())
        {
            case "reset":
                mgr?.ResetSandbox();
                return "Sandbox reset.";
            case "dummies":
                int n = ParseInt(p, 2);
                mgr?.SetDummyCount(n);
                ModConfig.Data.PracticeDummies = n;
                ModConfig.Save();
                return $"Dummy count set to {n}.";
            case "spawn_dummy":
                mgr?.SpawnDummies(1);
                return "Spawned 1 dummy.";
            case "clear_dummies":
                mgr?.ClearDummies();
                return "Dummies cleared.";
            case "invincible":
                ModConfig.Data.PracticeInvincible = ParseBool(p, 2);
                ModConfig.Save();
                return $"Invincible mode: {ModConfig.Data.PracticeInvincible}.";
            case "unlimited":
                ModConfig.Data.PracticeUnlimited = ParseBool(p, 2);
                ModConfig.Save();
                return $"Unlimited abilities: {ModConfig.Data.PracticeUnlimited}.";
            default:
                throw new ConsoleException($"Unknown practice subcommand '{p[1]}'.");
        }
    }

    private string CmdVfx(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: vfx <subcommand> ...");
        switch (p[1].ToLowerInvariant())
        {
            case "enabled":      ModConfig.Data.VfxEnabled     = ParseBool(p, 2);  break;
            case "intensity":    ModConfig.Data.VfxIntensity    = ParseInt(p, 2);   break;
            case "particles":    ModConfig.Data.VfxParticles    = ParseBool(p, 2);  break;
            case "screen_shake": ModConfig.Data.VfxScreenShake  = ParseBool(p, 2);  break;
            case "distortion":   ModConfig.Data.VfxDistortion   = ParseBool(p, 2);  break;
            case "quality":
                if (p.Length < 3) throw new ConsoleException("Usage: vfx quality <low|medium|high|ultra>");
                ModConfig.Data.VfxQuality = p[2].ToLowerInvariant();
                break;
            default: throw new ConsoleException($"Unknown vfx subcommand '{p[1]}'.");
        }
        ModConfig.Save();
        return $"vfx.{p[1]} updated.";
    }

    private string CmdCinematic(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: cinematic <skip|replay|duration> ...");
        switch (p[1].ToLowerInvariant())
        {
            case "skip":
                ModConfig.Data.CinematicSkip = ParseBool(p, 2);
                ModConfig.Save();
                return $"Cinematic skip: {ModConfig.Data.CinematicSkip}.";
            case "replay":
                // replay <ability_id>
                if (p.Length < 3) throw new ConsoleException("Usage: cinematic replay <ability_id>");
                var ability = LokiCharacter.Instance.GetById(p[2]);
                if (ability == null) throw new ConsoleException($"Unknown ability '{p[2]}'.");
                ability.TryActivate(PlayerControl.LocalPlayer);
                return $"Replaying {ability.Name}.";
            case "duration":
                if (p.Length < 4) throw new ConsoleException("Usage: cinematic duration <ability_id> <s>");
                var cfg = ModConfig.GetAbility(p[2]);
                cfg.CinematicDur = ParseFloat(p, 3);
                ModConfig.SetAbility(p[2], cfg);
                return $"[{p[2]}] cinematic duration = {cfg.CinematicDur}s.";
            default:
                throw new ConsoleException($"Unknown cinematic subcommand '{p[1]}'.");
        }
    }

    private string CmdDebug(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: debug <on|off|abilities|targets|cooldowns|performance|errors>");
        switch (p[1].ToLowerInvariant())
        {
            case "on":  ModConfig.Data.DebugMode = true;  ModConfig.Save(); return "Debug mode ON.";
            case "off": ModConfig.Data.DebugMode = false; ModConfig.Save(); return "Debug mode OFF.";
            case "abilities":
                return string.Join("\n", LokiCharacter.Instance.All.Select(a =>
                    $"  {a.Id}: enabled={a.Cfg.Enabled} cd={a.CooldownRemaining:F1}s"));
            case "cooldowns":
                return string.Join("\n", LokiCharacter.Instance.All.Select(a =>
                    $"  {a.Id}: {a.CooldownRemaining:F1}s remaining"));
            case "targets":
                return $"Local player: {PlayerControl.LocalPlayer?.Data?.PlayerName ?? "none"}";
            case "performance":
                return $"FPS: {(1f / Time.deltaTime):F0} | Illusions: {IllusionManager.Instance?.ActiveCount ?? 0}";
            case "errors":
                var log = PracticeModeManager.Instance?.TestLogLines;
                return log != null ? string.Join("\n", log.TakeLast(20)) : "No log available.";
            default:
                throw new ConsoleException($"Unknown debug subcommand '{p[1]}'.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float ParseFloat(string[] p, int idx)
    {
        if (idx >= p.Length || !float.TryParse(p[idx], out float v))
            throw new ConsoleException($"Expected a number at position {idx}.");
        return v;
    }

    private static int ParseInt(string[] p, int idx)
    {
        if (idx >= p.Length || !int.TryParse(p[idx], out int v))
            throw new ConsoleException($"Expected an integer at position {idx}.");
        return v;
    }

    private static bool ParseBool(string[] p, int idx)
    {
        if (idx >= p.Length) throw new ConsoleException($"Expected 'on' or 'off' at position {idx}.");
        return p[idx].ToLowerInvariant() switch
        {
            "on" or "true"  or "1" => true,
            "off" or "false" or "0" => false,
            _ => throw new ConsoleException($"Expected 'on' or 'off', got '{p[idx]}'."),
        };
    }

    private static string Commit(Action action, string success)
    {
        action();
        return success;
    }

    private void AddLog(string msg)
    {
        _log.Add(msg);
        if (_log.Count > 300) _log.RemoveAt(0);
        _scroll = new Vector2(0, float.MaxValue); // auto-scroll
    }
}

public class ConsoleException : Exception
{
    public ConsoleException(string msg) : base(msg) { }
}

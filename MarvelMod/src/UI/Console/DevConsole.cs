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
        "meeting"    => CmdMeeting(p),
        "finisher"   => CmdFinisher(p),
        "snap"       => CmdSnap(p),
        "portal"     => CmdPortal(p),
        "sfx"        => CmdSfx(p),
        "arena"      => CmdArena(p),
        "cast"       => CmdCast(p),
        "tune"       => CmdTune(p),
        "wanda"      => CmdHero(p, "wanda"),
        "thor"       => CmdHero(p, "thor"),
        "loki"       => CmdHero(p, "loki"),
        "thanos"     => CmdHero(p, "thanos"),
        "strange"    => CmdHero(p, "strange"),
        "combo"      => CmdCombo(p),
        _            => throw new ConsoleException($"Unknown command '{p[0]}'. Type 'help' for commands."),
    };

    // ── Commands ──────────────────────────────────────────────────────────────

    private string CmdHelp(string[] p)
    {
        if (p.Length > 1) return GetDetailedHelp(p[1]);
        return
            "Commands: help, clear, status, config, character, ability, cast, tune, wanda, thor, loki, thanos, strange, combo, meeting, finisher, snap, portal, illusion, practice, vfx, sfx, arena, cinematic, debug\n" +
            "Use 'help <command>' for details.";
    }

    private string GetDetailedHelp(string cmd) => cmd.ToLowerInvariant() switch
    {
        "ability"   => "ability cooldown <id> <s> | duration <id> <s> | casttime | range | damage | radius | enabled <id> <on|off> | reset <id> | reset_all",
        "illusion"  => "illusion spawn <n> | behavior <idle|wander|follow|task|random> | despawn_all | movement_speed <v> | lifetime <s> | nameplates <on|off>",
        "practice"  => "practice reset | dummies <n> | spawn_dummy | clear_dummies | invincible <on|off> | unlimited <on|off> | cooldowns <on|off>",
        "vfx"       => "vfx enabled <on|off> | intensity <0-200> | particles <on|off> | screen_shake <on|off> | shake <0-300 %> | camera <on|off> | speed <0.4-2.5> | distortion <on|off> | quality <low|medium|high|ultra> (preset) | test <heavy|magic|electric|glitch|cosmic|comedy|eerie|time|chaos|lightning|sigil|charge>",
        "sfx"       => "sfx enabled <on|off> | volume <0-100> | cinematic <0-100> | test <kind or cue> | list | stop",
        "arena"     => "arena reset  (removes leftover effects, restores players, camera, statuses, portals, pocket dimensions; stops sounds)",
        "cast"      => "cast <ability_id>  (practice/freeplay: fire any ability as yourself, on the closest player if it needs a target)",
        "tune"      => "tune cooldowns <percent> | tune reset",
        "wanda" or "thor" or "loki" or "thanos" or "strange"
                    => $"{cmd} <cooldown|duration|range|radius|power|damage|casttime|cinematic> <ability_id> <value> | {cmd} reset <ability_id> | {cmd} list   (power = area/strength radius)",
        "combo"     => "combo list | combo <chaosstorm|reality_thunderclap|end_of_reality> test  (solo preview in freeplay)",
        "character" => "character loki | thanos | strange | none | info | abilities",
        "meeting"   => "meeting ability cooldown|uses_per_meeting|duration <id> <value> | meeting ability enabled|practice_only <id> <on|off> | meeting ability phase <id> <discussion|voting|both> | meeting list | meeting reset",
        "finisher"  => "finisher list | preview <id> (on the closest player, nobody dies) | replay <id> | duration <id> <s> | mode <full|short|skip> | body <on|off>",
        "snap"      => "snap mode <visual|practice_kill|match_kill_half|match_stun>",
        "portal"    => "portal status | create | create_pair | close <id> | close_all | max_pairs <n> | lifetime <s> | size <v> | teleport_cooldown <s>",
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
        if (p.Length < 2) throw new ConsoleException("Usage: character <" + string.Join("|", CharacterRegistry.All.Select(c => c.Id)) + "|none|info|abilities>");
        var arg = p[1].ToLowerInvariant();
        switch (arg)
        {
            case "info":
                return $"Freeplay character: {ModConfig.Data.ActiveCharacter} | Playing now: {CharacterRegistry.Local?.DisplayName ?? "none"} | Impostors get: {ModConfig.Data.ImpostorCharacter}";
            case "abilities":
                var c = CharacterRegistry.Local ?? CharacterRegistry.ById(ModConfig.Data.ActiveCharacter);
                if (c == null) return "No character selected.";
                return string.Join("\n", c.AllAbilities().Select(a =>
                    $"  [{a.Id}] {a.Name} ({a.Category}) - CD:{a.Cfg.Cooldown}s Dur:{a.Cfg.Duration}s"));
            case "none":
                CharacterRegistry.Choose("none");
                return "Freeplay character cleared.";
            default:
                if (CharacterRegistry.ById(arg) == null) throw new ConsoleException($"Unknown character '{p[1]}'. Options: {string.Join(", ", CharacterRegistry.All.Select(c => c.Id))}, none.");
                CharacterRegistry.Choose(arg);
                return $"Freeplay character set to {arg} (used in freeplay and the lobby).";
        }
    }

    private static string RequireAbility(string id)
    {
        if (CharacterRegistry.Ability(id) == null)
            throw new ConsoleException($"Unknown ability id '{id}'. Use 'character abilities' to list ids.");
        return id;
    }

    private string CmdMeeting(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: " + GetDetailedHelp("meeting"));
        switch (p[1].ToLowerInvariant())
        {
            case "list":
                return string.Join("\n", CharacterRegistry.All.SelectMany(c => c.MeetingAbilities).Select(a =>
                    $"  [{a.Id}] {a.Name} - uses {a.Cfg.UsesPerMeeting}/meeting, phase {a.Cfg.MeetingPhase}, enabled {a.Cfg.Enabled}, practice_only {a.Cfg.PracticeOnly}"));
            case "reset":
                foreach (var a in CharacterRegistry.All.SelectMany(c => c.MeetingAbilities))
                    ModConfig.SetAbility(a.Id, DefaultAbilities.Get(a.Id));
                Abilities.Meeting.MeetingAbility.NewMeeting();
                return "Meeting abilities reset to defaults (uses refilled).";
            case "ability":
                if (p.Length < 5) throw new ConsoleException("Usage: meeting ability <setting> <id> <value>");
                var sub = p[2].ToLowerInvariant();
                var id = RequireAbility(p[3].ToLowerInvariant());
                if (CharacterRegistry.Ability(id) is not Abilities.Meeting.MeetingAbility)
                    throw new ConsoleException($"'{id}' is not a meeting ability. Use 'meeting list'.");
                var cfg = ModConfig.GetAbility(id);
                switch (sub)
                {
                    case "cooldown":         cfg.Cooldown = ParseFloat(p, 4); break;
                    case "duration":         cfg.Duration = ParseFloat(p, 4); break;
                    case "uses_per_meeting": cfg.UsesPerMeeting = Math.Max(0, ParseInt(p, 4)); break;
                    case "enabled":          cfg.Enabled = ParseBool(p, 4); break;
                    case "practice_only":    cfg.PracticeOnly = ParseBool(p, 4); break;
                    case "phase":
                        var phase = p[4].ToLowerInvariant();
                        if (phase is not ("discussion" or "voting" or "both")) throw new ConsoleException("Phase must be discussion, voting or both.");
                        cfg.MeetingPhase = phase; break;
                    default: throw new ConsoleException($"Unknown setting '{sub}'.");
                }
                ModConfig.SetAbility(id, cfg);
                return $"[{id}] {sub} = {p[4]} (saved, applies immediately).";
            default:
                throw new ConsoleException("Usage: " + GetDetailedHelp("meeting"));
        }
    }

    private string CmdFinisher(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: " + GetDetailedHelp("finisher"));
        var finishers = CharacterRegistry.AllAbilities().OfType<Abilities.Loki.FinisherAbility>().Distinct().ToList();
        switch (p[1].ToLowerInvariant())
        {
            case "list":
                return string.Join("\n", finishers.Select(f => $"  [{f.Id}] {f.Name} - duration {(f.Cfg.Duration > 0 ? f.Cfg.Duration + "s" : "default")}"));
            case "mode":
                var mode = p.Length > 2 ? p[2].ToLowerInvariant() : "";
                if (mode is not ("full" or "short" or "skip")) throw new ConsoleException("Mode must be full, short or skip.");
                ModConfig.Data.FinisherMode = mode; ModConfig.Save();
                return $"Finisher mode = {mode}.";
            case "body":
                ModConfig.Data.FinisherLeavesBody = ParseBool(p, 2); ModConfig.Save();
                return $"Finishers leave a body = {ModConfig.Data.FinisherLeavesBody}.";
            case "duration":
            {
                if (p.Length < 4) throw new ConsoleException("Usage: finisher duration <id> <seconds>");
                var f = finishers.FirstOrDefault(x => x.Id == p[2].ToLowerInvariant()) ?? throw new ConsoleException($"Unknown finisher '{p[2]}'.");
                var cfg = ModConfig.GetAbility(f.Id);
                cfg.Duration = Math.Max(0f, ParseFloat(p, 3));
                ModConfig.SetAbility(f.Id, cfg);
                return $"[{f.Id}] duration = {cfg.Duration}s (0 = default).";
            }
            case "preview":
            case "replay":
            {
                if (p.Length < 3) throw new ConsoleException("Usage: finisher preview <id>");
                var f = finishers.FirstOrDefault(x => x.Id == p[2].ToLowerInvariant()) ?? throw new ConsoleException($"Unknown finisher '{p[2]}'.");
                var lp = PlayerControl.LocalPlayer ?? throw new ConsoleException("Not in a game.");
                var target = Players.ClosestTarget(lp, 50f) ?? throw new ConsoleException("No player nearby to preview on (freeplay dummies work).");
                _open = false; IsOpen = false;
                f.Preview(lp, target);
                return $"Previewing {f.Name} on {target.Data?.PlayerName} (nobody dies).";
            }
            default:
                throw new ConsoleException("Usage: " + GetDetailedHelp("finisher"));
        }
    }

    private string CmdPortal(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: " + GetDetailedHelp("portal"));
        var lp = PlayerControl.LocalPlayer;
        switch (p[1].ToLowerInvariant())
        {
            case "status": return PortalEngine.Status();
            case "close_all": PortalEngine.CloseAll(); return "All portals closed.";
            case "close":
                return PortalEngine.CloseById(ParseInt(p, 2)) ? $"Portal #{p[2]} (and its link) closed." : throw new ConsoleException($"No portal #{p[2]}. Use 'portal status'.");
            case "create":
                if (lp == null) throw new ConsoleException("Not in a game.");
                var one = PortalEngine.OpenPortal(lp, lp.transform.position, ModConfig.Data.PortalLifetime, linkToPending: true);
                return $"Portal #{one.Id} opened" + (one.Link != null ? $" and linked to #{one.Link.Id}." : " (waiting for a link: walk away and 'portal create' again).");
            case "create_pair":
                if (lp == null) throw new ConsoleException("Not in a game.");
                Vector2 here = lp.transform.position;
                var there = Players.SafeDestination(here, UI.HUD.AbilityButtons.LastMoveDirection, 3f);
                var (a, b) = PortalEngine.OpenPair(lp, here + UI.HUD.AbilityButtons.LastMoveDirection * -0.8f, there, ModConfig.Data.PortalLifetime);
                return $"Linked pair #{a.Id} <-> #{b.Id} (local preview).";
            case "max_pairs": ModConfig.Data.PortalMaxPairs = Math.Max(1, ParseInt(p, 2)); break;
            case "lifetime": ModConfig.Data.PortalLifetime = Math.Clamp(ParseFloat(p, 2), 2f, 600f); break;
            case "size": ModConfig.Data.PortalSize = Math.Clamp(ParseFloat(p, 2), 0.5f, 3f); break;
            case "teleport_cooldown": ModConfig.Data.PortalTeleportCooldown = Math.Clamp(ParseFloat(p, 2), 0.3f, 10f); break;
            default: throw new ConsoleException("Usage: " + GetDetailedHelp("portal"));
        }
        ModConfig.Save();
        return $"portal {p[1]} = {p[2]} (saved).";
    }

    private string CmdSnap(string[] p)
    {
        if (p.Length < 3 || p[1].ToLowerInvariant() != "mode") throw new ConsoleException("Usage: " + GetDetailedHelp("snap"));
        var mode = p[2].ToLowerInvariant();
        if (mode is not ("visual" or "practice_kill" or "match_kill_half" or "match_stun"))
            throw new ConsoleException("Snap mode must be visual, practice_kill, match_kill_half or match_stun.");
        ModConfig.Data.SnapMode = mode; ModConfig.Save();
        return $"The Snap outcome = {mode}.";
    }

    private string CmdAbility(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: ability <subcommand> ...");
        if (p[1] == "reset_all")
        {
            foreach (var a in CharacterRegistry.AllAbilities()) a.ResetCooldown();
            return "All cooldowns reset.";
        }
        if (p.Length < 3) throw new ConsoleException("Usage: ability <subcommand> <id> [value]");

        var sub = p[1].ToLowerInvariant();
        var id  = RequireAbility(p[2].ToLowerInvariant());

        if (sub == "reset")
        {
            var def = DefaultAbilities.Get(id);
            ModConfig.SetAbility(id, def);
            CharacterRegistry.Ability(id)?.ResetCooldown();
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
            case "cooldowns":   // "practice cooldowns off" = no cooldowns
                ModConfig.Data.PracticeUnlimited = !ParseBool(p, 2);
                ModConfig.Save();
                return $"Cooldowns {(ModConfig.Data.PracticeUnlimited ? "OFF" : "ON")}.";
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
            case "shake":        ModConfig.Data.ShakeStrength   = Math.Clamp(ParseInt(p, 2), 0, 300); break;
            case "camera":       ModConfig.Data.CameraEffects   = ParseBool(p, 2);  break;
            case "speed":        ModConfig.Data.AnimationSpeed  = Math.Clamp(ParseFloat(p, 2), 0.4f, 2.5f); break;
            case "quality":
            {
                if (p.Length < 3) throw new ConsoleException("Usage: vfx quality <low|medium|high|ultra>");
                var q = p[2].ToLowerInvariant();
                var d = ModConfig.Data;
                switch (q)
                {
                    case "low":    d.VfxParticles = true; d.VfxIntensity = 70;  d.VfxDistortion = false; d.ShakeStrength = Math.Min(d.ShakeStrength, 60); break;
                    case "medium": d.VfxParticles = true; d.VfxIntensity = 85;  d.VfxDistortion = true; break;
                    case "high":   d.VfxParticles = true; d.VfxIntensity = 100; d.VfxDistortion = true; break;
                    case "ultra":  d.VfxParticles = true; d.VfxIntensity = 120; d.VfxDistortion = true; break;
                    default: throw new ConsoleException($"Unknown quality '{p[2]}'. Use low, medium, high or ultra.");
                }
                d.VfxQuality = q;
                ModConfig.Save();
                return $"Quality preset '{q}': particles x{(q == "low" ? 0.35f : q == "medium" ? 0.65f : q == "ultra" ? 1.4f : 1f)}, intensity {d.VfxIntensity}%, distortion {d.VfxDistortion}.";
            }
            case "test":
            {
                if (p.Length < 3) throw new ConsoleException("Usage: vfx test <style>");
                var lp = PlayerControl.LocalPlayer ?? throw new ConsoleException("No local player.");
                Vector2 at = (Vector2)lp.transform.position + VFX.Fx.TestOffset;
                var s = p[2].ToLowerInvariant();
                switch (s)
                {
                    case "lightning": Reactor.Utilities.Coroutines.Start(VFX.Fx.StrikeFromSky(at, new Color(0.6f, 0.8f, 1f), 0.6f)); break;
                    case "sigil": VFX.Fx.Sigil(at, VFX.Fx.LokiGold, 2f, 1.5f); break;
                    case "charge": Reactor.Utilities.Coroutines.Start(VFX.Fx.Charge(lp.transform, VFX.Fx.ChaosPurple, 1.2f)); break;
                    default:
                        if (!Enum.TryParse<VFX.ImpactStyle>(s, true, out var style))
                            throw new ConsoleException($"Unknown style '{p[2]}'. Try: {string.Join(", ", Enum.GetNames(typeof(VFX.ImpactStyle))).ToLowerInvariant()}, lightning, sigil, charge.");
                        VFX.Fx.Release(at, VFX.Fx.ChaosPurple, style, 1.4f);
                        break;
                }
                return $"Played {s}.";
            }
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
                var ability = CharacterRegistry.Ability(p[2]);
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

    /// <summary>Per-character tuning: "wanda cooldown chaos_bolt 5", "thor power thunderclap 12".</summary>
    private string CmdHero(string[] p, string heroId)
    {
        var hero = CharacterRegistry.ById(heroId) ?? throw new ConsoleException($"Character '{heroId}' is not loaded.");
        if (p.Length < 2) throw new ConsoleException("Usage: " + GetDetailedHelp(heroId));
        var sub = p[1].ToLowerInvariant();
        if (sub == "list")
            return string.Join("\n", hero.AllAbilities().Where(a => a.Category != "Hidden").Select(a =>
                $"  [{a.Id}] {a.Name} - CD {a.Cfg.Cooldown}s, dur {a.Cfg.Duration}s, range {a.Cfg.Range}, radius {a.Cfg.Radius}, dmg {a.Cfg.Damage}"));
        if (p.Length < 3) throw new ConsoleException("Usage: " + GetDetailedHelp(heroId));
        var id = p[2].ToLowerInvariant();
        if (!hero.AllAbilities().Any(a => a.Id == id))
            throw new ConsoleException($"{hero.DisplayName} has no ability '{id}'. Type '{heroId} list'.");
        if (sub == "reset")
        {
            ModConfig.SetAbility(id, DefaultAbilities.Get(id));
            return $"[{id}] reset to defaults.";
        }
        var cfg = ModConfig.GetAbility(id);
        float v = ParseFloat(p, 3);
        if (v < 0f) throw new ConsoleException("Values can't be negative.");
        switch (sub)
        {
            case "cooldown":  cfg.Cooldown = v; break;
            case "duration":  cfg.Duration = v; break;
            case "range":     cfg.Range = v; break;
            case "radius":
            case "power":     cfg.Radius = v; break;
            case "damage":    cfg.Damage = v; break;
            case "casttime":  cfg.CastTime = v; break;
            case "cinematic": cfg.CinematicDur = v; break;
            default: throw new ConsoleException($"Unknown setting '{p[1]}'. Try cooldown, duration, range, radius/power, damage, casttime, cinematic.");
        }
        ModConfig.SetAbility(id, cfg);
        return $"[{id}] {sub} = {v} (saved, applies to the next cast).";
    }

    private string CmdCombo(string[] p)
    {
        var combos = new Abilities.AbilityBase[] { Abilities.Combo.Combos.Chaosstorm, Abilities.Combo.Combos.Thunderclap, Abilities.Combo.Combos.EndOfReality };
        if (p.Length < 2 || p[1].ToLowerInvariant() == "list")
            return string.Join("\n", combos.Select(c => $"  [{c.Id}] {c.Name} - CD {c.Cfg.Cooldown}s, partner range {c.Cfg.Range}"));
        var combo = combos.FirstOrDefault(c => c.Id == p[1].ToLowerInvariant()) ?? throw new ConsoleException($"Unknown combo '{p[1]}'. Type 'combo list'.");
        if (p.Length < 3 || p[2].ToLowerInvariant() != "test") throw new ConsoleException($"Usage: combo {combo.Id} test");
        if (!CharacterRegistry.IsFreeplay && !CharacterRegistry.InLobby) throw new ConsoleException("combo test only works in freeplay / the lobby.");
        var lp = PlayerControl.LocalPlayer ?? throw new ConsoleException("No local player.");
        var who = CharacterRegistry.Local?.Id;
        if (who != "wanda" && who != "thor")
            throw new ConsoleException("Play as Scarlet Witch or Thor first ('character wanda' or 'character thor').");
        combo.ResetCooldown();
        _open = false; IsOpen = false;
        return combo.TryActivate(lp) ? $"Testing {combo.Name} (solo preview with a stand-in partner)." : $"{combo.Name} could not start.";
    }

    private string CmdSfx(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: sfx <enabled|volume|cinematic|test|list|stop> ...");
        var d = ModConfig.Data;
        switch (p[1].ToLowerInvariant())
        {
            case "enabled":   d.SfxEnabled = ParseBool(p, 2); break;
            case "volume":    d.SfxVolume = Math.Clamp(ParseInt(p, 2), 0, 100) / 100f; break;
            case "cinematic": d.CinematicVolume = Math.Clamp(ParseInt(p, 2), 0, 100) / 100f; break;
            case "stop":      VFX.Sfx.StopAll(); return "All sounds stopped.";
            case "list":
                return "Kinds: " + string.Join(", ", Enum.GetNames(typeof(VFX.SfxKind))).ToLowerInvariant() +
                       "\nCues: " + string.Join(", ", Enum.GetNames(typeof(VFX.SfxCue))).ToLowerInvariant();
            case "test":
            {
                if (p.Length < 3) throw new ConsoleException("Usage: sfx test <kind or cue>  (see 'sfx list')");
                if (Enum.TryParse<VFX.SfxCue>(p[2], true, out var cue)) { VFX.Sfx.Cue(cue, null, 1.4f); return $"Cue {cue}."; }
                if (Enum.TryParse<VFX.SfxKind>(p[2], true, out var kind)) { VFX.Sfx.Play(kind); return $"Sound {kind}."; }
                throw new ConsoleException($"Unknown sound '{p[2]}'. Type 'sfx list'.");
            }
            default: throw new ConsoleException($"Unknown sfx subcommand '{p[1]}'.");
        }
        ModConfig.Save();
        return $"sfx: enabled={d.SfxEnabled} volume={(int)(d.SfxVolume * 100)}% cinematic={(int)(d.CinematicVolume * 100)}%.";
    }

    private string CmdArena(string[] p)
    {
        if (p.Length < 2 || p[1].ToLowerInvariant() != "reset") throw new ConsoleException("Usage: arena reset");
        return Arena.Reset();
    }

    private string CmdCast(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: cast <ability_id>");
        if (!CharacterRegistry.IsFreeplay && !CharacterRegistry.InLobby)
            throw new ConsoleException("cast only works in freeplay / practice.");
        var ability = CharacterRegistry.Ability(p[1]) ?? throw new ConsoleException($"Unknown ability '{p[1]}'. Use 'character abilities' to list ids.");
        var lp = PlayerControl.LocalPlayer ?? throw new ConsoleException("No local player.");
        PlayerControl? target = null;
        if (ability.Targeting != Abilities.TargetMode.None) target = Players.ClosestTarget(lp, 99f);
        if (ability.Targeting is Abilities.TargetMode.Required or Abilities.TargetMode.Menu && target == null)
            throw new ConsoleException($"{ability.Name} needs a target: spawn a dummy first ('practice spawn_dummy').");
        ability.ResetCooldown();
        return ability.TryActivate(lp, target) ? $"Cast {ability.Name}." : $"{ability.Name} could not be cast right now (disabled, dead or its own rule refused).";
    }

    private string CmdTune(string[] p)
    {
        if (p.Length < 2) throw new ConsoleException("Usage: tune cooldowns <percent> | tune reset");
        switch (p[1].ToLowerInvariant())
        {
            case "cooldowns": ModConfig.Data.CooldownScale = Math.Clamp(ParseInt(p, 2), 0, 500); break;
            case "reset":     ModConfig.Data.CooldownScale = 100; ModConfig.Data.AnimationSpeed = 1f; ModConfig.Data.ShakeStrength = 100; break;
            default: throw new ConsoleException($"Unknown tune subcommand '{p[1]}'.");
        }
        ModConfig.Save();
        return $"Cooldowns at {ModConfig.Data.CooldownScale}% of normal.";
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

using System.Collections.Generic;
using AmongUs.GameOptions;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Loki;

// ── G: Mind ───────────────────────────────────────────────────────────────────
// POSSESS a crewmate (play as them: STOP / FRAME KILL / KILL HOST),
// CONTRACT a crewmate into an impostor "employee", BETRAY your employees from anywhere.

/// <summary>Contracted employees per Loki (same on every client, since casts run everywhere).</summary>
public static class Employees
{
    private static readonly Dictionary<byte, HashSet<byte>> ByBoss = new();

    public static void Add(PlayerControl boss, PlayerControl employee)
    {
        if (!ByBoss.TryGetValue(boss.PlayerId, out var set)) ByBoss[boss.PlayerId] = set = new HashSet<byte>();
        set.Add(employee.PlayerId);
    }

    public static bool IsEmployee(PlayerControl pc)
    {
        foreach (var set in ByBoss.Values)
            if (set.Contains(pc.PlayerId)) return true;
        return false;
    }

    public static List<PlayerControl> LivingOf(PlayerControl boss)
    {
        var list = new List<PlayerControl>();
        if (!ByBoss.TryGetValue(boss.PlayerId, out var set)) return list;
        foreach (var id in set)
        {
            var pc = Players.ById(id);
            if (Players.IsAlive(pc)) list.Add(pc!);
        }
        return list;
    }

    public static void Remove(PlayerControl employee)
    {
        foreach (var set in ByBoss.Values) set.Remove(employee.PlayerId);
    }

    public static bool BelongsTo(PlayerControl boss, PlayerControl employee) =>
        ByBoss.TryGetValue(boss.PlayerId, out var set) && set.Contains(employee.PlayerId);

    public static void Clear() => ByBoss.Clear();
}

/// <summary>Active possessions: Loki's body is left behind as an illusion while he walks inside the host.</summary>
public static class Possessions
{
    private sealed class State
    {
        public PlayerControl Master = null!;
        public PlayerControl Host = null!;
        public Vector2 Anchor;
        public LokiIllusion? Shell;
        public float EndsAt;
    }

    private static readonly Dictionary<byte, State> ByMaster = new();

    public static PlayerControl? HostOf(PlayerControl master) =>
        ByMaster.TryGetValue(master.PlayerId, out var s) ? s.Host : null;

    public static bool IsPossessing(PlayerControl master) => ByMaster.ContainsKey(master.PlayerId);

    public static bool IsPossessed(PlayerControl pc)
    {
        foreach (var s in ByMaster.Values)
            if (s.Host == pc) return true;
        return false;
    }

    public static void Begin(PlayerControl master, PlayerControl host, Vector2 anchor, float duration)
    {
        End(master, teleportBack: false);

        // Loki's body stays where he was (an illusion), the real Loki becomes an invisible passenger
        var shell = IllusionManager.Instance?.SpawnIllusion(master, anchor, quiet: true);
        if (shell != null)
        {
            shell.SetBehavior(IllusionBehavior.Idle);
            shell.Untouchable = true;
            shell.Lifetime = duration + 2f;
        }
        StatusManager.SetHidden(master, true);
        Players.Teleport(master, host.transform.position);

        // the host's body now copies the passenger's movement exactly
        StatusManager.Instance?.ApplyStatus(host, StatusEffect.Possessed, duration, Vector2.zero, master.PlayerId);
        ByMaster[master.PlayerId] = new State { Master = master, Host = host, Anchor = anchor, Shell = shell, EndsAt = Time.time + duration };

        // completely silent for everyone else: no beam, no aura, no sound
        if (master == PlayerControl.LocalPlayer)
            Fx.Toast($"POSSESSING {host.Data?.PlayerName?.ToUpperInvariant()}", Fx.LokiGreen, 2f, 3.6f);
    }

    public static void End(PlayerControl master, bool teleportBack = true)
    {
        if (!ByMaster.TryGetValue(master.PlayerId, out var s)) return;
        ByMaster.Remove(master.PlayerId);
        if (s.Host != null) StatusManager.Instance?.RemoveStatus(s.Host, StatusEffect.Possessed);
        if (s.Shell != null) s.Shell.Vanish();
        if (master != null)
        {
            if (teleportBack && MeetingHud.Instance == null) Players.Teleport(master, s.Anchor);
            StatusManager.SetHidden(master, false);
        }
    }

    /// <summary>Every frame on every client: time limits, dead hosts, meetings.</summary>
    public static void Tick()
    {
        if (ByMaster.Count == 0) return;
        var ending = new List<PlayerControl>();
        foreach (var s in ByMaster.Values)
            if (Time.time >= s.EndsAt || !Players.IsAlive(s.Host) || !Players.IsAlive(s.Master) || MeetingHud.Instance != null)
                ending.Add(s.Master);
        foreach (var m in ending) End(m, MeetingHud.Instance == null);
    }

    public static void EndAll()
    {
        foreach (var m in new List<PlayerControl>(GetMasters())) End(m, teleportBack: false);
    }

    private static IEnumerable<PlayerControl> GetMasters()
    {
        foreach (var s in ByMaster.Values) yield return s.Master;
    }
}

/// <summary>Jump into a crewmate's body and play as them.</summary>
public class Possession : AbilityBase
{
    public override string Id       => "possession";
    public override string Name     => "Possession";
    public override string Category => "Mind";
    public override string Label    => "POSSESS";
    public override TargetMode Targeting => TargetMode.Menu;
    public override bool Silent => true;

    public override bool CanUse(PlayerControl caster) => LobbyBehaviour.Instance == null && !Possessions.IsPossessing(caster);

    public override bool IsValidTarget(PlayerControl caster, PlayerControl target)
    {
        bool ok = target.Data?.Role != null && !target.Data.Role.IsImpostor && !Employees.IsEmployee(target);
        if (!ok && caster == PlayerControl.LocalPlayer)
            Fx.Toast("YOU CAN'T POSSESS IMPOSTORS OR YOUR OWN EMPLOYEES", Fx.AlarmRed, 2f, 3.2f);
        return ok;
    }

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        Possessions.Begin(ctx.Caster, ctx.Target, ctx.Point, ctx.Cfg.Duration);
    }
}

/// <summary>Turn a crewmate into an impostor employee.</summary>
public class LokiContract : AbilityBase
{
    public override string Id       => "loki_contract";
    public override string Name     => "Loki's Contract";
    public override string Category => "Mind";
    public override string Label    => "CONTRACT";
    public override TargetMode Targeting => TargetMode.Menu;

    public override bool CanUse(PlayerControl caster) => LobbyBehaviour.Instance == null && !Possessions.IsPossessing(caster);

    public override bool IsValidTarget(PlayerControl caster, PlayerControl target)
    {
        bool ok = target.Data?.Role != null && !target.Data.Role.IsImpostor;
        if (!ok && caster == PlayerControl.LocalPlayer)
            Fx.Toast("THEY ALREADY WORK FOR THE DARK SIDE", Fx.AlarmRed, 2f, 3.2f);
        return ok;
    }

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || target.Data?.Role == null || target.Data.Role.IsImpostor) return;

        Fx.Beam(ctx.CasterPos, target.transform.position, Fx.LokiGold, 0.3f, 0.6f);
        Fx.Sparkles(target.transform.position, Fx.LokiGold, 16, 1.2f);
        Sfx.Play(SfxKind.Chime, 0.6f, target.transform.position);

        RoleManager.Instance.SetRole(target, RoleTypes.Impostor);
        Employees.Add(ctx.Caster, target);

        if (ctx.LocalIs(target))
        {
            Fx.ScreenTint(new Color(0.9f, 0.75f, 0.2f, 0.35f), 2.5f);
            Fx.Toast("YOU SIGNED LOKI'S CONTRACT. YOU ARE NOW AN IMPOSTOR.", Fx.LokiGold, 4f, 3.6f);
        }
        if (ctx.LocalIs(ctx.Caster))
            Fx.Toast($"{target.Data.PlayerName?.ToUpperInvariant()} NOW WORKS FOR YOU", Fx.LokiGold, 2f, 3.6f);
    }
}

/// <summary>Pick one of your employees from the menu and kill them, wherever they are.</summary>
public class UltimateBetrayal : AbilityBase
{
    public override string Id       => "ultimate_betrayal";
    public override string Name     => "The Ultimate Betrayal";
    public override string Category => "Mind";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Menu;

    public override string Label
    {
        get
        {
            var lp = PlayerControl.LocalPlayer;
            int n = lp != null ? Employees.LivingOf(lp).Count : 0;
            return n > 0 ? $"BETRAY ({n})" : "BETRAY";
        }
    }

    public override bool CanUse(PlayerControl caster) =>
        LobbyBehaviour.Instance == null && !Possessions.IsPossessing(caster) && Employees.LivingOf(caster).Count > 0;

    public override bool IsValidTarget(PlayerControl caster, PlayerControl target)
    {
        bool ok = Employees.BelongsTo(caster, target);
        if (!ok && caster == PlayerControl.LocalPlayer)
            Fx.Toast("THAT'S NOT ONE OF YOUR EMPLOYEES", Fx.AlarmRed, 2f, 3.2f);
        return ok;
    }

    protected override void OnCast(CastContext ctx)
    {
        var e = ctx.Target;
        if (e == null || !Employees.BelongsTo(ctx.Caster, e)) return;
        Employees.Remove(e);
        Vector2 p = e.transform.position;
        Sfx.Play(SfxKind.Snap, 1f, p);
        Fx.Burst(p, Fx.LokiGold, 1.6f, 0.4f);
        Fx.Fragments(p, Fx.LokiGold, 40, 1.4f, 1f);
        if (ctx.LocalIs(e)) Fx.Toast("\"NOTHING PERSONAL.\" - LOKI", Fx.LokiGold, 3f, 4f);
        Kills.Kill(ctx.Caster, e, leaveBody: true, checkShield: false);
    }
}

// ── buttons shown while possessing ──

public class PossessStop : AbilityBase
{
    public override string Id       => "possess_stop";
    public override string Name     => "Stop Possessing";
    public override string Category => "Possessing";
    public override string Label    => "STOP";
    public override bool Silent => true;

    public override bool CanUse(PlayerControl caster) => Possessions.IsPossessing(caster);
    protected override void OnCast(CastContext ctx) => Possessions.End(ctx.Caster);
}

/// <summary>The possessed body kills someone nearby — it's their kill, not yours.</summary>
public class PossessFrameKill : AbilityBase
{
    public override string Id       => "possess_frame";
    public override string Name     => "Frame Kill";
    public override string Category => "Possessing";
    public override string Label    => "FRAME KILL";
    public override bool Silent => true;

    private static PlayerControl? Victim(PlayerControl caster, float range)
    {
        var host = Possessions.HostOf(caster);
        if (host == null) return null;
        PlayerControl? best = null;
        float bestD = range;
        foreach (var pc in Players.All())
        {
            if (pc == host || pc == caster || !Players.IsAlive(pc)) continue;
            float d = Vector2.Distance(host.transform.position, pc.transform.position);
            if (d < bestD && !Players.WallBetween(host.transform.position, pc.transform.position)) { best = pc; bestD = d; }
        }
        return best;
    }

    public override bool CanUse(PlayerControl caster) => Victim(caster, Cfg.Range) != null;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => target = Victim(caster, Cfg.Range);

    protected override void OnCast(CastContext ctx)
    {
        var host = Possessions.HostOf(ctx.Caster);
        if (host == null || ctx.Target == null) return;
        Kills.Kill(host, ctx.Target, leaveBody: true);
    }
}

/// <summary>Kill the body you're possessing.</summary>
public class PossessKillHost : AbilityBase
{
    public override string Id       => "possess_kill";
    public override string Name     => "Kill Host";
    public override string Category => "Possessing";
    public override string Label    => "KILL HOST";
    public override bool Silent => true;

    public override bool CanUse(PlayerControl caster) => Possessions.IsPossessing(caster);

    protected override void OnCast(CastContext ctx)
    {
        var host = Possessions.HostOf(ctx.Caster);
        if (host == null) return;
        Possessions.End(ctx.Caster);
        Kills.Kill(ctx.Caster, host, leaveBody: true);
    }
}

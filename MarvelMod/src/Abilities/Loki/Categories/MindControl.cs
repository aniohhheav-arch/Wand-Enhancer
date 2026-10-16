using System.Collections.Generic;
using AmongUs.GameOptions;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── G: Mind (scepter control) ────────────────────────────────────────────────

/// <summary>Who each Loki has possessed (same on every client, since casts run everywhere).</summary>
public static class Thralls
{
    private static readonly Dictionary<byte, HashSet<byte>> ByMaster = new();

    public static void Add(PlayerControl master, PlayerControl thrall)
    {
        if (!ByMaster.TryGetValue(master.PlayerId, out var set)) ByMaster[master.PlayerId] = set = new HashSet<byte>();
        set.Add(thrall.PlayerId);
    }

    public static List<PlayerControl> LivingOf(PlayerControl master)
    {
        var list = new List<PlayerControl>();
        if (!ByMaster.TryGetValue(master.PlayerId, out var set)) return list;
        foreach (var id in set)
        {
            var pc = Players.ById(id);
            if (Players.IsAlive(pc)) list.Add(pc!);
        }
        return list;
    }

    /// <summary>New game / lobby: forget everyone.</summary>
    public static void Clear() => ByMaster.Clear();
}

/// <summary>For a few seconds the chosen player's body copies Loki's movement.</summary>
public class MindControl : AbilityBase
{
    public override string Id       => "mind_control";
    public override string Name     => "Mind Control";
    public override string Category => "Mind";
    public override string Label    => "CONTROL";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Fx.Beam(ctx.CasterPos, target.transform.position, Fx.LokiGreen, 0.25f, 0.5f);
        Fx.Sparkles(target.transform.position, Fx.LokiGreen, 10, 1f);
        Sfx.Play(SfxKind.Creak, 1f, target.transform.position);
        var offset = (Vector2)target.transform.position - ctx.CasterPos;
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Possessed, ctx.Cfg.Duration, offset, ctx.Caster.PlayerId);
        if (ctx.LocalIs(ctx.Caster)) Fx.Toast($"CONTROLLING {target.Data?.PlayerName?.ToUpperInvariant()}", Fx.LokiGreen, 1.8f, 3.4f);
    }
}

/// <summary>A crewmate secretly becomes an impostor bound to Loki.</summary>
public class Possession : AbilityBase
{
    public override string Id       => "possession";
    public override string Name     => "Possession";
    public override string Category => "Mind";
    public override string Label    => "POSSESS";
    public override TargetMode Targeting => TargetMode.Menu;

    public override bool CanUse(PlayerControl caster) => LobbyBehaviour.Instance == null;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || target.Data == null || target.Data.Role == null || target.Data.Role.IsImpostor) return;

        Fx.Beam(ctx.CasterPos, target.transform.position, new Color(0.35f, 0.75f, 1f, 1f), 0.3f, 0.6f);
        Fx.Aura(target.transform, Sprites.Glow, new Color(0.3f, 1f, 0.4f, 0.8f), 0.4f, 2.5f, 0f, 0.3f);   // glowing eyes
        Sfx.Play(SfxKind.Chime, 0.6f, target.transform.position);

        RoleManager.Instance.SetRole(target, RoleTypes.Impostor);
        Thralls.Add(ctx.Caster, target);

        if (ctx.LocalIs(target))
        {
            Fx.ScreenTint(new Color(0.2f, 0.9f, 0.35f, 0.4f), 2.5f);
            Fx.Toast("THE SCEPTER HAS TURNED YOU. YOU ARE NOW AN IMPOSTOR.", Fx.LokiGreen, 4f, 3.6f);
        }
        if (ctx.LocalIs(ctx.Caster))
            Fx.Toast($"{target.Data.PlayerName?.ToUpperInvariant()} IS YOURS", Fx.LokiGold, 2f, 3.6f);
    }
}

/// <summary>Kill every possessed player, anywhere on the map.</summary>
public class SeverTheStrings : AbilityBase
{
    public override string Id       => "sever_the_strings";
    public override string Name     => "Sever the Strings";
    public override string Category => "Mind";
    public override string Label    => "SEVER";

    public override bool CanUse(PlayerControl caster) => Thralls.LivingOf(caster).Count > 0;

    protected override void OnCast(CastContext ctx)
    {
        Sfx.Play(SfxKind.Snap, 1f, ctx.CasterPos);
        foreach (var thrall in Thralls.LivingOf(ctx.Caster))
        {
            Vector2 p = thrall.transform.position;
            // threads snap upward, the body crumbles into fragments
            for (int i = 0; i < 3; i++)
            {
                var t = Fx.MakeSprite(Sprites.Bar, p + new Vector2(-0.2f + i * 0.2f, 1.2f), Fx.LokiLight, 1f);
                t.transform.rotation = Quaternion.Euler(0, 0, 90f);
                var a0 = p + new Vector2(-0.2f + i * 0.2f, 1.2f);
                Fx.Animate(t, 0.6f, k =>
                {
                    t.transform.position = new Vector3(a0.x, a0.y + k * 2f, a0.y / 1000f - 0.6f);
                    t.transform.localScale = new Vector3(2f * (1f - k), 0.04f, 1f);
                });
            }
            Fx.Fragments(p, Fx.LokiGreen, 40, 1.4f, 1f);
            Sfx.Play(SfxKind.Bell, 0.9f, p);
            if (ctx.LocalIs(thrall)) Fx.Toast("LOKI CUT YOUR STRINGS.", Fx.LokiGreen, 3f, 4f);
            Kills.Kill(ctx.Caster, thrall, leaveBody: true);
        }
    }
}

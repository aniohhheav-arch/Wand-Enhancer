using System.Collections;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── F: Executions (real kills with finishers) ────────────────────────────────
// Kills are disabled in the lobby and during meetings; Mystic Shield blocks one kill.

/// <summary>Blink behind the target, three stabs, then the kill.</summary>
public class Backstab : AbilityBase
{
    public override string Id       => "backstab";
    public override string Name     => "Backstab";
    public override string Category => "Executions";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || !Kills.CanKill(ctx.Caster, target)) return;
        Kills.Finisher(Run(ctx, target));
    }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Stunned, 1.2f);
        Vector2 tp = target.transform.position;
        var behind = tp + (tp - ctx.CasterPos).normalized * 0.45f;
        VfxPlayer.PlayLocal(VfxType.LokiSmoke, ctx.CasterPos);
        Players.Teleport(ctx.Caster, behind);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, behind);
        yield return Kills.Wait(0.2f);

        for (int i = 0; i < 3; i++)
        {
            if (!Players.IsAlive(target)) yield break;
            Vector2 p = target.transform.position;
            Slash(p, i % 2 == 0 ? 35f : -35f);
            Fx.BloodSplatter(p, 0.45f, p - behind);
            Fx.Shake(0.12f, 0.3f);
            yield return Kills.Wait(0.16f);
        }
        Kills.Kill(ctx.Caster, target, 1.8f, (Vector2)target.transform.position - behind);
    }

    internal static void Slash(Vector2 p, float angle)
    {
        var slash = Fx.MakeSprite(Sprites.Ring, p, new Color(0.8f, 1f, 0.85f, 1f), 0.5f);
        Fx.Animate(slash, 0.18f, k =>
        {
            slash.transform.localScale = new Vector3(1.5f, 0.3f, 1f) * (0.6f + k * 0.6f);
            slash.transform.rotation = Quaternion.Euler(0, 0, angle - 50f + 100f * k);
            slash.color = new Color(0.8f, 1f, 0.85f, 1f - k);
        });
    }
}

/// <summary>Blast from Loki's scepter: the target is lifted in blue light, then bursts.</summary>
public class ScepterBlast : AbilityBase
{
    public override string Id       => "scepter_blast";
    public override string Name     => "Scepter Blast";
    public override string Category => "Executions";
    public override string Label    => "SCEPTER";
    public override TargetMode Targeting => TargetMode.Required;

    private static readonly Color MindBlue = new(0.35f, 0.75f, 1f, 1f);

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || !Kills.CanKill(ctx.Caster, target)) return;
        Kills.Finisher(Run(ctx, target));
    }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Stunned, 1.5f);
        bool arrived = false;
        Fx.Projectile(ctx.CasterPos, target.transform.position, MindBlue, 18f, () => arrived = true);
        while (!arrived) yield return null;
        if (!Players.IsAlive(target)) yield break;

        var aura = Fx.Aura(target.transform, Sprites.Glow, new Color(0.35f, 0.75f, 1f, 0.8f), 1.6f, 0.7f);
        Fx.Sparkles(target.transform.position, MindBlue, 14, 1.3f, 0.6f);
        yield return Kills.Wait(0.6f);
        if (aura != null) Object.Destroy(aura);
        Fx.Burst(target.transform.position, MindBlue, 2.6f, 0.45f);
        Kills.Kill(ctx.Caster, target, 2.6f);
    }
}

/// <summary>A serpent bite: the target is poisoned and dies a few seconds later.</summary>
public class SerpentStrike : AbilityBase
{
    public override string Id       => "serpent_strike";
    public override string Name     => "Serpent Strike";
    public override string Category => "Executions";
    public override string Label    => "SERPENT";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || !Kills.CanKill(ctx.Caster, target)) return;
        if (StatusManager.Instance != null && StatusManager.Instance.ConsumeShield(target)) return;
        Kills.Finisher(Run(ctx, target));
    }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        Fx.Burst(target.transform.position, Fx.LokiGreen, 1.2f, 0.25f);
        Fx.BloodSplatter(target.transform.position, 0.4f);
        if (target == PlayerControl.LocalPlayer)
        {
            Fx.Toast("POISONED BY LOKI'S SERPENT!", Fx.LokiGreen, 2f, 3.5f);
            Fx.ScreenTint(new Color(0.2f, 0.9f, 0.2f, 0.35f), ctx.Cfg.Duration, pulses: 3);
        }
        var aura = Fx.Aura(target.transform, Sprites.Glow, new Color(0.3f, 1f, 0.3f, 0.55f), 1.4f, ctx.Cfg.Duration + 0.2f);
        for (float t = 0; t < ctx.Cfg.Duration; t += 0.4f)
        {
            if (!Players.IsAlive(target) || MeetingHud.Instance != null)
            {
                if (aura != null) Object.Destroy(aura);
                yield break;
            }
            Fx.Smoke(target.transform.position, Fx.LokiGreen, 2, 0.6f);
            yield return Kills.Wait(0.4f);
        }
        if (aura != null) Object.Destroy(aura);
        Kills.Kill(ctx.Caster, target, 1.6f);
    }
}

/// <summary>Three Lokis appear around the target and strike at once.</summary>
public class IllusionAmbush : AbilityBase
{
    public override string Id       => "illusion_ambush";
    public override string Name     => "Illusion Ambush";
    public override string Category => "Executions";
    public override string Label    => "AMBUSH";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || !Kills.CanKill(ctx.Caster, target)) return;
        Kills.Finisher(Run(ctx, target));
    }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Stunned, 1.4f);
        Vector2 tp = target.transform.position;
        var clones = new System.Collections.Generic.List<LokiIllusion>();
        for (int i = 0; i < 3; i++)
        {
            float a = i * Mathf.PI * 2f / 3f;
            var spot = tp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.1f;
            var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
            ill.SetBehavior(IllusionBehavior.Follow, target);
            ill.Lifetime = 3f;
            clones.Add(ill);
        }
        yield return Kills.Wait(0.7f);
        if (!Players.IsAlive(target)) yield break;

        foreach (var c in clones)
        {
            if (c == null) continue;
            Vector2 cp = c.transform.position;
            Backstab.Slash(Vector2.Lerp(cp, target.transform.position, 0.6f), Random.Range(-60f, 60f));
            Fx.BloodSplatter(target.transform.position, 0.5f, (Vector2)target.transform.position - cp);
            c.Pop();
            yield return Kills.Wait(0.08f);
        }
        Kills.Kill(ctx.Caster, target, 2f);
    }
}

/// <summary>Ultimate: golden chains lift the target, then a slam.</summary>
public class RoyalExecution : AbilityBase
{
    public override string Id       => "royal_execution";
    public override string Name     => "Royal Execution";
    public override string Category => "Executions";
    public override string Label    => "EXECUTE";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || !Kills.CanKill(ctx.Caster, target)) return;
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Bound, ctx.Cfg.CinematicDur + 1.5f);
        CinematicPlayer.PlayUltimate(CinematicType.HallOfLoki, ctx.Caster, () => Kills.Finisher(Run(ctx, target)));
    }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        if (!Players.IsAlive(target)) yield break;
        // rising golden rings
        for (int i = 0; i < 4; i++)
        {
            Fx.Aura(target.transform, Sprites.Ring, new Color(1f, 0.8f, 0.2f, 1f), 1.4f - i * 0.2f, 1.2f, 200f, 0.2f + i * 0.25f);
            yield return Kills.Wait(0.12f);
        }
        yield return Kills.Wait(0.6f);
        if (!Players.IsAlive(target)) yield break;
        Vector2 p = target.transform.position;
        Fx.Burst(p, Fx.LokiGold, 3.5f, 0.5f);
        Fx.Shake(0.6f, 1f);
        Kills.Kill(ctx.Caster, target, 3.2f);
        if (ctx.LocalIs(ctx.Caster) || ctx.LocalIs(target))
            Fx.Toast("EXECUTED", Fx.AlarmRed, 1.8f, 6f, 0.5f);
    }
}

/// <summary>Hidden ability: click the map to teleport (no cooldown).</summary>
public class MapTeleport : AbilityBase
{
    public override string Id       => "map_teleport";
    public override string Name     => "Map Teleport";
    public override string Category => "Hidden";

    /// <summary>Set by MapTeleportInput right before TryActivate.</summary>
    public Vector2 PendingDestination { get; set; }

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = PendingDestination;

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, ctx.CasterPos);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, ctx.Point);
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

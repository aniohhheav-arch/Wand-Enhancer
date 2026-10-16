using MarvelMod.Characters;
using UnityEngine;
using MarvelMod.VFX;

namespace MarvelMod.Abilities.Loki;

// ── B: Shapeshifting ──────────────────────────────────────────────────────────

/// <summary>Take the target's look (keep your own name).</summary>
public class Disguise : AbilityBase
{
    public override string Id       => "disguise";
    public override string Name     => "Disguise";
    public override string Category => "Shapeshifting";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
        ShapeshiftManager.Instance!.Shapeshift(ctx.Caster, ctx.Target, ctx.Cfg.Duration);
    }
}

/// <summary>Take the target's look AND name.</summary>
public class PerfectImpersonation : AbilityBase
{
    public override string Id       => "perfect_impersonation";
    public override string Name     => "Perfect Impersonation";
    public override string Category => "Shapeshifting";
    public override string Label    => "MIMIC";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // a mirror-flash reveal: a gold seam sweeps up the body and the face changes in the flash
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
        Fx.Beam(ctx.CasterPos + new Vector2(-0.6f, -0.4f), ctx.CasterPos + new Vector2(0.6f, 0.5f), Fx.LokiGold, 0.1f, 0.3f);
        ShapeshiftManager.Instance!.Shapeshift(ctx.Caster, ctx.Target, ctx.Cfg.Duration, copyNameplate: true);
    }
}

/// <summary>Shrink into a tiny creature — harder to spot.</summary>
public class CreatureForm : AbilityBase
{
    public override string Id       => "creature_form";
    public override string Name     => "Creature Form";
    public override string Category => "Shapeshifting";
    public override string Label    => "CREATURE";

    protected override void OnCast(CastContext ctx)
    {
        // cartoon shrink: puff, squeak, tiny Loki
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
        Fx.Release(ctx.CasterPos, Fx.LokiGreen, ImpactStyle.Comedy, 0.7f);
        Sfx.Play(SfxKind.Boing, 1.8f, ctx.CasterPos, 0.5f);
        ShapeshiftManager.Instance!.ApplyCreatureForm(ctx.Caster, ctx.Cfg.Duration);
    }
}

/// <summary>You and the target swap looks and names.</summary>
public class IdentitySwap : AbilityBase
{
    public override string Id       => "identity_swap";
    public override string Name     => "Identity Swap";
    public override string Category => "Shapeshifting";
    public override string Label    => "LOOK SWAP";
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // two green threads cross between them, then both faces flip at once
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.Target.transform.position);
        Fx.Tendril(ctx.CasterPos, ctx.Target.transform.position, Fx.LokiGreen, 0.5f);
        Fx.Tendril(ctx.Target.transform.position, ctx.CasterPos, Fx.LokiGold, 0.5f);
        ShapeshiftManager.Instance!.SwapAppearances(ctx.Caster, ctx.Target, ctx.Cfg.Duration);
    }
}

/// <summary>Ultimate: become the target while a fake copy of them walks elsewhere.</summary>
public class TheOtherYou : AbilityBase
{
    public override string Id       => "the_other_you";
    public override string Name     => "The Other You";
    public override string Label    => "OTHER YOU";
    public override string Category => "Shapeshifting";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Menu;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        CinematicPlayer.PlayUltimate(CinematicType.TheOtherYou, ctx.Caster, () =>
        {
            // the target's reflection steps out of Loki and walks off on its own
            Fx.Release(ctx.Caster.transform.position, Fx.LokiGreen, ImpactStyle.Glitch, 1.2f);
            ShapeshiftManager.Instance!.Shapeshift(ctx.Caster, target, ctx.Cfg.Duration, copyNameplate: true);
            var spot = MapSpawnPoints.GetRandom(1, ctx.Rng, ctx.CasterPos)[0];
            VfxPlayer.PlayLocal(VfxType.LokiSpawn, spot);
            var ill = IllusionManager.Instance!.SpawnIllusion(target, spot);
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = ctx.Cfg.Duration;
        });
    }
}

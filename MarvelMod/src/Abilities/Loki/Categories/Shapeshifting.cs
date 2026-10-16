using MarvelMod.Characters;
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
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
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
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
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
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.CasterPos);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, ctx.Target.transform.position);
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
            ShapeshiftManager.Instance!.Shapeshift(ctx.Caster, target, ctx.Cfg.Duration, copyNameplate: true);
            var spot = MapSpawnPoints.GetRandom(1, ctx.Rng, ctx.CasterPos)[0];
            var ill = IllusionManager.Instance!.SpawnIllusion(target, spot);
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = ctx.Cfg.Duration;
        });
    }
}

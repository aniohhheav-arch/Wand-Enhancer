using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── B: Shapeshifting ──────────────────────────────────────────────────────────

public class Disguise : AbilityBase
{
    public override string Id       => "disguise";
    public override string Name     => "Disguise";
    public override string Category => "Shapeshifting";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        ShapeshiftManager.Instance.Shapeshift(caster, target, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, caster.transform.position);
    }
}

public class PerfectImpersonation : AbilityBase
{
    public override string Id       => "perfect_impersonation";
    public override string Name     => "Perfect Impersonation";
    public override string Category => "Shapeshifting";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        // Copies appearance AND nameplate (local cosmetic only — never rewrites server role)
        ShapeshiftManager.Instance.Shapeshift(caster, target, Cfg.Duration, copyNameplate: true);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, caster.transform.position);
    }
}

public class CreatureForm : AbilityBase
{
    public override string Id       => "creature_form";
    public override string Name     => "Creature Form";
    public override string Category => "Shapeshifting";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        ShapeshiftManager.Instance.ApplyCreatureForm(caster, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, caster.transform.position);
    }
}

public class IdentitySwap : AbilityBase
{
    public override string Id       => "identity_swap";
    public override string Name     => "Identity Swap";
    public override string Category => "Shapeshifting";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        // Swaps cosmetic appearance of two players locally — does NOT touch roles or tasks
        ShapeshiftManager.Instance.SwapAppearances(caster, target, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, caster.transform.position);
        VfxPlayer.PlayLocal(VfxType.LokiShapeshift, target.transform.position);
    }
}

public class TheOtherYou : AbilityBase
{
    public override string Id       => "the_other_you";
    public override string Name     => "The Other You";
    public override string Category => "Shapeshifting";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        CinematicPlayer.PlayUltimate(CinematicType.TheOtherYou, caster, () =>
        {
            // Loki takes target's appearance
            ShapeshiftManager.Instance.Shapeshift(caster, target, Cfg.Duration, copyNameplate: true);
            // Spawn a convincing illusion of the target elsewhere on the map
            var pos = MapSpawnPoints.GetRandom(1)[0];
            var ill = IllusionManager.Instance.SpawnIllusion(target, pos);
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = Cfg.Duration;
        });
    }
}

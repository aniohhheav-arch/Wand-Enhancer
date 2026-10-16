using MarvelMod.Characters;
using MarvelMod.UI.HUD;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── C: Trickster Mobility ─────────────────────────────────────────────────────

/// <summary>Short teleport in the direction you're moving (stops at walls).</summary>
public class MysticBlink : AbilityBase
{
    public override string Id       => "mystic_blink";
    public override string Name     => "Mystic Blink";
    public override string Category => "Trickster Mobility";
    public override string Label    => "BLINK";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
    {
        point = Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, Cfg.Range);
    }

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, ctx.CasterPos);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, ctx.Point);
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

/// <summary>Swap places with the target.</summary>
public class SwapTrick : AbilityBase
{
    public override string Id       => "swap_trick";
    public override string Name     => "Swap Trick";
    public override string Category => "Trickster Mobility";
    public override string Label    => "SWAP";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Vector2 casterPos = ctx.Point;             // caster position at cast time (sent by the caster)
        Vector2 targetPos = Players.Pos(target);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, casterPos);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, targetPos);
        Players.Teleport(ctx.Caster, targetPos);   // runs on the caster's client
        Players.Teleport(target, casterPos);       // runs on the target's client
    }
}

/// <summary>Leave a walking decoy behind and vanish to somewhere else on the map.</summary>
public class DecoyEscape : AbilityBase
{
    public override string Id       => "decoy_escape";
    public override string Name     => "Decoy Escape";
    public override string Category => "Trickster Mobility";
    public override string Label    => "DECOY";

    protected override void OnCast(CastContext ctx)
    {
        var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, ctx.CasterPos);
        ill.SetBehavior(IllusionBehavior.Wander);
        ill.Lifetime = ctx.Cfg.Duration;

        var dest = MapSpawnPoints.GetRandom(1, ctx.Rng, ctx.CasterPos)[0];
        VfxPlayer.PlayLocal(VfxType.LokiSmoke, ctx.CasterPos);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, dest);
        Players.Teleport(ctx.Caster, dest);
    }
}

/// <summary>Turn invisible for a few seconds.</summary>
public class ShadowStep : AbilityBase
{
    public override string Id       => "shadow_step";
    public override string Name     => "Shadow Step";
    public override string Category => "Trickster Mobility";
    public override string Label    => "VANISH";

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiSmoke, ctx.CasterPos);
        InvisibilityManager.Instance!.ApplyInvisibility(ctx.Caster, ctx.Cfg.Duration);
    }
}

/// <summary>Ultimate: idle Lokis appear at several spots, the real one is at the last.</summary>
public class MasterOfMisdirection : AbilityBase
{
    public override string Id       => "master_of_misdirection";
    public override string Name     => "Master of Misdirection";
    public override string Category => "Trickster Mobility";
    public override string Label    => "MISDIRECT";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        CinematicPlayer.PlayUltimate(CinematicType.MasterOfMisdirection, ctx.Caster, () =>
        {
            int stops = Mathf.Clamp(ctx.Cfg.MaxTargets, 2, 8);
            var positions = MapSpawnPoints.GetRandom(stops, ctx.Rng, ctx.CasterPos);
            for (int i = 0; i < positions.Count - 1; i++)
            {
                var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, positions[i]);
                ill.SetBehavior(IllusionBehavior.Idle);
                ill.Lifetime = ctx.Cfg.Duration;
                VfxPlayer.PlayLocal(VfxType.LokiTeleport, positions[i]);
            }
            var last = positions[positions.Count - 1];
            VfxPlayer.PlayLocal(VfxType.LokiSmoke, ctx.Caster.transform.position);
            VfxPlayer.PlayLocal(VfxType.LokiTeleport, last);
            Players.Teleport(ctx.Caster, last);
        });
    }
}

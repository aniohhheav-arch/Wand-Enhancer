using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── A: Illusion Magic ─────────────────────────────────────────────────────────

/// <summary>A copy of Loki that follows him around.</summary>
public class MirrorImage : AbilityBase
{
    public override string Id       => "mirror_image";
    public override string Name     => "Mirror Image";
    public override string Category => "Illusion Magic";

    protected override void OnCast(CastContext ctx)
    {
        var spot = MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.6f, 1f);
        var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
        ill.SetBehavior(IllusionBehavior.Follow, ctx.Caster);
        ill.Lifetime = ctx.Cfg.Duration;
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, ctx.CasterPos);
    }
}

/// <summary>A squad of wandering Lokis around the caster.</summary>
public class PhantomSquad : AbilityBase
{
    public override string Id       => "phantom_squad";
    public override string Name     => "Phantom Squad";
    public override string Category => "Illusion Magic";
    public override string Label    => "PHANTOMS";

    protected override void OnCast(CastContext ctx)
    {
        int count = Mathf.Clamp(ctx.Cfg.MaxTargets, 1, 6);
        for (int i = 0; i < count; i++)
        {
            var spot = MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.8f, 2.2f);
            var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = ctx.Cfg.Duration;
        }
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, ctx.CasterPos);
    }
}

/// <summary>Nearby players get a fake reactor alarm on their screen.</summary>
public class FalseEmergency : AbilityBase
{
    public override string Id       => "false_emergency";
    public override string Name     => "False Emergency";
    public override string Category => "Illusion Magic";
    public override string Label    => "FAKE ALARM";

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiMagic, ctx.CasterPos);
        Fx.Burst(ctx.CasterPos, Fx.AlarmRed, ctx.Cfg.Range * 0.6f, 0.6f);

        var me = PlayerControl.LocalPlayer;
        if (me == null || me == ctx.Caster || !Players.IsAlive(me)) return;
        if (Vector2.Distance(Players.Pos(me), ctx.CasterPos) > ctx.Cfg.Range) return;

        // Purely visual on the victim's screen — no real sabotage is triggered.
        Fx.ScreenTint(new Color(1f, 0.05f, 0.05f, 0.45f), ctx.Cfg.Duration, pulses: 4);
        Fx.Toast("REACTOR MELTDOWN!", Fx.AlarmRed, ctx.Cfg.Duration, 5f, 1.6f);
        Fx.Shake(0.5f, 0.2f);
    }
}

/// <summary>The target is trapped in a vision of Lokis closing in.</summary>
public class IllusionTrap : AbilityBase
{
    public override string Id       => "illusion_trap";
    public override string Name     => "Illusion Trap";
    public override string Category => "Illusion Magic";
    public override string Label    => "TRAP";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Fx.Projectile(ctx.CasterPos, Players.Pos(target), Fx.LokiGreen, 14f, () =>
        {
            VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
            StatusManager.Instance?.ApplyStatus(target, StatusEffect.Stunned, 0.6f);

            // Only the victim sees the Lokis surrounding them.
            if (!ctx.LocalIs(target)) return;
            Fx.ScreenTint(new Color(0.1f, 0.8f, 0.25f, 0.35f), ctx.Cfg.Duration, pulses: 3);
            Fx.Shake(0.6f, 0.25f);
            for (int i = 0; i < 3; i++)
            {
                var spot = MapSpawnPoints.NearbyOpenPoint(Players.Pos(target), ctx.Rng, 1.2f, 1.8f);
                var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
                ill.SetBehavior(IllusionBehavior.Follow, target);
                ill.Lifetime = ctx.Cfg.Duration;
            }
        });
    }
}

/// <summary>Ultimate: Lokis everywhere on the map.</summary>
public class HallOfLoki : AbilityBase
{
    public override string Id       => "hall_of_loki";
    public override string Name     => "Hall of Loki";
    public override string Category => "Illusion Magic";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        CinematicPlayer.PlayUltimate(CinematicType.HallOfLoki, ctx.Caster, () =>
        {
            var spots = MapSpawnPoints.GetRandom(ctx.Cfg.MaxTargets, ctx.Rng, ctx.CasterPos);
            foreach (var spot in spots)
            {
                var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
                ill.SetBehavior(IllusionBehavior.Random);
                ill.Lifetime = ctx.Cfg.Duration;
                VfxPlayer.PlayLocal(VfxType.LokiSpawn, spot);
            }
        });
    }
}

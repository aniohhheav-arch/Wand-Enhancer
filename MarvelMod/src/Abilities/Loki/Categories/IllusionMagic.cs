using MarvelMod.Characters;
using Reactor.Utilities;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── A: Illusion Magic ─────────────────────────────────────────────────────────

/// <summary>A copy of Loki that follows him around.</summary>
public class MirrorImage : AbilityBase
{
    public override string Id       => "mirror_image";
    public override string Name     => "Mirror Image";
    public override string Label    => "MIRROR";
    public override string Category => "Illusion Magic";

    protected override void OnCast(CastContext ctx)
    {
        // Loki splits in two: the copy peels off his body with a trail of afterimages
        var spot = MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.6f, 1f);
        var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, ctx.CasterPos, quiet: true);
        ill.MoveTo(spot, 4f);
        ill.Lifetime = ctx.Cfg.Duration;
        Fx.Trail(ill.transform, Fx.LokiGreen, 0.5f);
        Coroutines.Start(Then(ill, ctx.Caster, 0.45f));
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, ctx.CasterPos);
    }

    private static System.Collections.IEnumerator Then(LokiIllusion ill, PlayerControl caster, float after)
    {
        yield return new UnityEngine.WaitForSeconds(after);
        if (ill != null) ill.SetBehavior(IllusionBehavior.Follow, caster);
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
        var spots = new System.Collections.Generic.List<Vector2>();
        for (int i = 0; i < count; i++) spots.Add(MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.8f, 2.2f));
        Coroutines.Start(Squad(ctx, spots));
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, ctx.CasterPos);
    }

    /// <summary>Phantoms step out of gold doorways one after another, like a drumroll.</summary>
    private static System.Collections.IEnumerator Squad(CastContext ctx, System.Collections.Generic.List<Vector2> spots)
    {
        int i = 0;
        foreach (var spot in spots)
        {
            Fx.Tendril(ctx.CasterPos, spot, Fx.LokiGold, 0.3f, 6);
            Fx.ShockRing(spot, Fx.LokiGold, 1.3f, 0.3f, squash: 0.45f);
            Sfx.Play(SfxKind.Pop, 0.8f + i++ * 0.12f, spot, 0.4f);
            var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = ctx.Cfg.Duration;
            yield return new UnityEngine.WaitForSeconds(0.12f);
        }
    }
}

/// <summary>Nearby players get a fake reactor alarm on their screen.</summary>
public class FalseEmergency : AbilityBase
{
    public override string Id       => "false_emergency";
    public override string Name     => "False Emergency";
    public override string Category => "Illusion Magic";
    public override string Label    => "ALARM";

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
        Coroutines.Start(Klaxon(ctx.Cfg.Duration));
    }

    /// <summary>A fake alarm: two-tone klaxon only the victim hears.</summary>
    private static System.Collections.IEnumerator Klaxon(float dur)
    {
        for (float t = 0; t < dur; t += 0.5f)
        {
            if (MeetingHud.Instance != null) yield break;
            Sfx.Play(SfxKind.Bell, ((int)(t * 2f) % 2 == 0) ? 1.4f : 1.1f, null, 0.35f);
            yield return new UnityEngine.WaitForSeconds(0.5f);
        }
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
            Fx.Comic(Players.Pos(target) + new Vector2(0f, 1f), "BOO!", Fx.LokiGold, 1.3f);
            Sfx.Play(SfxKind.Stab, 1.3f, null, 0.5f);
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
    public override string Label    => "HALL";
    public override string Category => "Illusion Magic";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        CinematicPlayer.PlayUltimate(CinematicType.HallOfLoki, ctx.Caster, () =>
        {
            var spots = MapSpawnPoints.GetRandom(ctx.Cfg.MaxTargets, ctx.Rng, ctx.CasterPos);
            Coroutines.Start(Reveal(ctx, spots));
        });
    }

    /// <summary>Lokis pop into existence across the map in a rolling wave, each with a cheeky "HA!".</summary>
    private static System.Collections.IEnumerator Reveal(CastContext ctx, System.Collections.Generic.List<Vector3> spots)
    {
        Fx.Darken(0.2f, 1.5f, new Color(0f, 0.15f, 0.05f));
        int i = 0;
        foreach (var spot in spots)
        {
            var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
            ill.SetBehavior(IllusionBehavior.Random);
            ill.Lifetime = ctx.Cfg.Duration;
            VfxPlayer.PlayLocal(VfxType.LokiSpawn, spot);
            if (i++ % 2 == 0) Fx.Comic((Vector2)spot + new Vector2(0f, 0.9f), "HA!", Fx.LokiGold, 0.9f);
            yield return new UnityEngine.WaitForSeconds(0.08f);
        }
    }
}

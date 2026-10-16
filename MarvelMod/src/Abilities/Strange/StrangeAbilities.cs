using System.Collections;
using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.UI.HUD;
using MarvelMod.VFX;
using Reactor.Utilities;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Strange;

/// <summary>Base for Doctor Strange: orange-gold spell circle at the caster on every cast.</summary>
public abstract class StrangeAbility : AbilityBase
{
    public static readonly Color Gold = new(1f, 0.65f, 0.15f, 1f);
    public static readonly Color Crimson = new(0.9f, 0.12f, 0.15f, 1f);
    public static readonly Color TimeGreen = new(0.25f, 0.95f, 0.45f, 1f);
    public static readonly Color Astral = new(1f, 0.9f, 0.55f, 1f);
    public static readonly Color MirrorBlue = new(0.6f, 0.85f, 1f, 1f);

    public override Color CastColor => Gold;

    /// <summary>Preparation: hand gesture mandalas flare at both hands while a spell circle forms underfoot.</summary>
    protected override void BeforeCast(CastContext ctx)
    {
        foreach (var side in new[] { -1f, 1f })
        {
            var hand = Fx.MakeSprite(Sprites.Runes, ctx.CasterPos + new Vector2(0.42f * side, 0.05f), new Color(Gold.r, Gold.g, Gold.b, 0f), 0.1f, "HandMandala");
            Fx.Animate(hand, 0.55f, k =>
            {
                hand.transform.localScale = Vector3.one * Mathf.Lerp(0.1f, 0.5f, Mathf.Min(1f, k * 3f));
                hand.transform.Rotate(0, 0, side * 420f * Time.deltaTime);
                hand.color = new Color(Gold.r, Gold.g, Gold.b, 0.95f * (k < 0.7f ? 1f : (1f - k) / 0.3f));
            });
        }
        var circle = Fx.MakeSprite(Sprites.Runes, ctx.CasterPos + new Vector2(0f, -0.3f), new Color(Gold.r, Gold.g, Gold.b, 0.9f), 0.2f, "SpellCircle");
        Fx.Animate(circle, 0.7f, k =>
        {
            circle.transform.localScale = new Vector3(1.4f, 0.7f, 1f) * Mathf.Min(1f, k * 4f);
            circle.transform.Rotate(0, 0, 160f * Time.deltaTime);
            circle.color = new Color(Gold.r, Gold.g, Gold.b, 0.9f * (1f - k));
        });
    }

    protected static void Hit(CastContext ctx, PlayerControl pc, Vector2 from, float stun) =>
        DamageSystem.Instance?.ApplyDamage(pc, from, ctx.Cfg.Damage, DamageType.Magical, stun);

    protected static Vector2 Ahead(PlayerControl caster, float dist) =>
        Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, dist);
}

// ══ A: SLING-RING MAGIC ══════════════════════════════════════════════════════

/// <summary>Open a portal where you stand. It waits for Portal Link to connect it.</summary>
public class SlingRing : StrangeAbility
{
    public override string Id => "sling_ring";
    public override string Name => "Sling Ring";
    public override string Category => "Sling Ring";
    public override string Label => "PORTAL A";

    protected override void OnCast(CastContext ctx)
    {
        PortalEngine.OpenPortal(ctx.Caster, ctx.CasterPos, ModConfig.Data.PortalLifetime, linkToPending: false);
        Fx.Release(ctx.CasterPos, Gold, ImpactStyle.Magic, 0.6f);
        if (ctx.LocalIs(ctx.Caster)) Fx.Toast("PORTAL OPEN - WALK SOMEWHERE AND USE PORTAL LINK", Gold, 2.5f, 3f);
    }
}

/// <summary>Open the second portal here and link it to the waiting one.</summary>
public class PortalLink : StrangeAbility
{
    public override string Id => "portal_link";
    public override string Name => "Portal Link";
    public override string Category => "Sling Ring";
    public override string Label => "PORTAL B";

    public override bool CanUse(PlayerControl caster) => PortalEngine.HasPending(caster);

    protected override void OnCast(CastContext ctx)
    {
        PortalEngine.OpenPortal(ctx.Caster, ctx.CasterPos, ModConfig.Data.PortalLifetime, linkToPending: true);
        Fx.Release(ctx.CasterPos, Gold, ImpactStyle.Magic, 0.6f);
        var other = PortalEngine.LatestExit(ctx.Caster);
        if (other.HasValue) Fx.Tendril(ctx.CasterPos, other.Value, Gold, 0.6f, 12);   // the link snaps across
        if (ctx.LocalIs(ctx.Caster)) Fx.Toast("PORTALS LINKED", Gold, 1.5f, 3.5f);
    }
}

/// <summary>Shove the target in front of you through your latest portal (or to a random vent).</summary>
public class PortalRedirect : StrangeAbility
{
    public override string Id => "portal_redirect";
    public override string Name => "Portal Redirect";
    public override string Category => "Sling Ring";
    public override string Label => "REDIRECT";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        var exit = PortalEngine.LatestExit(ctx.Caster) ?? (Vector2)MapSpawnPoints.GetRandom(1, ctx.Rng, ctx.CasterPos)[0];
        var temp = PortalEngine.OpenPair(ctx.Caster, target.transform.position, exit, 1.2f);
        Fx.Release(target.transform.position, Gold, ImpactStyle.Cosmic, 0.8f);
        Fx.Comic((Vector2)target.transform.position + new Vector2(0f, 1f), "WHEEE!", Gold, 1f);
        Fx.Release(exit, Gold, ImpactStyle.Cosmic, 0.8f);
        Players.Teleport(target, Players.SafeDestination(exit, Vector2.down, 0.6f));
    }
}

/// <summary>A trap portal: the first other player to step on it is flung to a random vent.</summary>
public class PortalSnare : StrangeAbility
{
    public override string Id => "portal_snare";
    public override string Name => "Portal Snare";
    public override string Category => "Sling Ring";
    public override string Label => "SNARE";

    protected override void OnCast(CastContext ctx)
    {
        var exit = (Vector2)MapSpawnPoints.GetRandom(1, ctx.Rng, ctx.CasterPos)[0];
        // a trap must stay hidden: only Strange sees a faint sigil where he set it
        if (ctx.LocalIs(ctx.Caster)) Fx.Sigil(ctx.CasterPos, new Color(Gold.r, Gold.g, Gold.b, 0.5f), 0.9f, 0.6f);
        PortalEngine.OpenSnare(ctx.Caster, ctx.CasterPos + AbilityButtons.LastMoveDirection * 0.01f, exit, ctx.Cfg.Duration);
    }
}

/// <summary>Ultimate: everyone near you is pulled through a chain of portals across the map, then returned.</summary>
public class MultiversalGateway : StrangeAbility
{
    public override string Id => "multiversal_gateway";
    public override string Name => "Multiversal Gateway";
    public override string Category => "Sling Ring";
    public override string Label => "GATEWAY";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        CinematicPlayer.Play("MULTIVERSAL GATEWAY", Gold, caster, ctx.Cfg.CinematicDur, () => Coroutines.Start(Run(ctx)));
    }

    private static IEnumerator Run(CastContext ctx)
    {
        Vector2 c = ctx.Caster.transform.position;
        Fx.Darken(0.3f, 4f, new Color(0.08f, 0.03f, 0f));
        Fx.Sigil(c, Gold, 3f, 3f);
        Sfx.Play(SfxKind.Gong, 0.8f, c, 0.7f, cinematic: true);
        var giant = Fx.MakeSprite(Sprites.Runes, c, Gold, 0.2f);
        Fx.Animate(giant, 3.5f, k => { giant.transform.localScale = Vector3.one * Mathf.Min(6f, k * 20f); giant.transform.Rotate(0, 0, 50f * Time.deltaTime); });
        var victims = Players.InRadius(c, ctx.Cfg.Range, 15, ctx.Caster);
        var homes = new List<Vector2>();
        foreach (var v in victims) homes.Add(v.transform.position);
        var stops = MapSpawnPoints.GetRandom(3, ctx.Rng, c);
        foreach (var stop in stops)
        {
            PortalEngine.OpenPair(ctx.Caster, c, stop, 1f);
            Fx.Release(stop, Gold, ImpactStyle.Cosmic, 1f);
            foreach (var v in victims) Players.Teleport(v, (Vector2)stop + Random.insideUnitCircle * 0.3f);
            for (float t = 0; t < 0.9f; t += Time.deltaTime) yield return null;
            if (MeetingHud.Instance != null) yield break;
        }
        for (int i = 0; i < victims.Count; i++)   // returned safely to where they were
            if (Players.IsAlive(victims[i])) Players.Teleport(victims[i], homes[i]);
    }
}

// ══ B: MYSTIC COMBAT ═════════════════════════════════════════════════════════

public class CrimsonBands : StrangeAbility
{
    public override string Id => "crimson_bands";
    public override string Name => "Crimson Bands of Cyttorak";
    public override string Category => "Mystic Combat";
    public override string Label => "BANDS";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Fx.Projectile(ctx.CasterPos, target.transform.position, Crimson, 14f, () =>
        {
            // bands snap shut with a metal clang, three loops cinching one after another
            for (int i = 0; i < 3; i++)
            {
                float a = i * 2.1f;
                Fx.Tendril((Vector2)target.transform.position + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.1f, target.transform.position, Crimson, 0.4f, 8);
            }
            Sfx.Play(SfxKind.Clang, 1.2f, target.transform.position, 0.6f);
            StatusManager.Instance?.ApplyStatus(target, StatusEffect.Bound, ctx.Cfg.Duration);
            Fx.Aura(target.transform, Sprites.Ring, new Color(0.9f, 0.15f, 0.15f, 0.95f), 1.05f, ctx.Cfg.Duration, -220f);
        });
    }
}

/// <summary>A glowing whip that drags the target to you.</summary>
public class EldritchWhip : StrangeAbility
{
    public override string Id => "eldritch_whip";
    public override string Name => "Eldritch Whip";
    public override string Category => "Mystic Combat";
    public override string Label => "WHIP";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        // a crackling gold lash, a whip-crack and a "YOINK"
        Fx.Lightning(ctx.CasterPos, target.transform.position, Gold, 0.3f, 1, 0.2f);
        Fx.Beam(ctx.CasterPos, target.transform.position, Gold, 0.12f, 0.45f);
        Sfx.Play(SfxKind.Slash, 1.2f, ctx.CasterPos);
        Sfx.Play(SfxKind.Snap, 1.6f, target.transform.position, 0.4f);
        Fx.Comic((Vector2)target.transform.position + new Vector2(0f, 1f), "YOINK!", Gold, 1f);
        var pull = Players.SafeDestination(ctx.CasterPos, (Vector2)target.transform.position - ctx.CasterPos, 0.7f);
        Players.Teleport(target, pull);
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Stunned, 0.6f);
    }
}

public class EldritchShield : StrangeAbility
{
    public override string Id => "eldritch_shield";
    public override string Name => "Eldritch Shield";
    public override string Category => "Mystic Combat";
    public override string Label => "SHIELD";

    protected override void OnCast(CastContext ctx)
    {
        StatusManager.Instance?.ApplyStatus(ctx.Caster, StatusEffect.Shielded, ctx.Cfg.Duration);
        Fx.Sigil(ctx.CasterPos + new Vector2(0.5f, 0f), Gold, 1.4f, 0.4f);
        Sfx.Play(SfxKind.Glass, 1.2f, ctx.CasterPos, 0.4f);
        Fx.Aura(ctx.Caster.transform, Sprites.Runes, new Color(1f, 0.7f, 0.2f, 0.8f), 1.5f, ctx.Cfg.Duration, 60f);
    }
}

/// <summary>Blades orbit Strange, then fly at the closest player.</summary>
public class MysticBlades : StrangeAbility
{
    public override string Id => "mystic_blades";
    public override string Name => "Mystic Blades";
    public override string Category => "Mystic Combat";
    public override string Label => "BLADES";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private static IEnumerator Run(CastContext ctx)
    {
        var blades = new List<SpriteRenderer>();
        for (int i = 0; i < 4; i++)
        {
            var b = Fx.MakeSprite(Sprites.Bar, ctx.CasterPos, Gold, 1f, "MysticBlade");
            b.transform.localScale = new Vector3(0.5f, 0.12f, 1f);
            blades.Add(b);
        }
        for (float t = 0; t < 0.8f; t += Time.deltaTime)
        {
            if (ctx.Caster == null) yield break;
            Vector2 c = ctx.Caster.transform.position;
            for (int i = 0; i < blades.Count; i++)
            {
                float a = t * 9f + i * Mathf.PI / 2f;
                var p = c + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 0.7f;
                blades[i].transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
                blades[i].transform.rotation = Quaternion.Euler(0, 0, a * Mathf.Rad2Deg + 90f);
            }
            yield return null;
        }
        foreach (var b in blades) if (b != null) Object.Destroy(b.gameObject);
        Vector2 from = ctx.Caster.transform.position;
        var target = ctx.Target;
        var to = target != null ? (Vector2)target.transform.position : Players.SafeDestination(from, AbilityButtons.LastMoveDirection, ctx.Cfg.Range);
        Sfx.Play(SfxKind.Whoosh, 1.6f, from, 0.5f);
        for (int i = 0; i < 3; i++)
            Fx.Projectile(from + Random.insideUnitCircle * 0.3f, to, Gold, 16f + i * 2f, i == 0 ? () =>
            {
                Fx.Streaks(to, Gold, 6, 0.9f);
                Sfx.Play(SfxKind.Clang, 1.5f, to, 0.4f);
                if (target != null) Hit(ctx, target, from, ctx.Cfg.Duration);
            } : null);
    }
}

/// <summary>Ultimate KILL: bands lift the target inside a rune circle; it closes in golden light.</summary>
public class CrimsonJudgment : StrangeAbility
{
    public override string Id => "crimson_judgment";
    public override string Name => "Crimson Judgment";
    public override string Category => "Mystic Combat";
    public override string Label => "JUDGMENT";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null || !Kills.CanKill(ctx.Caster, target)) return;
        Kills.Finisher(Run(ctx, target));
    }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Bound, 3f);
        var circle = Fx.MakeSprite(Sprites.Runes, (Vector2)target.transform.position + new Vector2(0f, -0.3f), Crimson, 0.2f);
        Fx.Animate(circle, 2.6f, k => { circle.transform.localScale = new Vector3(2.2f, 1.1f, 1f) * Mathf.Min(1f, k * 3f); circle.transform.Rotate(0, 0, 90f * Time.deltaTime); });
        for (float k = 0; k <= 1f; k += 0.08f) { CinematicDirector.Pose(target, k * 0.4f, 0f); yield return Kills.Wait(0.06f); }
        for (int i = 0; i < 3; i++)
        {
            Fx.Aura(target.transform, Sprites.Ring, new Color(0.9f, 0.2f, 0.15f, 0.9f), 1.4f - i * 0.25f, 1.4f, 180f * (i % 2 == 0 ? 1 : -1), 0.4f);
            yield return Kills.Wait(0.15f);
        }
        yield return Kills.Wait(0.6f);
        CinematicDirector.Pose(target, 0f, 0f);
        Fx.Release(target.transform.position, Crimson, ImpactStyle.Magic, 2f, cinematic: true);
        Fx.Flash(new Color(1f, 0.8f, 0.4f, 0.4f), 0.2f);
        Fx.Sparkles(target.transform.position, Astral, 30, 1.6f);
        Sfx.Play(SfxKind.Bell, 1f, target.transform.position);
        Kills.Kill(ctx.Caster, target, leaveBody: false);
    }
}

// ══ C: THE MIRROR DIMENSION ══════════════════════════════════════════════════

public class MirrorShard : StrangeAbility
{
    public override string Id => "mirror_shard";
    public override string Name => "Mirror Shard";
    public override string Category => "Mirror Dimension";
    public override string Label => "SHARD";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = target != null ? Players.Pos(target) : Ahead(caster, Cfg.Range);

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target; var from = ctx.CasterPos;
        Fx.Projectile(from, ctx.Point, MirrorBlue, 14f, () =>
        {
            Fx.Fragments(ctx.Point, MirrorBlue, 40, 1.4f, 1f);
            Fx.Release(ctx.Point, MirrorBlue, ImpactStyle.Glitch, 0.8f);
            foreach (var pc in Players.InRadius(ctx.Point, 1f, 6, ctx.Caster)) Hit(ctx, pc, ctx.Point, ctx.Cfg.Duration);
        });
    }
}

/// <summary>Everyone nearby sees reality fold: cracks, mirrored tint, slowed movement.</summary>
public class FracturedReality : StrangeAbility
{
    public override string Id => "fractured_reality";
    public override string Name => "Fractured Reality";
    public override string Category => "Mirror Dimension";
    public override string Label => "FRACTURE";

    protected override void OnCast(CastContext ctx)
    {
        var c = ctx.CasterPos;
        // kaleidoscope: the floor folds into mirror panels and the space ripples twice
        Fx.Cracks(c, MirrorBlue, 14, ctx.Cfg.Range, ctx.Cfg.Duration, ctx.Rng.Next());
        Fx.Release(c, MirrorBlue, ImpactStyle.Glitch, 1.3f);
        Fx.ShockRing(c, MirrorBlue, ctx.Cfg.Range * 2f, 0.8f);
        Sfx.Play(SfxKind.Reverse, 1.1f, c, 0.5f);
        foreach (var pc in Players.InRadius(c, ctx.Cfg.Range, 15, ctx.Caster))
        {
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, ctx.Cfg.Duration);
            if (ctx.LocalIs(pc))
            {
                Fx.ScreenTint(new Color(0.5f, 0.7f, 1f, 0.3f), ctx.Cfg.Duration, pulses: 3);
                Fx.Shake(ctx.Cfg.Duration * 0.5f, 0.15f);
            }
        }
    }
}

public class MirrorClone : StrangeAbility
{
    public override string Id => "mirror_clone";
    public override string Name => "Mirror Clone";
    public override string Category => "Mirror Dimension";
    public override string Label => "CLONE";

    protected override void OnCast(CastContext ctx)
    {
        var spot = MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.6f, 1.2f);
        Fx.Beam(ctx.CasterPos, spot, MirrorBlue, 0.08f, 0.3f);
        Fx.Release(spot, MirrorBlue, ImpactStyle.Glitch, 0.5f);
        var clone = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
        clone.SetBehavior(IllusionBehavior.Wander);
        clone.Lifetime = ctx.Cfg.Duration;
    }
}

/// <summary>Send a target into the Mirror Dimension (an isolated arena far from the map) for a while.</summary>
public class DimensionalPrison : StrangeAbility
{
    public static readonly Vector2 MirrorCenter = new(0f, -260f);

    public override string Id => "dimensional_prison";
    public override string Name => "Dimensional Prison";
    public override string Category => "Mirror Dimension";
    public override string Label => "PRISON";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // the target falls into their own reflection: a pane of glass shatters where they stood
        Fx.Release(ctx.Target.transform.position, MirrorBlue, ImpactStyle.Glitch, 1.1f);
        Fx.Fragments(ctx.Target.transform.position, MirrorBlue, 30, 1.2f, 0.9f);
        MirrorScenery(MirrorCenter, ctx.Cfg.Duration + 1f, ctx.Rng.Next());
        Dimensions.Send(ctx.Target, MirrorCenter, Vector2.zero, ctx.Cfg.Duration, MirrorBlue, "TRAPPED IN THE MIRROR DIMENSION");
    }

    /// <summary>Mirror architecture: rotating mirrored panels, shards and rings around the arena.</summary>
    public static void MirrorScenery(Vector2 center, float duration, int seed)
    {
        Dimensions.Scenery(center, MirrorBlue, duration, seed);
        var rng = new System.Random(seed);
        for (int i = 0; i < 10; i++)
        {
            float a = i * Mathf.PI / 5f;
            var p = center + new Vector2(Mathf.Cos(a) * 4f, Mathf.Sin(a) * 2.6f);
            var panel = Fx.MakeSprite(Sprites.Box, p, new Color(0.7f, 0.9f, 1f, 0.6f), 1.2f + (float)rng.NextDouble());
            float spin = (float)(rng.NextDouble() * 60 - 30);
            Fx.Animate(panel, duration, k =>
            {
                panel.transform.Rotate(0, 0, spin * Time.deltaTime);
                var s = panel.transform.localScale;
                panel.transform.localScale = new Vector3(s.y * Mathf.Abs(Mathf.Cos(Time.time + a)), s.y, 1f);   // folding
            });
        }
    }
}

/// <summary>Ultimate: Strange and everyone near him fall into a collapsing mirror world, then return.</summary>
public class MirrorWorldCollapse : StrangeAbility
{
    public override string Id => "mirror_world_collapse";
    public override string Name => "Mirror World Collapse";
    public override string Category => "Mirror Dimension";
    public override string Label => "MIRROR WORLD";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        int seed = ctx.Rng.Next();
        CinematicPlayer.Play("MIRROR WORLD", MirrorBlue, caster, ctx.Cfg.CinematicDur, () =>
        {
            var c = DimensionalPrison.MirrorCenter;
            Fx.Release(caster.transform.position, MirrorBlue, ImpactStyle.Glitch, 2.5f, cinematic: true);
            Fx.Fragments(caster.transform.position, MirrorBlue, 70, ctx.Cfg.Range, 1.2f);
            // the whole area folds: a big mirror sigil and the rest of the world dims
            Fx.Sigil(caster.transform.position, MirrorBlue, ctx.Cfg.Range * 1.4f, 0.8f);
            Fx.Darken(0.3f, 1.5f, new Color(0.1f, 0.15f, 0.3f));
            DimensionalPrison.MirrorScenery(c, ctx.Cfg.Duration + 1f, seed);
            var who = Players.InRadius(caster.transform.position, ctx.Cfg.Range, 15, caster);
            who.Add(caster);
            for (int i = 0; i < who.Count; i++)
            {
                float a = i * Mathf.PI * 2f / who.Count;
                Dimensions.Send(who[i], c, new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 2f, ctx.Cfg.Duration, MirrorBlue, "THE MIRROR WORLD");
            }
            for (int i = 0; i < 6; i++)   // Strange on every surface
            {
                float a = i * Mathf.PI / 3f;
                var echo = IllusionManager.Instance!.SpawnIllusion(caster, c + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 3.5f, quiet: true);
                echo.SetBehavior(IllusionBehavior.Idle);
                echo.Untouchable = true;
                echo.SetTint(new Color(0.8f, 0.95f, 1f, 0.6f));
                echo.Lifetime = ctx.Cfg.Duration;
            }
        });
    }
}

// ══ D: THE TIME STONE ════════════════════════════════════════════════════════

public class TemporalRewind : StrangeAbility
{
    public override string Id => "temporal_rewind";
    public override string Name => "Temporal Rewind";
    public override string Category => "Time Stone";
    public override string Label => "REWIND";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = PositionHistory.Ago(Cfg.Duration, caster.transform.position);

    protected override void OnCast(CastContext ctx)
    {
        for (int i = 1; i <= 4; i++) Fx.Flare(Vector2.Lerp(ctx.CasterPos, ctx.Point, i / 5f), new Color(TimeGreen.r, TimeGreen.g, TimeGreen.b, 0.6f), 0.6f, 0.2f + i * 0.07f);
        Fx.Release(ctx.Point, TimeGreen, ImpactStyle.Time, 0.8f);
        var clock = Fx.MakeSprite(Sprites.Runes, ctx.Point, TimeGreen, 1.3f);
        Fx.Animate(clock, 0.8f, k => { clock.transform.Rotate(0, 0, -600f * Time.deltaTime); clock.color = new Color(TimeGreen.r, TimeGreen.g, TimeGreen.b, 1f - k); });
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

/// <summary>A green bubble where everyone inside moves in slow motion.</summary>
public class TimeBubble : StrangeAbility
{
    public override string Id => "time_bubble";
    public override string Name => "Time Bubble";
    public override string Category => "Time Stone";
    public override string Label => "BUBBLE";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point) => point = Ahead(caster, 1.5f);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private static IEnumerator Run(CastContext ctx)
    {
        var p = ctx.Point;
        float r = ctx.Cfg.Radius;
        var bubble = Fx.MakeSprite(Sprites.Glow, p, new Color(TimeGreen.r, TimeGreen.g, TimeGreen.b, 0.3f), r * 2f);
        var ring = Fx.MakeSprite(Sprites.Runes, p, new Color(TimeGreen.r, TimeGreen.g, TimeGreen.b, 0.7f), r * 2f);
        int tick = 0;
        for (float t = 0; t < ctx.Cfg.Duration; t += 0.4f)
        {
            if (MeetingHud.Instance != null) break;
            if (tick++ % 2 == 0) Sfx.Play(SfxKind.Tick, 0.7f, p, 0.35f);   // slow, heavy clock
            foreach (var pc in Players.InRadius(p, r, 15, ctx.Caster))
                StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, 0.6f);
            for (float w = 0; w < 0.4f; w += Time.deltaTime)
            {
                if (ring != null) ring.transform.Rotate(0, 0, 30f * Time.deltaTime);
                yield return null;
            }
        }
        if (bubble != null) Object.Destroy(bubble.gameObject);
        if (ring != null) Object.Destroy(ring.gameObject);
        Fx.Release(p, TimeGreen, ImpactStyle.Time, r * 0.6f);
    }
}

public class StrangeTemporalEcho : StrangeAbility
{
    public override string Id => "strange_echo";
    public override string Name => "Temporal Echo";
    public override string Category => "Time Stone";
    public override string Label => "ECHO";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = PositionHistory.Ago(4f, caster.transform.position);

    protected override void OnCast(CastContext ctx)
    {
        var path = ctx.LocalIs(ctx.Caster) ? PositionHistory.Since(4f) : new List<Vector2> { ctx.Point, ctx.CasterPos };
        if (path.Count < 2) path = new List<Vector2> { ctx.Point, ctx.CasterPos };
        Fx.Release(path[0], TimeGreen, ImpactStyle.Time, 0.5f);
        var echo = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, path[0]);
        echo.SetTint(new Color(0.5f, 1f, 0.6f, 0.6f));
        echo.Untouchable = true;
        echo.PlayPath(path, 4f);
        echo.Lifetime = ctx.Cfg.Duration;
        // Strange's echo leaves a smear of afterimages, like a long exposure
        Fx.Trail(echo.transform, TimeGreen, Mathf.Min(4f, ctx.Cfg.Duration), 0.09f);
    }
}

/// <summary>The target keeps being returned to the same spot for a few seconds.</summary>
public class TimeLoop : StrangeAbility
{
    public override string Id => "time_loop";
    public override string Name => "Time Loop";
    public override string Category => "Time Stone";
    public override string Label => "LOOP";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx) { if (ctx.Target != null) Coroutines.Start(Run(ctx, ctx.Target)); }

    private static IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        Vector2 anchor = target.transform.position;
        Fx.Sigil(anchor, TimeGreen, 1.3f, ctx.Cfg.Duration, spinBack: false);   // the loop's anchor point
        for (float t = 0; t < ctx.Cfg.Duration; t += 1.2f)
        {
            for (float w = 0; w < 1.2f; w += Time.deltaTime)
            {
                if (MeetingHud.Instance != null || !Players.IsAlive(target)) yield break;
                yield return null;
            }
            Fx.Release(anchor, TimeGreen, ImpactStyle.Time, 0.6f);
            Fx.Beam(target.transform.position, anchor, TimeGreen, 0.08f, 0.25f);   // yanked back along a thread of time
            Players.Teleport(target, anchor);
        }
    }
}

/// <summary>Ultimate: the Eye opens; the target is frozen in a sphere of runes while echoes replay around them.</summary>
public class EyeOfAgamotto : StrangeAbility
{
    public override string Id => "eye_of_agamotto";
    public override string Name => "Eye of Agamotto";
    public override string Category => "Time Stone";
    public override string Label => "THE EYE";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        // the Eye opens on Strange's chest during the wind-up
        Fx.Aura(ctx.Caster.transform, Sprites.Glow, new Color(TimeGreen.r, TimeGreen.g, TimeGreen.b, 0.9f), 0.3f, ctx.Cfg.CinematicDur + 0.5f, 0f, 0.15f);
        CinematicPlayer.Play("EYE OF AGAMOTTO", TimeGreen, ctx.Caster, ctx.Cfg.CinematicDur, () =>
        {
            Fx.Sigil(target.transform.position, TimeGreen, 2.4f, ctx.Cfg.Duration * 0.3f);
            Fx.Release(target.transform.position, TimeGreen, ImpactStyle.Time, 2f, cinematic: true);
            StatusManager.Instance?.ApplyStatus(target, StatusEffect.Frozen, ctx.Cfg.Duration);
            for (int i = 0; i < 3; i++)
                Fx.Aura(target.transform, Sprites.Runes, new Color(TimeGreen.r, TimeGreen.g, TimeGreen.b, 0.8f), 1.2f + i * 0.4f, ctx.Cfg.Duration, (i % 2 == 0 ? 1 : -1) * (60f + i * 30f));
            for (int i = 0; i < 3; i++)
            {
                float a = i * Mathf.PI * 2f / 3f;
                var echo = IllusionManager.Instance!.SpawnIllusion(target, (Vector2)target.transform.position + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.2f, quiet: true);
                echo.SetTint(new Color(0.5f, 1f, 0.6f, 0.5f));
                echo.Untouchable = true;
                echo.SetBehavior(IllusionBehavior.Wander);
                echo.Lifetime = ctx.Cfg.Duration;
            }
            if (target == PlayerControl.LocalPlayer) Fx.ScreenTint(new Color(0.3f, 1f, 0.5f, 0.35f), ctx.Cfg.Duration);
        });
    }
}

// ══ E: ASTRAL & COSMIC ═══════════════════════════════════════════════════════

public class AstralProjection : StrangeAbility
{
    public override string Id => "astral_projection";
    public override string Name => "Astral Projection";
    public override string Category => "Astral";
    public override string Label => IsOn(PlayerControl.LocalPlayer) ? "RETURN" : "ASTRAL";
    /// <summary>Press to leave your body, press again to return. Duration is the maximum time out.</summary>
    public override bool IsToggle => true;

    protected override void OnCast(CastContext ctx)
    {
        Coroutines.Start(BodyLoop(ctx));
        if (ctx.LocalIs(ctx.Caster)) Coroutines.Start(Run(ctx));
    }

    /// <summary>Every client: the body stays rooted with a soft glow until the projection returns.</summary>
    private IEnumerator BodyLoop(CastContext ctx)
    {
        var caster = ctx.Caster;
        var glow = Fx.Aura(caster.transform, Sprites.Glow, new Color(Astral.r, Astral.g, Astral.b, 0.45f), 1.4f, ctx.Cfg.Duration + 1f);
        for (float t = 0; t < ctx.Cfg.Duration; t += Time.deltaTime)
        {
            if (!IsOn(caster) || caster == null || !Players.IsAlive(caster) || MeetingHud.Instance != null) break;
            StatusManager.Instance?.ApplyStatus(caster, StatusEffect.Rooted, 0.3f);
            yield return null;
        }
        if (glow != null) Object.Destroy(glow.gameObject);
        if (caster != null) { Fx.Sparkles(caster.transform.position, Astral, 8, 0.8f, 0.5f); Sfx.Play(SfxKind.Shimmer, 0.8f, caster.transform.position, 0.5f); }
        ToggleEnded(caster);
    }

    private IEnumerator Run(CastContext ctx)
    {
        var hud = HudManager.Instance;
        var astral = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, ctx.CasterPos, quiet: true);
        astral.SetBehavior(IllusionBehavior.Controlled);
        astral.Untouchable = true;
        astral.Lifetime = ctx.Cfg.Duration + 1f;
        astral.SetTint(new Color(1f, 0.92f, 0.6f, 0.5f));
        if (hud != null && hud.PlayerCam != null) hud.PlayerCam.SetTarget(astral);
        Fx.Toast("ASTRAL FORM: WASD / ARROWS — PRESS AGAIN TO RETURN", Astral, 2.5f, 3.4f);
        for (float t = 0; t < ctx.Cfg.Duration; t += Time.deltaTime)
        {
            if (!IsOn(ctx.Caster) || MeetingHud.Instance != null || astral == null || !Players.IsAlive(ctx.Caster)) break;
            if (Vector2.Distance(astral.transform.position, ctx.CasterPos) > ctx.Cfg.Range)   // leash
            {
                var back = ctx.CasterPos + ((Vector2)astral.transform.position - ctx.CasterPos).normalized * ctx.Cfg.Range;
                astral.transform.position = new Vector3(back.x, back.y, back.y / 1000f);
            }
            yield return null;
        }
        if (astral != null) astral.Vanish();
        var lp = PlayerControl.LocalPlayer;
        if (hud != null && hud.PlayerCam != null && lp != null) hud.PlayerCam.SetTarget(lp);
    }
}

/// <summary>See every player and every portal through walls for a while.</summary>
public class AstralSight : StrangeAbility
{
    public override string Id => "astral_sight";
    public override string Name => "Astral Sight";
    public override string Category => "Astral";
    public override string Label => "SIGHT";

    protected override void OnCast(CastContext ctx)
    {
        Fx.Release(ctx.CasterPos, Astral, ImpactStyle.Eerie, 0.6f);
        if (!ctx.LocalIs(ctx.Caster)) return;
        foreach (var pc in Players.All())
            if (pc != ctx.Caster && Players.IsAlive(pc)) Fx.ScreenMarker(pc.transform, Astral, ctx.Cfg.Duration);
        foreach (var p in PortalEngine.All)
            if (p.Visual != null) Fx.ScreenMarker(p.Visual.transform, Gold, ctx.Cfg.Duration);
        Fx.ScreenTint(new Color(1f, 0.9f, 0.5f, 0.12f), ctx.Cfg.Duration);
    }
}

public class AstralBind : StrangeAbility
{
    public override string Id => "astral_bind";
    public override string Name => "Astral Bind";
    public override string Category => "Astral";
    public override string Label => "BIND";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        Coroutines.Start(Fx.Bind(ctx.Caster.transform, ctx.Target.transform, Astral, Mathf.Min(1.5f, ctx.Cfg.Duration), 2));
        Fx.Rising(ctx.Target.transform.position, Astral, 6, 0.6f, 1f);
        Sfx.Play(SfxKind.Choir, 1.5f, ctx.Target.transform.position, 0.3f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Chained, ctx.Cfg.Duration);
    }
}

public class DimensionStep : StrangeAbility
{
    public override string Id => "dimension_step";
    public override string Name => "Dimension Step";
    public override string Category => "Astral";
    public override string Label => "STEP";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point) => point = Ahead(caster, Cfg.Range);

    protected override void OnCast(CastContext ctx)
    {
        PortalEngine.OpenPair(ctx.Caster, ctx.CasterPos, ctx.Point, 0.8f);
        Fx.Trail(ctx.Caster.transform, Gold, 0.3f);
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

/// <summary>Ultimate: the target's astral form is pulled into a cosmic dimension, then returned.</summary>
public class AstralExile : StrangeAbility
{
    public static readonly Vector2 AstralCenter = new(-260f, -260f);

    public override string Id => "astral_exile";
    public override string Name => "Astral Exile";
    public override string Category => "Astral";
    public override string Label => "EXILE";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        int seed = ctx.Rng.Next();
        // the target's soul starts leaking out of their body during the wind-up
        Fx.Rising(target.transform.position, Astral, 14, 0.8f, ctx.Cfg.CinematicDur + 0.5f);
        CinematicPlayer.Play("ASTRAL EXILE", Astral, ctx.Caster, ctx.Cfg.CinematicDur, () =>
        {
            Fx.Release(target.transform.position, Astral, ImpactStyle.Eerie, 1.8f, cinematic: true);
            Dimensions.Scenery(AstralCenter, Astral, ctx.Cfg.Duration + 1f, seed);
            Dimensions.Send(target, AstralCenter, Vector2.zero, ctx.Cfg.Duration, Astral, "YOUR SOUL IS PULLED FROM YOUR BODY");
        });
    }
}

// ══ Hidden: role assignment ══════════════════════════════════════════════════

/// <summary>Sent by the host at the start of a match: the target crewmate becomes Doctor Strange.</summary>
public class AssignStrange : AbilityBase
{
    public override string Id => "assign_strange";
    public override string Name => "Assign Doctor Strange";
    public override string Category => "Hidden";
    public override bool Silent => true;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target != null) CharacterRegistry.Assign(ctx.Target.PlayerId, "strange");
    }
}

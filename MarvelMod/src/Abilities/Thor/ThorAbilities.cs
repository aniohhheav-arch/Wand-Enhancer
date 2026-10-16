using System.Collections;
using System.Collections.Generic;
using MarvelMod.Abilities.Loki;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.UI.HUD;
using MarvelMod.VFX;
using Reactor.Utilities;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Thor;

/// <summary>
/// Thor: lightning with weight. Visual language: branching bolts from the sky, hammer arcs, scorch
/// marks, rolling thunder, storm darkness. Sounds: crackle builds, clangs, thunder cracks and rolls.
/// </summary>
public abstract class ThorAbility : AbilityBase
{
    public static readonly Color Bolt = new(0.65f, 0.85f, 1f, 1f);
    public static readonly Color Storm = new(0.25f, 0.45f, 0.95f, 1f);
    public static readonly Color Steel = new(0.75f, 0.78f, 0.85f, 1f);

    public override string Category => "Thunder";
    public override Color CastColor => Bolt;

    /// <summary>Electricity crawls over the hammer hand before every move.</summary>
    protected override void BeforeCast(CastContext ctx)
    {
        var hand = ctx.CasterPos + new Vector2(0.38f, 0.02f);
        for (int i = 0; i < 2; i++)
            Fx.Lightning(hand + Random.insideUnitCircle * 0.15f, hand + Random.insideUnitCircle * 0.6f, Bolt, 0.15f, 0, 0.15f);
    }

    protected static void Hit(CastContext ctx, PlayerControl pc, Vector2 from, float stun) =>
        DamageSystem.Instance?.ApplyDamage(pc, from, ctx.Cfg.Damage, DamageType.Magical, stun);

    /// <summary>The hammer sprite (generated icon, or a steel block if missing).</summary>
    public static SpriteRenderer Hammer(Vector2 at, float size = 0.55f, string prop = "prop_mjolnir")
    {
        var icon = Sprites.Icon(prop, 256f);
        var sr = Fx.MakeSprite(icon ?? Sprites.Box, at, icon != null ? Color.white : Steel, icon != null ? size : size * 0.6f, "ThorHammer");
        return sr;
    }

    /// <summary>A dark storm cloud bank drifting over an area for a while.</summary>
    public static void Clouds(Vector2 center, float radius, float dur, int count = 8)
    {
        for (int i = 0; i < count; i++)
        {
            var p = center + new Vector2(Random.Range(-radius, radius), Random.Range(0.5f, 1.5f) + Random.Range(-radius, radius) * 0.4f);
            var cloud = Fx.MakeSprite(Sprites.Glow, p, new Color(0.08f, 0.09f, 0.14f, 0f), Random.Range(1.6f, 2.8f), "StormCloud");
            float drift = Random.Range(-0.3f, 0.3f);
            Fx.Animate(cloud, dur, k =>
            {
                cloud.transform.position += new Vector3(drift * Time.deltaTime, 0f, 0f);
                cloud.color = new Color(0.08f, 0.09f, 0.14f, 0.7f * Mathf.Min(1f, k * 5f) * Mathf.Min(1f, (1f - k) * 5f));
            });
        }
    }
}

/// <summary>Hurl Mjolnir: a spinning hammer with a crackling trail, a heavy hit, and it flies back to your hand.</summary>
public class MjolnirThrow : ThorAbility
{
    public override string Id => "mjolnir_throw";
    public override string Name => "Mjolnir Throw";
    public override string Label => "MJOLNIR";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = target != null ? Players.Pos(target) : Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, Cfg.Range);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var caster = ctx.Caster; var target = ctx.Target;
        Vector2 from = ctx.CasterPos + new Vector2(0.35f, 0f), to = ctx.Point;
        var hammer = Hammer(from);
        Fx.Trail(hammer.transform, Bolt, 2f, 0.03f);
        Sfx.Play(SfxKind.Whoosh, 0.7f, from, 0.7f);
        Sfx.Play(SfxKind.Sizzle, 1f, from, 0.4f);
        float dur = Mathf.Max(0.12f, Vector2.Distance(from, to) / 15f);
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (hammer == null) yield break;
            var p = Vector2.Lerp(from, to, t / dur);
            hammer.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            hammer.transform.Rotate(0, 0, -1100f * Time.deltaTime);
            if (Random.value < 0.3f) Fx.Lightning(p, p + Random.insideUnitCircle * 0.5f, Bolt, 0.1f, 0, 0.1f);
            yield return null;
        }
        // impact: heavy thud + electric burst + scorch
        Fx.Release(to, Bolt, ImpactStyle.Heavy, 1f);
        Fx.Release(to, Bolt, ImpactStyle.Electric, 0.7f);
        Fx.Scorch(to, 0.8f);
        Sfx.Play(SfxKind.Clang, 0.9f, to, 0.7f);
        if (target != null && Players.IsAlive(target)) Hit(ctx, target, from, ctx.Cfg.Duration);
        yield return new WaitForSeconds(0.15f);
        // the return trip: it always finds its way back to the hand
        float back = 0.35f;
        for (float t = 0; t < back; t += Time.deltaTime)
        {
            if (hammer == null || caster == null) break;
            var hand = (Vector2)caster.transform.position + new Vector2(0.35f, 0f);
            var p = Vector2.Lerp(to, hand, (t / back) * (t / back)) + new Vector2(0f, Mathf.Sin(t / back * Mathf.PI) * 0.5f);
            hammer.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            hammer.transform.Rotate(0, 0, 1300f * Time.deltaTime);
            yield return null;
        }
        if (hammer != null) Object.Destroy(hammer.gameObject);
        if (caster != null)
        {
            Fx.Flare((Vector2)caster.transform.position + new Vector2(0.35f, 0f), Bolt, 0.6f, 0.15f);
            Sfx.Play(SfxKind.Thud, 1.3f, caster.transform.position, 0.5f);   // *catch*
        }
    }
}

/// <summary>Mark a spot (the closest player, or ahead of you); a warning ring flickers, then a bolt hits it.</summary>
public class LightningStrike : ThorAbility
{
    public override string Id => "lightning_strike";
    public override string Name => "Lightning Strike";
    public override string Label => "STRIKE";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = target != null ? Players.Pos(target) : Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, Cfg.Range * 0.6f);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        // Thor points his hammer at the sky: a thin bolt goes up first
        var hand = ctx.CasterPos + new Vector2(0.35f, 0.3f);
        Fx.Lightning(hand, hand + new Vector2(0f, 3f), Bolt, 0.2f, 1, 0.2f);
        yield return Fx.StrikeFromSky(ctx.Point, Bolt, Mathf.Max(0.2f, ctx.Cfg.CastTime), 1.1f);
        Sfx.Play(SfxKind.Thunder, 1f * Sfx.Vary(), ctx.Point, 0.8f);
        foreach (var pc in Players.InRadius(ctx.Point, ctx.Cfg.Radius, 15, ctx.Caster)) Hit(ctx, pc, ctx.Point, ctx.Cfg.Duration);
        Fx.Aftermath(ctx.Point, Bolt, Residue.Static, 0.8f);
    }
}

/// <summary>Leap, then slam the hammer down: a circular thunder wave that throws everyone back.</summary>
public class Thunderclap : ThorAbility
{
    public override string Id => "thunderclap";
    public override string Name => "Thunderclap";
    public override string Label => "THUNDERCLAP";

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var caster = ctx.Caster;
        StatusManager.Instance?.ApplyStatus(caster, StatusEffect.Rooted, 0.6f);
        Sfx.Play(SfxKind.Sizzle, 0.8f, ctx.CasterPos, 0.5f);
        for (float t = 0; t < 0.35f; t += Time.deltaTime)
        {
            if (caster == null) yield break;
            CharacterVisuals.Set(caster, "clap", lift: Mathf.Sin(t / 0.35f * Mathf.PI * 0.5f) * 0.6f, maxSeconds: 1f);
            if (Random.value < 0.4f) Fx.Lightning(caster.transform.position, (Vector2)caster.transform.position + Random.insideUnitCircle * 0.8f, Bolt, 0.1f, 0, 0.15f);
            yield return null;
        }
        yield return new WaitForSeconds(0.06f);
        if (caster == null) yield break;
        CharacterVisuals.Clear(caster, "clap");
        Vector2 c = caster.transform.position;
        Fx.Release(c, Bolt, ImpactStyle.Heavy, 1.4f);
        Fx.Release(c, Bolt, ImpactStyle.Electric, 1.3f);
        Fx.ShockRing(c, Color.white, ctx.Cfg.Range * 2f, 0.4f, squash: 0.6f);
        Sfx.Play(SfxKind.Thunder, 1.2f, c, 0.8f);
        Fx.Wave(c, Bolt, ctx.Cfg.Range, 0.35f, pc =>
        {
            if (pc == caster) return;
            Fx.Lightning(c, pc.transform.position, Bolt, 0.2f, 1, 0.3f);
            Players.Knockback(pc, c, Mathf.Max(0.5f, ctx.Cfg.Radius));
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, ctx.Cfg.Duration);
        });
        Fx.Shake(0.5f, 0.6f);
        Fx.Aftermath(c, Bolt, Residue.Static, 1.2f);
    }
}

/// <summary>Spin the hammer and fly: a fast dash along your movement direction leaving a lightning trail.</summary>
public class Stormflight : ThorAbility
{
    public override string Id => "stormflight";
    public override string Name => "Stormflight";
    public override string Label => "FLIGHT";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, Cfg.Range);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var caster = ctx.Caster;
        Vector2 from = ctx.CasterPos, to = ctx.Point;
        Fx.Release(from, Bolt, ImpactStyle.Electric, 0.7f);
        Sfx.Play(SfxKind.Whoosh, 0.6f, from, 0.8f);
        Fx.Trail(caster.transform, Bolt, 0.5f, 0.025f);
        CharacterVisuals.Set(caster, "flight", lift: 0.35f, tilt: to.x >= from.x ? -20f : 20f, maxSeconds: 1.5f);
        const int steps = 6;
        Vector2 prev = from;
        for (int i = 1; i <= steps; i++)
        {
            var p = Vector2.Lerp(from, to, (float)i / steps);
            Players.Teleport(caster, p);   // only the owner actually moves
            Fx.Lightning(prev, p, Bolt, 0.3f, 1, 0.2f);
            prev = p;
            yield return new WaitForSeconds(0.04f);
        }
        if (caster != null) CharacterVisuals.Clear(caster, "flight");
        Fx.Release(to, Bolt, ImpactStyle.Electric, 0.9f);
        Fx.Scorch(to, 0.7f);
        Sfx.Play(SfxKind.Thud, 1f, to, 0.6f);
    }
}

/// <summary>Ultimate: the sky goes dark, storm clouds roll in, bolts rain down, then one colossal strike on Thor.</summary>
public class GodOfThunder : ThorAbility
{
    public override string Id => "god_of_thunder";
    public override string Name => "God of Thunder";
    public override string Label => "GOD OF THUNDER";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        Fx.Darken(0.45f, ctx.Cfg.CinematicDur + ctx.Cfg.Duration + 1f, new Color(0.02f, 0.03f, 0.1f));
        Clouds(ctx.CasterPos, ctx.Cfg.Range, ctx.Cfg.CinematicDur + ctx.Cfg.Duration + 1f, 10);
        Sfx.Play(SfxKind.Thunder, 0.6f, ctx.CasterPos, 0.6f, cinematic: true);
        CharacterVisuals.Set(caster, "godthunder", lift: 0.3f, maxSeconds: ctx.Cfg.CinematicDur + ctx.Cfg.Duration + 2f);
        CinematicPlayer.Play("GOD OF THUNDER", Bolt, caster, ctx.Cfg.CinematicDur, () => Coroutines.Start(RainStorm(ctx)));
    }

    private IEnumerator RainStorm(CastContext ctx)
    {
        var caster = ctx.Caster;
        Vector2 c = caster.transform.position;
        float r = ctx.Cfg.Range;
        // rain of bolts around the area (same pattern on every client)
        int bolts = Mathf.RoundToInt(6 + ctx.Cfg.Duration * 2f);
        for (int i = 0; i < bolts; i++)
        {
            if (MeetingHud.Instance != null) break;
            float a = (float)(ctx.Rng.NextDouble() * Mathf.PI * 2), d = (float)ctx.Rng.NextDouble() * r;
            var p = c + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * d;
            Coroutines.Start(Fx.StrikeFromSky(p, Bolt, 0.35f, 0.8f));
            foreach (var pc in Players.InRadius(p, 1f, 4, caster)) StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, 0.8f);
            yield return new WaitForSeconds(ctx.Cfg.Duration / bolts);
        }
        if (caster == null) yield break;
        CharacterVisuals.Clear(caster, "godthunder");
        // the colossal final strike on Thor himself, and the shockwave
        c = caster.transform.position;
        for (int i = 0; i < 3; i++) Fx.Lightning(c + new Vector2((i - 1) * 0.4f, 8f), c, i == 1 ? Color.white : Bolt, 0.6f, 4, 0.6f);
        Fx.Flash(new Color(0.85f, 0.9f, 1f, 0.8f), 0.3f);
        Fx.Release(c, Bolt, ImpactStyle.Electric, 2.6f, cinematic: true);
        Fx.Release(c, Storm, ImpactStyle.Heavy, 2.2f, cinematic: true);
        Sfx.Play(SfxKind.Thunder, 0.5f, c, 1f, cinematic: true);
        Fx.Cracks(c, Bolt, 16, r * 0.8f, 2f, ctx.Rng.Next());
        Fx.Wave(c, Bolt, r, 0.6f, pc =>
        {
            if (pc == caster) return;
            if (ctx.Cfg.Damage > 0f && Vector2.Distance(c, pc.transform.position) <= ctx.Cfg.Radius) Kills.Kill(caster, pc);
            else Hit(ctx, pc, c, 2f);
        });
        Fx.Aftermath(c, Bolt, Residue.Static, 2f);
    }
}

// ══ FINISHERS ═════════════════════════════════════════════════════════════════

public abstract class ThorFinisher : FinisherAbility
{
    public override string Category => "Thor Finishers";
    public override Color CastColor => ThorAbility.Bolt;
    protected static Color Bolt => ThorAbility.Bolt;
    protected static Color Storm => ThorAbility.Storm;

    /// <summary>Lightning crawling over a body for a while.</summary>
    protected static IEnumerator Crackle(PlayerControl pc, float dur, float radius = 0.7f)
    {
        float tick = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (pc == null) yield break;
            tick -= Time.deltaTime;
            if (tick <= 0f)
            {
                tick = 0.08f;
                Vector2 p = pc.transform.position;
                Fx.Lightning(p + Random.insideUnitCircle * 0.2f, p + Random.insideUnitCircle * radius, Bolt, 0.1f, 0, 0.15f);
            }
            yield return null;
        }
    }
}

/// <summary>1. Mjolnir flies into Thor's hand, charges with electricity, and comes down in one dramatic strike.</summary>
public class TheWorthy : ThorFinisher
{
    public override string Id => "the_worthy";
    public override string Name => "The Worthy";
    public override string Label => "WORTHY";
    protected override float NominalSeconds => 8f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var thor = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Players.Teleport(thor, Players.SafeDestination(vp, Vector2.left, 1.3f));
        d.Zoom(0.6f, 0.8f);
        Fx.WorldText(thor.transform, "*holds out a hand*", Bolt, 1f * d.TimeScale, 2.4f);
        yield return d.Wait(0.7f);

        // the hammer comes screaming in from off-screen
        var tp = Pos(thor) + new Vector2(0.35f, 0.1f);
        var start = tp + new Vector2(-6f, 3f);
        var hammer = ThorAbility.Hammer(start, 0.6f);
        Fx.Trail(hammer.transform, Bolt, 1f * d.TimeScale, 0.03f);
        Sfx.Play(SfxKind.Whoosh, 0.6f, tp, 0.8f);
        Fx.Animate(hammer, 0.6f * d.TimeScale, k =>
        {
            var p = Vector2.Lerp(start, tp, k * k);
            hammer.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.65f);
            hammer.transform.Rotate(0, 0, -1200f * Time.deltaTime);
        });
        yield return d.Wait(0.6f);
        Sfx.Play(SfxKind.Thud, 1.2f, tp, 0.7f);
        Fx.Flare(tp, Bolt, 0.8f, 0.2f);
        d.React(victim, "uh oh");

        // charge: the hammer is raised and lightning gathers into it
        var held = ThorAbility.Hammer(tp + new Vector2(0f, 0.5f), 0.55f);
        Reactor.Utilities.Coroutines.Start(Crackle(thor, 2f * d.TimeScale, 1f));
        Sfx.Play(SfxKind.Rise, 0.8f, tp, 0.7f, cinematic: true);
        for (int i = 0; i < 8; i++)
        {
            float a = i * Mathf.PI / 4f;
            Fx.Lightning(tp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 2.5f + new Vector2(0f, 1f), tp + new Vector2(0f, 0.5f), Bolt, 0.2f, 1, 0.3f);
            Sfx.Play(SfxKind.Crackle, 1f + i * 0.08f, tp, 0.35f);
            yield return d.Wait(0.18f);
        }
        d.Pan((Pos(thor) + vp) * 0.5f, 0.5f);
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(thor, k * 0.6f, -k * 15f); yield return d.Wait(0.04f); }
        yield return d.Wait(0.2f);   // hang at the top

        // the strike
        if (held != null) Object.Destroy(held.gameObject);
        CinematicDirector.Pose(thor, 0f, 0f);
        Players.Teleport(thor, Players.SafeDestination(vp, Vector2.left, 0.6f));
        Fx.Flash(new Color(0.85f, 0.9f, 1f, 0.7f), 0.25f);
        Fx.Release(vp, Bolt, ImpactStyle.Heavy, 2.2f, cinematic: true);
        for (int i = 0; i < 6; i++) Fx.Lightning(vp, vp + Random.insideUnitCircle.normalized * Random.Range(1.5f, 3f), Bolt, 0.5f, 2, 0.4f);
        Sfx.Play(SfxKind.Thunder, 0.8f, vp, 1f, cinematic: true);
        Sfx.Play(SfxKind.Clang, 0.7f, vp, 0.7f, cinematic: true);
        Fx.Scorch(vp, 1.6f);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);
        Fx.Aftermath(vp, Bolt, Residue.Static, 1.5f);
        Fx.WorldText(thor.transform, "*twirls hammer*", Bolt, 1f * d.TimeScale, 2.4f);
        yield return d.Wait(0.8f);
    }
}

/// <summary>2. A rainbow Bifrost beam slams down, Thor arrives in it, and launches straight through the target.</summary>
public class BifrostArrival : ThorFinisher
{
    public override string Id => "bifrost_arrival";
    public override string Name => "Bifrost Arrival";
    public override string Label => "BIFROST";
    protected override float NominalSeconds => 9f;

    private static readonly Color[] Rainbow =
    {
        new(1f, 0.3f, 0.3f), new(1f, 0.65f, 0.2f), new(1f, 0.95f, 0.3f), new(0.35f, 1f, 0.45f), new(0.3f, 0.7f, 1f), new(0.7f, 0.4f, 1f),
    };

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var thor = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var landing = Players.SafeDestination(vp, Vector2.left, 2f);
        d.Zoom(0.85f, 0.8f);
        d.Pan((landing + vp) * 0.5f, 0.6f);
        StatusManager.SetHidden(thor, true);
        // the Bifrost glyph is burned into the floor
        var glyph = Fx.MakeSprite(Sprites.Runes, landing + new Vector2(0f, -0.3f), new Color(1f, 1f, 1f, 0f), 2.4f, "BifrostGlyph");
        Fx.Animate(glyph, 5f * d.TimeScale, k =>
        {
            glyph.transform.localScale = new Vector3(2.4f, 1.2f, 1f);
            glyph.transform.Rotate(0, 0, 200f * Time.deltaTime);
            glyph.color = Color.Lerp(Rainbow[(int)(k * 30f) % Rainbow.Length], Color.white, 0.3f) * new Color(1, 1, 1, Mathf.Min(0.9f, k * 6f) * (1f - k));
        });
        d.React(victim, "?");
        Sfx.Play(SfxKind.Rise, 1f, landing, 0.8f, cinematic: true);
        yield return d.Wait(0.8f);

        // the rainbow beam slams down
        for (int i = 0; i < Rainbow.Length; i++)
        {
            float x = (i - 2.5f) * 0.12f;
            Fx.Beam(landing + new Vector2(x, 9f), landing + new Vector2(x, -0.2f), Rainbow[i], 0.25f, 1.6f * d.TimeScale);
        }
        Fx.Beam(landing + new Vector2(0f, 9f), landing, Color.white, 0.18f, 1.6f * d.TimeScale);
        Fx.Flash(new Color(1f, 1f, 1f, 0.6f), 0.2f);
        Sfx.Play(SfxKind.Gong, 0.7f, landing, 0.8f, cinematic: true);
        Sfx.Play(SfxKind.Thunder, 0.9f, landing, 0.6f, cinematic: true);
        Fx.Release(landing, Color.white, ImpactStyle.Cosmic, 1.8f, cinematic: true);
        yield return d.Wait(0.5f);
        Players.Teleport(thor, landing);
        StatusManager.SetHidden(thor, false);
        Fx.Release(landing, Bolt, ImpactStyle.Heavy, 1.4f);
        d.Cut(landing);
        yield return d.Wait(0.8f);
        Fx.WorldText(thor.transform, "*cracks neck*", Bolt, 1f * d.TimeScale, 2.4f);
        yield return d.Wait(0.8f);

        // he launches through the target in a rainbow streak
        d.Zoom(0.5f, 0.4f);
        var behind = Players.SafeDestination(vp, vp - landing, 1.2f);
        Fx.Trail(thor.transform, Bolt, 0.5f, 0.02f);
        for (int i = 0; i < Rainbow.Length; i++)
            Fx.Beam(landing + new Vector2(0f, (i - 2.5f) * 0.06f), behind + new Vector2(0f, (i - 2.5f) * 0.06f), Rainbow[i], 0.12f, 0.8f * d.TimeScale);
        Players.Teleport(thor, behind);
        Sfx.Play(SfxKind.Whoosh, 0.5f, vp, 0.9f, cinematic: true);
        Fx.Streaks(vp, Color.white, 14, 2f);
        Fx.Release(vp, Color.white, ImpactStyle.Cosmic, 2.2f, cinematic: true);
        Sfx.Play(SfxKind.Stab, 0.9f, vp, 0.8f, cinematic: true);
        d.Fade(victim, 0f);
        d.Finish();
        d.Pan(behind, 0.5f);
        yield return d.Wait(1.4f);
    }
}

/// <summary>3. Stormbreaker answers the call; a storm gathers over the target; one swing releases a blue-white lightning wave.</summary>
public class StormbreakerFinisher : ThorFinisher
{
    public override string Id => "stormbreaker";
    public override string Name => "Stormbreaker";
    public override string Label => "STORMBREAKER";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var thor = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var tp = Players.SafeDestination(vp, Vector2.left, 2.2f);
        Players.Teleport(thor, tp);
        d.Zoom(0.75f, 0.8f);
        d.Pan((tp + vp) * 0.5f, 0.6f);
        if (d.Participant) Fx.ScreenTint(new Color(0.05f, 0.08f, 0.2f, 0.5f), 8f * d.TimeScale);
        // the axe spins in from the sky
        var hand = tp + new Vector2(0.35f, 0.1f);
        var axe = ThorAbility.Hammer(hand + new Vector2(2f, 5f), 0.7f, "prop_stormbreaker");
        Fx.Trail(axe.transform, Storm, 0.8f * d.TimeScale, 0.03f);
        Vector2 a0 = axe.transform.position;
        Fx.Animate(axe, 0.7f * d.TimeScale, k =>
        {
            var p = Vector2.Lerp(a0, hand, k * k);
            axe.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.65f);
            axe.transform.Rotate(0, 0, 1000f * Time.deltaTime);
        });
        Sfx.Play(SfxKind.Whoosh, 0.5f, hand, 0.8f);
        yield return d.Wait(0.75f);
        Sfx.Play(SfxKind.Clang, 0.8f, hand, 0.7f);
        Fx.Release(hand, Storm, ImpactStyle.Electric, 1f);

        // the storm gathers over the target
        ThorAbility.Clouds(vp, 2.5f, 5f * d.TimeScale, 9);
        Reactor.Utilities.Coroutines.Start(Crackle(victim, 3f * d.TimeScale, 1.2f));
        d.React(victim, "!!");
        for (int i = 0; i < 5; i++)
        {
            float a = i * Mathf.PI * 2f / 5f;
            Fx.Lightning(vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 2f + new Vector2(0f, 1.5f), vp + new Vector2(Mathf.Cos(a + 1f), Mathf.Sin(a + 1f)) * 1.2f, Bolt, 0.3f, 2, 0.4f);
            Sfx.Play(SfxKind.Thunder, 1.3f + i * 0.08f, vp, 0.35f);
            yield return d.Wait(0.35f);
        }
        d.Orbit(vp, 0.9f, 120f, 1.2f);
        Fx.WorldText(thor.transform, "*raises Stormbreaker*", Bolt, 1f * d.TimeScale, 2.4f);
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(thor, k * 0.3f, -k * 25f); yield return d.Wait(0.05f); }
        yield return d.Wait(0.3f);

        // the swing: a crescent wave of blue-white lightning
        CinematicDirector.Pose(thor, 0f, 20f);
        if (axe != null) Object.Destroy(axe.gameObject);
        var dir = (vp - tp).normalized;
        var n = new Vector2(-dir.y, dir.x);
        for (int i = -3; i <= 3; i++)
            Fx.Lightning(tp + dir * 0.5f, vp + n * i * 0.35f + dir * 0.6f, i == 0 ? Color.white : Bolt, 0.5f, 1, 0.3f);
        Fx.Wave(vp, Bolt, 3f, 0.4f);
        Fx.Flash(new Color(0.8f, 0.9f, 1f, 0.6f), 0.25f);
        Fx.Release(vp, Bolt, ImpactStyle.Electric, 2.4f, cinematic: true);
        Sfx.Play(SfxKind.Thunder, 0.6f, vp, 1f, cinematic: true);
        Sfx.Play(SfxKind.Stab, 0.75f, vp, 0.7f, cinematic: true);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);
        CinematicDirector.Pose(thor, 0f, 0f);
        Fx.Aftermath(vp, Bolt, Residue.Static, 1.6f);
        yield return d.Wait(0.6f);
    }
}

/// <summary>4. The sky darkens and thunder rolls; strikes land in a tightening circle until the last one hits the target.</summary>
public class WrathOfAsgard : ThorFinisher
{
    public override string Id => "wrath_of_asgard";
    public override string Name => "Wrath of Asgard";
    public override string Label => "WRATH";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var thor = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        if (d.Participant) Fx.ScreenTint(new Color(0.02f, 0.03f, 0.1f, 0.6f), 8.5f * d.TimeScale);
        ThorAbility.Clouds(vp, 4f, 8f * d.TimeScale, 12);
        d.Zoom(1.1f, 1f);
        Sfx.Play(SfxKind.Thunder, 0.5f, vp, 0.7f, cinematic: true);
        yield return d.Wait(1f);
        Fx.WorldText(thor.transform, "*points at the sky*", Bolt, 1f * d.TimeScale, 2.4f);
        Sfx.Play(SfxKind.Thunder, 0.7f, null, 0.5f, cinematic: true);   // rolling, far away
        yield return d.Wait(0.8f);
        d.React(victim, "?!");

        // strikes land on a circle that keeps tightening around the target
        int n = 9;
        for (int i = 0; i < n; i++)
        {
            float k = (float)i / n;
            float r = Mathf.Lerp(3.2f, 0.7f, k);
            float a = i * 2.4f;
            Reactor.Utilities.Coroutines.Start(Fx.StrikeFromSky(vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a) * 0.7f) * r, Bolt, 0.18f, 0.8f));
            if (i % 3 == 2) d.TurnAround(victim);
            yield return d.Wait(Mathf.Lerp(0.45f, 0.18f, k));   // faster and faster
        }
        d.Zoom(0.5f, 0.4f);
        yield return d.Wait(0.5f);   // silence before the last one
        Reactor.Utilities.Coroutines.Start(Fx.StrikeFromSky(vp, Color.white, 0.25f, 2f));
        yield return d.Wait(0.3f);
        for (int i = 0; i < 3; i++) Fx.Lightning(vp + new Vector2((i - 1) * 0.5f, 8f), vp, Bolt, 0.6f, 3, 0.6f);
        Fx.Flash(new Color(0.9f, 0.95f, 1f, 0.8f), 0.3f);
        Sfx.Play(SfxKind.Thunder, 0.45f, vp, 1f, cinematic: true);
        Fx.Release(vp, Storm, ImpactStyle.Heavy, 2.2f, cinematic: true);
        Fx.Scorch(vp, 2f);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.4f);
        Fx.Aftermath(vp, Bolt, Residue.Smoke, 1.5f);
        yield return d.Wait(0.5f);
    }
}

/// <summary>5. Ultimate: Thor rises with lightning circling him, calls down a gigantic column, then drops in a final shockwave.</summary>
public class KingOfThunder : ThorFinisher
{
    public override string Id => "king_of_thunder";
    public override string Name => "King of Thunder";
    public override string Label => "KING";
    public override bool IsUltimate => true;
    protected override float NominalSeconds => 12f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var thor = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var tp = Players.SafeDestination(vp, Vector2.left, 1.8f);
        Players.Teleport(thor, tp);
        if (d.Participant)
        {
            Fx.ScreenTint(new Color(0.02f, 0.03f, 0.12f, 0.65f), 11f * d.TimeScale);
            Fx.Toast("KING OF THUNDER", Bolt, 2f * d.TimeScale, 5f, 2f);
        }
        ThorAbility.Clouds(vp, 5f, 10f * d.TimeScale, 14);
        d.Zoom(0.7f, 1f);
        d.Pan(tp, 0.8f);
        Sfx.Play(SfxKind.Rumble, 0.6f, tp, 0.8f, cinematic: true);
        yield return d.Wait(1f);

        // he rises; rings of lightning circle him faster and faster
        Reactor.Utilities.Coroutines.Start(Crackle(thor, 6f * d.TimeScale, 1.3f));
        for (int step = 0; step < 20; step++)
        {
            float k = step / 19f;
            CinematicDirector.Pose(thor, k * 1.1f, 0f);
            var c = Pos(thor) + new Vector2(0f, k * 1.1f);
            float a = step * 0.9f;
            Fx.Lightning(c + new Vector2(Mathf.Cos(a), Mathf.Sin(a) * 0.5f) * 1.1f, c + new Vector2(Mathf.Cos(a + 1.2f), Mathf.Sin(a + 1.2f) * 0.5f) * 1.1f, Bolt, 0.15f, 0, 0.2f);
            if (step % 4 == 0) Sfx.Play(SfxKind.Crackle, 0.8f + k * 0.6f, tp, 0.45f, cinematic: true);
            yield return d.Wait(0.09f);
        }
        d.Pan(tp + new Vector2(0f, 1.1f), 0.5f);
        Sfx.Play(SfxKind.Rise, 0.6f, tp, 0.9f, cinematic: true);
        yield return d.Wait(0.6f);

        // the column: a wall of stacked bolts from the clouds to the target
        d.Pan(vp, 0.4f);
        d.Zoom(1.2f, 0.6f);
        for (int i = 0; i < 7; i++)
        {
            float x = (i - 3) * 0.18f;
            Fx.Beam(vp + new Vector2(x, 9f), vp + new Vector2(x, -0.2f), i == 3 ? Color.white : Bolt, 0.35f, 1.6f * d.TimeScale);
            Fx.Lightning(vp + new Vector2(x, 9f), vp + new Vector2(x * 2f, 0f), Bolt, 1.2f * d.TimeScale, 2, 0.5f);
        }
        Fx.Flash(new Color(0.9f, 0.95f, 1f, 0.85f), 0.35f);
        Sfx.Play(SfxKind.Thunder, 0.4f, vp, 1f, cinematic: true);
        Sfx.Play(SfxKind.Stab, 0.6f, vp, 0.8f, cinematic: true);
        Fx.Release(vp, Bolt, ImpactStyle.Electric, 2.8f, cinematic: true);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);

        // he drops: a final shockwave across the floor
        d.Pan(tp, 0.3f);
        for (float k = 1f; k >= 0f; k -= 0.25f) { CinematicDirector.Pose(thor, k * 1.1f, 0f); yield return d.Wait(0.03f); }
        CinematicDirector.Pose(thor, 0f, 0f);
        Fx.Release(tp, Storm, ImpactStyle.Heavy, 2.4f, cinematic: true);
        Fx.ShockRing(tp, Color.white, 10f, 0.9f, squash: 0.5f);
        Fx.Cracks(tp, Bolt, 18, 4f, 2.5f * d.TimeScale, ctx.Rng.Next());
        Fx.Shake(0.9f, 1f);
        yield return d.Wait(1.2f);
        Fx.Aftermath(tp, Bolt, Residue.Static, 2f);
        yield return d.Wait(0.4f);
    }
}

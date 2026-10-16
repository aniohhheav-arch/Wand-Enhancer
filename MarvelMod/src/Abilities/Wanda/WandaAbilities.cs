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

namespace MarvelMod.Abilities.Wanda;

/// <summary>
/// Scarlet Witch: unstable red chaos magic. Visual language: wavy red tendrils (never straight beams),
/// hex sigils, floating debris, reality cracking like glass. Sounds: reversed swells, sizzle, stabs.
/// </summary>
public abstract class WandaAbility : AbilityBase
{
    public static readonly Color Scarlet = new(0.92f, 0.1f, 0.22f, 1f);
    public static readonly Color Hex = new(1f, 0.35f, 0.45f, 1f);
    public static readonly Color Deep = new(0.45f, 0.02f, 0.1f, 1f);

    public override string Category => "Chaos Magic";
    public override Color CastColor => Scarlet;

    /// <summary>Gesture: red energy curls around both hands before every spell.</summary>
    protected override void BeforeCast(CastContext ctx)
    {
        foreach (var side in new[] { -1f, 1f })
        {
            var hand = ctx.CasterPos + new Vector2(0.4f * side, 0.05f);
            Fx.Tendril(hand + new Vector2(0.3f * side, 0.4f), hand, Hex, 0.35f, 6);
            Fx.Flare(hand, Scarlet, 0.35f, 0.25f);
        }
    }

    protected static void Hit(CastContext ctx, PlayerControl pc, Vector2 from, float stun) =>
        DamageSystem.Instance?.ApplyDamage(pc, from, ctx.Cfg.Damage, DamageType.Magical, stun);

    protected static Vector2 AimPoint(PlayerControl caster, PlayerControl? target, float range) =>
        target != null ? Players.Pos(target) : Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, range);
}

/// <summary>A wobbling hex-orb that curves to its target and bursts into chaos threads.</summary>
public class ChaosBolt : WandaAbility
{
    public override string Id => "chaos_bolt";
    public override string Name => "Chaos Bolt";
    public override string Label => "CHAOS BOLT";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = AimPoint(caster, target, Cfg.Range);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Fly(ctx));

    private IEnumerator Fly(CastContext ctx)
    {
        Vector2 from = ctx.CasterPos + new Vector2(0.35f, 0.05f), to = ctx.Point;
        var target = ctx.Target;
        var orb = Fx.MakeSprite(Sprites.Glow, from, Scarlet, 0.7f, "ChaosBoltOrb");
        var hex = Fx.MakeSprite(Sprites.Runes, from, Hex, 0.45f, "ChaosBoltHex");
        Fx.Trail(orb.transform, Scarlet, 1.2f, 0.035f);
        Sfx.Play(SfxKind.Sizzle, 1.2f * Sfx.Vary(), from, 0.5f);
        float dur = Mathf.Max(0.15f, Vector2.Distance(from, to) / 11f);
        var n = new Vector2(-(to - from).y, (to - from).x).normalized;
        float bend = (float)(ctx.Rng.NextDouble() * 2 - 1) * 0.9f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (orb == null || hex == null) yield break;
            if (target != null && Players.IsAlive(target)) to = Players.Pos(target);   // homing
            float k = t / dur;
            var p = Vector2.Lerp(from, to, k) + n * (Mathf.Sin(k * Mathf.PI) * bend + Mathf.Sin(k * 25f) * 0.08f);
            orb.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            hex.transform.position = orb.transform.position - new Vector3(0, 0, 0.01f);
            hex.transform.Rotate(0, 0, 700f * Time.deltaTime);
            yield return null;
        }
        if (orb != null) Object.Destroy(orb.gameObject);
        if (hex != null) Object.Destroy(hex.gameObject);
        Fx.Release(to, Scarlet, ImpactStyle.Chaos, 0.9f);
        Fx.Aftermath(to, Scarlet, Residue.Embers, 0.6f);
        if (target != null && Players.IsAlive(target)) Hit(ctx, target, from, ctx.Cfg.Duration);
    }
}

/// <summary>Grab with telekinesis, hold them helpless in the air, then hurl them away.</summary>
public class TelekineticGrip : WandaAbility
{
    public override string Id => "telekinetic_grip";
    public override string Name => "Telekinetic Grip";
    public override string Label => "GRIP";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx) { if (ctx.Target != null) Coroutines.Start(Run(ctx, ctx.Target)); }

    private IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        var caster = ctx.Caster;
        float hold = Mathf.Max(0.6f, ctx.Cfg.Duration);
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Rooted, hold + 0.3f);
        StatusManager.Instance?.ApplyStatus(caster, StatusEffect.Rooted, hold + 0.2f);
        Coroutines.Start(Fx.Bind(caster.transform, target.transform, Scarlet, hold, 3));
        Sfx.Play(SfxKind.Reverse, 1.1f, target.transform.position, 0.6f);
        Fx.WorldText(target.transform, "!?", Hex, 1f, 3f);
        // lift and helplessly wobble in mid-air
        for (float t = 0; t < hold; t += Time.deltaTime)
        {
            if (target == null || !Players.IsAlive(target) || MeetingHud.Instance != null) { if (target != null) CharacterVisuals.Clear(target, "grip"); yield break; }
            float lift = Mathf.Min(1f, t / 0.3f) * 0.55f;
            CharacterVisuals.Set(target, "grip", lift: lift + Mathf.Sin(t * 9f) * 0.04f, tilt: Mathf.Sin(t * 6f) * 18f, maxSeconds: hold + 1f);
            yield return null;
        }
        if (target == null) yield break;
        CharacterVisuals.Clear(target, "grip");
        // throw: away from Wanda by the configured distance (Radius)
        Vector2 start = target.transform.position;
        var dir = (start - (Vector2)caster.transform.position).normalized;
        if (dir.sqrMagnitude < 0.01f) dir = Vector2.right;
        var land = Players.SafeDestination(start, dir, Mathf.Max(0.5f, ctx.Cfg.Radius));
        Sfx.Play(SfxKind.Whoosh, 0.8f, start, 0.7f);
        Fx.Streaks(start, Hex, 6, 1f);
        Fx.Comic(start + new Vector2(0f, 1f), "YEET!", Hex, 1.1f);
        Players.Teleport(target, land);
        Fx.Release(land, Scarlet, ImpactStyle.Heavy, 0.8f);
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Stunned, 0.8f);
    }
}

/// <summary>A red hex dome: blocks the next hostile ability (shield) for a while.</summary>
public class CrimsonBarrier : WandaAbility
{
    public override string Id => "crimson_barrier";
    public override string Name => "Crimson Barrier";
    public override string Label => "BARRIER";

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        StatusManager.Instance?.ApplyStatus(caster, StatusEffect.Shielded, ctx.Cfg.Duration);
        Fx.Sigil(ctx.CasterPos, Scarlet, 1.8f, 0.3f);
        Fx.Aura(caster.transform, Sprites.Glow, new Color(Scarlet.r, Scarlet.g, Scarlet.b, 0.25f), 1.9f, ctx.Cfg.Duration, 0f, 0f);
        Fx.Aura(caster.transform, Sprites.Runes, new Color(Hex.r, Hex.g, Hex.b, 0.55f), 1.8f, ctx.Cfg.Duration, 35f);
        Fx.Aura(caster.transform, Sprites.Ring, new Color(Hex.r, Hex.g, Hex.b, 0.7f), 2f, ctx.Cfg.Duration, -60f);
        Sfx.Play(SfxKind.Glass, 0.9f, ctx.CasterPos, 0.5f);
        Sfx.Play(SfxKind.Hum, 1.4f, ctx.CasterPos, 0.4f);
    }
}

/// <summary>
/// Hexes the minds of everyone nearby: each victim (on their own screen only) sees red static, a fake
/// lights-out, phantom crewmates who glitch out of existence. Nothing in the real game state changes.
/// </summary>
public class HexOfDeception : WandaAbility
{
    public override string Id => "hex_of_deception";
    public override string Name => "Hex of Deception";
    public override string Label => "HEX";

    protected override void OnCast(CastContext ctx)
    {
        Fx.Release(ctx.CasterPos, Scarlet, ImpactStyle.Glitch, 1f);
        var me = PlayerControl.LocalPlayer;
        if (me == null || me == ctx.Caster || !Players.IsAlive(me)) return;
        if (Vector2.Distance(Players.Pos(me), ctx.CasterPos) > ctx.Cfg.Range) return;
        Coroutines.Start(Vision(ctx, me));
    }

    private static IEnumerator Vision(CastContext ctx, PlayerControl me)
    {
        float dur = ctx.Cfg.Duration;
        Fx.Darken(0.55f, dur, new Color(0.15f, 0f, 0.02f));
        Fx.Toast("SOMETHING IS WRONG...", Hex, 2f, 3.6f);
        Sfx.Play(SfxKind.Reverse, 0.8f, null, 0.6f);
        var looks = new List<PlayerControl>();
        foreach (var pc in Players.All()) if (pc != me && Players.IsAlive(pc)) looks.Add(pc);
        var phantoms = new List<LokiIllusion>();
        for (int i = 0; i < 3 && looks.Count > 0; i++)
        {
            var ill = IllusionManager.Instance!.SpawnIllusion(looks[ctx.Rng.Next(looks.Count)], MapSpawnPoints.NearbyOpenPoint(Players.Pos(me), ctx.Rng, 1.2f, 3f), quiet: true);
            ill.SetBehavior(IllusionBehavior.Follow, me);
            ill.SetTint(new Color(1f, 0.6f, 0.65f, 0.85f));
            ill.Lifetime = dur;
            phantoms.Add(ill);
        }
        float glitch = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (MeetingHud.Instance != null || !Players.IsAlive(me)) break;
            glitch -= Time.deltaTime;
            if (glitch <= 0f)
            {
                glitch = Random.Range(0.4f, 0.9f);
                Fx.Release(Players.Pos(me) + Random.insideUnitCircle * 2f, Scarlet, ImpactStyle.Glitch, 0.5f);
            }
            yield return null;
        }
        foreach (var p in phantoms) if (p != null) { Fx.Release(p.transform.position, Scarlet, ImpactStyle.Glitch, 0.5f); p.Vanish(); }
    }
}

/// <summary>Ultimate: the arena becomes a chaos dimension — debris floats, reality cracks, and everyone inside is slowed and inverted.</summary>
public class RealityRewrite : WandaAbility
{
    public override string Id => "reality_rewrite";
    public override string Name => "Reality Rewrite";
    public override string Label => "REWRITE";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        Fx.Levitate(ctx.CasterPos, Scarlet, ctx.Cfg.Range * 0.5f, ctx.Cfg.CinematicDur + 0.5f, 10);
        CharacterVisuals.Set(caster, "rewrite", lift: 0.3f, maxSeconds: ctx.Cfg.CinematicDur + 1.5f);
        CinematicPlayer.Play("REALITY REWRITE", Scarlet, caster, ctx.Cfg.CinematicDur, () =>
        {
            CharacterVisuals.Clear(caster, "rewrite");
            Coroutines.Start(Field(ctx, caster.transform.position));
        });
    }

    private static IEnumerator Field(CastContext ctx, Vector2 c)
    {
        float r = ctx.Cfg.Range, dur = ctx.Cfg.Duration;
        Fx.Release(c, Scarlet, ImpactStyle.Chaos, 2.5f, cinematic: true);
        Fx.Cracks(c, Scarlet, 18, r, dur, ctx.Rng.Next());
        Fx.Levitate(c, Scarlet, r * 0.8f, dur, 16);
        Fx.Darken(0.3f, dur, new Color(0.3f, 0f, 0.05f));
        var dome = Fx.MakeSprite(Sprites.Runes, c, new Color(Scarlet.r, Scarlet.g, Scarlet.b, 0.35f), r * 2f, "RewriteDome");
        foreach (var pc in Players.InRadius(c, r, 15, ctx.Caster))
        {
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, dur);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.ChaosContract, dur);
            if (pc == PlayerControl.LocalPlayer) Fx.Toast("REALITY HAS BEEN REWRITTEN", Hex, 2.5f, 4f);
        }
        float fx = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (MeetingHud.Instance != null) break;
            if (dome != null) dome.transform.Rotate(0, 0, 12f * Time.deltaTime);
            fx -= Time.deltaTime;
            if (fx <= 0f)
            {
                fx = 0.5f;
                var a = c + Random.insideUnitCircle * r;
                Fx.Tendril(a, a + Random.insideUnitCircle * 1.5f, Scarlet, 0.6f);
                if (Random.value < 0.35f) Fx.Release(a, Scarlet, ImpactStyle.Glitch, 0.4f);
            }
            yield return null;
        }
        if (dome != null) Object.Destroy(dome.gameObject);
        Fx.Release(c, Hex, ImpactStyle.Cosmic, 1.5f);   // reality snaps back
    }
}

// ══ FINISHERS ═════════════════════════════════════════════════════════════════

public abstract class WandaFinisher : FinisherAbility
{
    public override string Category => "Wanda Finishers";
    public override Color CastColor => WandaAbility.Scarlet;
    protected static Color Scarlet => WandaAbility.Scarlet;
    protected static Color Hex => WandaAbility.Hex;

    protected static void HandsUp(PlayerControl wanda, float hold)
    {
        // both hands raised and glowing: one soft aura around the upper body plus a slow hex ring
        Fx.Aura(wanda.transform, Sprites.Glow, new Color(Scarlet.r, Scarlet.g, Scarlet.b, 0.55f), 0.9f, hold, 0f, 0.2f);
        Fx.Aura(wanda.transform, Sprites.Runes, new Color(Hex.r, Hex.g, Hex.b, 0.5f), 0.8f, hold, 90f, 0.2f);
    }
}

/// <summary>1. Scarlet energy spirals around the target, lifts them, a huge sigil forms, then a burst of red light.</summary>
public class CrimsonExecution : WandaFinisher
{
    public override string Id => "crimson_execution";
    public override string Name => "Crimson Execution";
    public override string Label => "EXECUTION";
    protected override float NominalSeconds => 8f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var wanda = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Players.Teleport(wanda, Players.SafeDestination(vp, Vector2.left, 1.8f));
        d.Zoom(0.6f, 0.8f);
        HandsUp(wanda, 7f * d.TimeScale);
        Sfx.Play(SfxKind.Reverse, 0.9f, vp, 0.6f, cinematic: true);
        yield return d.Wait(0.8f);

        // energy spirals in from four points and wraps the target
        for (int i = 0; i < 4; i++)
        {
            float a = i * Mathf.PI / 2f + 0.4f;
            Fx.Tendril(vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 2f, vp, Scarlet, 0.7f, 12);
            Sfx.Play(SfxKind.Sizzle, 0.9f + i * 0.1f, vp, 0.4f);
            yield return d.Wait(0.2f);
        }
        Reactor.Utilities.Coroutines.Start(Fx.Bind(wanda.transform, victim.transform, Scarlet, 4f * d.TimeScale, 3));
        d.React(victim, "!!");
        for (float k = 0; k <= 1f; k += 0.08f) { CinematicDirector.Pose(victim, k * 0.6f, Mathf.Sin(k * 12f) * 10f); yield return d.Wait(0.05f); }
        d.Pan(vp + new Vector2(0f, 0.6f), 0.8f);

        // a massive sigil assembles behind them
        Fx.Sigil(vp + new Vector2(0f, 0.6f), Scarlet, 3.4f, 2.2f * d.TimeScale);
        Fx.Levitate(vp, Scarlet, 2f, 3f * d.TimeScale, 8);
        Sfx.Play(SfxKind.Rise, 0.8f, vp, 0.7f, cinematic: true);
        d.Zoom(0.85f, 1f);
        yield return d.Wait(1.6f);

        yield return d.Wait(0.2f);   // the beat of silence
        Fx.Flash(new Color(1f, 0.2f, 0.25f, 0.6f), 0.3f);
        Fx.Release(vp + new Vector2(0f, 0.6f), Scarlet, ImpactStyle.Chaos, 2.6f, cinematic: true);
        Fx.Fragments(vp + new Vector2(0f, 0.6f), Scarlet, 60, 2f, 1.3f * d.TimeScale);
        Sfx.Play(SfxKind.Stab, 0.8f, vp, 0.8f, cinematic: true);
        CinematicDirector.Pose(victim, 0f, 0f);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);
        Fx.Aftermath(vp, Scarlet, Residue.Embers, 1.4f);
        yield return d.Wait(0.6f);
    }
}

/// <summary>2. The world warps into the House of M — floating scenery, red waves, Wanda's court — then the illusion collapses with them inside it.</summary>
public class HouseOfM : WandaFinisher
{
    public override string Id => "house_of_m";
    public override string Name => "House of M";
    public override string Label => "HOUSE OF M";
    protected override float NominalSeconds => 11f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var wanda = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        if (d.Participant)
        {
            Fx.ScreenTint(new Color(0.9f, 0.1f, 0.2f, 0.28f), 10f * d.TimeScale);
            Fx.Toast("HOUSE OF M", Scarlet, 2f * d.TimeScale, 5f, 2f);
        }
        d.Zoom(1.1f, 1.2f);
        for (int i = 0; i < 3; i++) { Fx.Wave(vp, new Color(Scarlet.r, Scarlet.g, Scarlet.b, 0.7f - i * 0.15f), 6f, (1f + i * 0.4f) * d.TimeScale); }
        Sfx.Play(SfxKind.Choir, 0.85f, vp, 0.6f, cinematic: true);
        Fx.Levitate(vp, Scarlet, 3.5f, 9f * d.TimeScale, 20);
        yield return d.Wait(1.4f);

        // a court of Wandas appears on a ring, the throne rises behind the target
        var throne = Prop("prop_throne", vp + new Vector2(0f, 2.2f), 0.1f);
        throne.color = new Color(1f, 0.55f, 0.6f, 1f);
        Fx.Animate(throne, 8f * d.TimeScale, k => throne.transform.localScale = Vector3.one * Mathf.Min(1.4f, k * 14f));
        var court = new List<LokiIllusion>();
        for (int i = 0; i < 8; i++)
        {
            float a = i * Mathf.PI / 4f;
            var c = Clone(wanda, vp + new Vector2(Mathf.Cos(a) * 2.6f, Mathf.Sin(a) * 1.8f), 8f * d.TimeScale);
            c.SetTint(new Color(1f, 0.6f, 0.65f, 0.75f));
            c.Face(Mathf.Cos(a) < 0f);
            court.Add(c);
            yield return d.Wait(0.1f);
        }
        d.React(victim, "where am I?");
        d.Orbit(vp, 1f, 220f, 2.6f);
        for (int i = 0; i < 4; i++)
        {
            Fx.Release(vp + Random.insideUnitCircle * 3f, Scarlet, ImpactStyle.Glitch, 0.6f);
            yield return d.Wait(0.6f);
        }

        // the illusion collapses: everything implodes onto the target
        Sfx.Play(SfxKind.Reverse, 0.6f, vp, 0.9f, cinematic: true);
        foreach (var c in court) if (c != null) c.MoveTo(vp, 7f);
        if (throne != null) Fx.Animate(throne, 0.6f * d.TimeScale, k => throne.transform.localScale *= 0.9f);
        d.Zoom(0.5f, 0.6f);
        yield return d.Wait(0.6f);
        foreach (var c in court) if (c != null) c.Pop();
        Fx.Release(vp, Scarlet, ImpactStyle.Cosmic, 2.4f, cinematic: true);
        Fx.Fragments(vp, Hex, 70, 2.4f, 1.2f * d.TimeScale);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);
        Fx.WorldText(wanda.transform, "<i>\"No more.\"</i>", Hex, 1.2f * d.TimeScale, 2.4f);
        yield return d.Wait(1f);
    }
}

/// <summary>3. Red threads from nowhere hook the target; Wanda conducts them like a puppet, then releases a burst.</summary>
public class PuppetOfChaos : WandaFinisher
{
    public override string Id => "puppet_of_chaos";
    public override string Name => "Puppet of Chaos";
    public override string Label => "PUPPET";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var wanda = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Players.Teleport(wanda, Players.SafeDestination(vp, Vector2.left, 2f));
        d.Zoom(0.65f, 0.8f);
        HandsUp(wanda, 8f * d.TimeScale);
        yield return d.Wait(0.6f);

        // threads drop from above and hook in, one by one
        for (int i = 0; i < 5; i++)
        {
            var top = vp + new Vector2(-0.6f + i * 0.3f, 3f);
            Fx.Tendril(top, vp + new Vector2(-0.3f + i * 0.15f, 0.3f), Hex, 0.6f, 10);
            Sfx.Play(SfxKind.Pop, 1.2f + i * 0.1f, vp, 0.35f);
            yield return d.Wait(0.15f);
        }
        d.React(victim, "?!");
        float tick = 0f;
        // the dance: jerked up, spun, dragged from side to side as Wanda moves her hands
        var moves = new[] { (0.4f, -30f), (0.55f, 25f), (0.3f, -45f), (0.7f, 360f), (0.45f, 15f), (0.6f, -20f) };
        foreach (var (lift, tilt) in moves)
        {
            CinematicDirector.Pose(victim, lift, tilt);
            d.TurnAround(victim);
            Sfx.Play(SfxKind.Creak, 1.4f - lift * 0.5f, vp, 0.4f);
            Sfx.Play(SfxKind.Boing, 1.6f + lift, vp, 0.25f);
            for (float t = 0; t < 0.35f * d.TimeScale; t += Time.deltaTime)
            {
                tick -= Time.deltaTime;
                if (tick <= 0f) { tick = 0.12f; Fx.Tendril(vp + new Vector2(Random.Range(-0.4f, 0.4f), 3f), Pos(victim) + new Vector2(0f, lift), Hex, 0.25f, 8); }
                yield return null;
            }
        }
        d.Pan(vp + new Vector2(0f, 0.6f), 0.5f);
        Fx.WorldText(wanda.transform, "*cuts the strings*", Hex, 1f * d.TimeScale, 2.4f);
        yield return d.Wait(0.6f);
        Fx.Release(vp + new Vector2(0f, 0.5f), Scarlet, ImpactStyle.Chaos, 2f, cinematic: true);
        Fx.Flash(new Color(1f, 0.3f, 0.35f, 0.5f), 0.25f);
        CinematicDirector.Pose(victim, 0f, 0f);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);
        Fx.Aftermath(vp, Hex, Residue.Smoke, 1f);
        yield return d.Wait(0.4f);
    }
}

/// <summary>4. The surroundings crack like glass and show alternate versions of the arena; the cracks converge on the target.</summary>
public class RealityFracture : WandaFinisher
{
    public override string Id => "reality_fracture";
    public override string Name => "Reality Fracture";
    public override string Label => "FRACTURE";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var wanda = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        d.Zoom(0.75f, 0.8f);
        Sfx.Play(SfxKind.Creak, 0.8f, vp, 0.6f);
        // cracks crawl outward in three waves with glass creaks
        for (int i = 0; i < 3; i++)
        {
            Fx.Cracks(vp, Hex, 8, 1.2f + i * 1f, 7f * d.TimeScale, ctx.Rng.Next());
            Sfx.Play(SfxKind.Glass, 1.4f - i * 0.2f, vp, 0.5f);
            yield return d.Wait(0.45f);
        }
        // alternate versions of the target flicker inside the cracks
        var alts = new List<LokiIllusion>();
        Color[] tints = { new(0.5f, 0.7f, 1f, 0.7f), new(0.5f, 1f, 0.6f, 0.7f), new(0.8f, 0.5f, 1f, 0.7f), new(1f, 0.9f, 0.4f, 0.7f) };
        for (int i = 0; i < 4; i++)
        {
            float a = i * Mathf.PI / 2f + 0.7f;
            var c = Clone(victim, vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.8f, 6f * d.TimeScale);
            c.SetTint(tints[i]);
            alts.Add(c);
            Fx.Release(c.transform.position, tints[i], ImpactStyle.Glitch, 0.5f);
            yield return d.Wait(0.25f);
        }
        d.React(victim, "...me?");
        d.Orbit(vp, 0.9f, 200f, 2f);
        float flicker = 0f;
        for (float t = 0; t < 2f * d.TimeScale; t += Time.deltaTime)
        {
            flicker -= Time.deltaTime;
            if (flicker <= 0f)
            {
                flicker = 0.18f;
                var pick = alts[Random.Range(0, alts.Count)];
                if (pick != null) pick.SetTint(tints[Random.Range(0, tints.Length)]);
            }
            yield return null;
        }
        // the cracks converge: every version is dragged into the one in the middle
        foreach (var c in alts) if (c != null) c.MoveTo(vp, 6f);
        Fx.Release(vp, Hex, ImpactStyle.Cosmic, 1.2f);
        yield return d.Wait(0.5f);
        foreach (var c in alts) if (c != null) c.Pop();
        Fx.Release(vp, Scarlet, ImpactStyle.Glitch, 2.4f, cinematic: true);
        Fx.Fragments(vp, new Color(1f, 0.8f, 0.85f), 70, 2f, 1.2f * d.TimeScale);
        Sfx.Play(SfxKind.Glass, 0.5f, vp, 0.9f, cinematic: true);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.4f);
    }
}

/// <summary>5. Ultimate: "No more." The scene freezes, red runes spread across the screen, the world folds inward and detonates.</summary>
public class NoMore : WandaFinisher
{
    public override string Id => "no_more";
    public override string Name => "No More";
    public override string Label => "NO MORE";
    public override bool IsUltimate => true;
    protected override float NominalSeconds => 12f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var wanda = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Vector2 wp = Pos(wanda);
        d.Zoom(0.6f, 0.8f);
        d.Pan(wp, 0.6f);
        CharacterVisuals.Set(wanda, "nomore", lift: 0.25f, maxSeconds: 14f * d.TimeScale);
        HandsUp(wanda, 10f * d.TimeScale);
        Fx.WorldText(wanda.transform, "<i>\"No more.\"</i>", Hex, 1.6f * d.TimeScale, 3f);
        Sfx.Play(SfxKind.Choir, 0.6f, wp, 0.6f, cinematic: true);
        yield return d.Wait(1.6f);

        // everything freezes: grey-out, a held breath
        StatusManager.Instance?.ApplyStatus(victim, StatusEffect.Frozen, 9f * d.TimeScale);
        if (d.Participant) Fx.ScreenTint(new Color(0.5f, 0.5f, 0.55f, 0.35f), 8f * d.TimeScale);
        if (d.Participant) Sfx.StopAll();   // the held breath (only for the two people in the scene)
        yield return d.Wait(0.8f);

        // red runes spread across the whole view
        var cam = Camera.main;
        float h = cam != null ? cam.orthographicSize : 3f, w = h * 1.8f;
        Vector2 cc = cam != null ? (Vector2)cam.transform.position : vp;
        for (int i = 0; i < 12; i++)
        {
            Fx.Sigil(cc + new Vector2(Random.Range(-w, w) * 0.8f, Random.Range(-h, h) * 0.8f), Scarlet, Random.Range(0.8f, 1.6f), 4f * d.TimeScale);
            Sfx.Play(SfxKind.Tick, 0.6f + i * 0.08f, null, 0.3f, cinematic: true);
            yield return d.Wait(0.12f);
        }
        d.Zoom(1.2f, 1.2f);
        Fx.Levitate(vp, Scarlet, 4f, 5f * d.TimeScale, 22);
        Sfx.Play(SfxKind.Rise, 0.6f, vp, 0.8f, cinematic: true);
        yield return d.Wait(1.4f);

        // the world folds inward: huge rings collapse onto the target, one after another
        for (int i = 0; i < 4; i++)
        {
            var ring = Fx.MakeSprite(Sprites.Ring, vp, new Color(Scarlet.r, Scarlet.g, Scarlet.b, 0.8f), 8f - i);
            float s0 = 8f - i;
            Fx.Animate(ring, 0.7f * d.TimeScale, k => { ring.transform.localScale = Vector3.one * s0 * (1f - k * k); ring.transform.Rotate(0, 0, 200f * Time.deltaTime); });
            Sfx.Play(SfxKind.Warp, 0.6f + i * 0.15f, vp, 0.5f, cinematic: true);
            yield return d.Wait(0.25f);
        }
        d.Zoom(0.5f, 0.5f);
        yield return d.Wait(0.6f);
        yield return d.Wait(0.25f);   // silence

        CharacterVisuals.Clear(wanda, "nomore");
        Fx.Flash(new Color(1f, 0.15f, 0.2f, 0.75f), 0.4f);
        Fx.Release(vp, Scarlet, ImpactStyle.Chaos, 3f, cinematic: true);
        Fx.Release(vp, Color.white, ImpactStyle.Cosmic, 2f, cinematic: true);
        Fx.Cracks(vp, Scarlet, 20, 5f, 2.5f * d.TimeScale, ctx.Rng.Next());
        Sfx.Play(SfxKind.Stab, 0.6f, vp, 1f, cinematic: true);
        Sfx.Play(SfxKind.Thunder, 0.7f, vp, 0.7f, cinematic: true);
        Fx.Shake(1f, 1f);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.6f);
        Fx.Aftermath(vp, Scarlet, Residue.Embers, 2f);
        yield return d.Wait(0.6f);
    }
}

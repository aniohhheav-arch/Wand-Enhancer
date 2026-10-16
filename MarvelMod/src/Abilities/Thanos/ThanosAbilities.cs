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

namespace MarvelMod.Abilities.Thanos;

/// <summary>Base for Thanos abilities: stone colour, sound and gauntlet pulse on every cast.</summary>
public abstract class ThanosAbility : AbilityBase
{
    public abstract Stone Stone { get; }
    public override string Category => Stone.ToString();
    public override Color CastColor => Stones.Color(Stone);
    protected Color C => Stones.Color(Stone);

    protected override void BeforeCast(CastContext ctx)
    {
        // activation sound + motif come from SoundBook / Fx.Motif (unique per ability)
        if (ctx.LocalIs(ctx.Caster)) ThanosCharacter.Instance.Pulse(Stone);
    }

    /// <summary>Ultimate: the stone's energy travels into the gauntlet, then the staged wind-up, then `climax`.</summary>
    protected void Ultimate(CastContext ctx, string title, System.Action climax)
    {
        var caster = ctx.Caster;
        Coroutines.Start(CoUltimate(caster, title, ctx.Cfg.CinematicDur, climax));
    }

    private IEnumerator CoUltimate(PlayerControl caster, string title, float dur, System.Action climax)
    {
        yield return Fx.StoneToGauntlet(caster.transform, C, 0.5f);
        if (caster == null || !Players.IsAlive(caster) || MeetingHud.Instance != null) yield break;
        CinematicPlayer.Play(title, C, caster, dur, climax);
    }

    /// <summary>Power Overload makes everything stun 50 % longer.</summary>
    protected static float Boost(CastContext ctx) =>
        StatusManager.Instance != null && StatusManager.Instance.HasStatus(ctx.Caster, StatusEffect.Haste) ? 1.5f : 1f;

    protected static void Hit(CastContext ctx, PlayerControl pc, Vector2 from, float stun) =>
        DamageSystem.Instance?.ApplyDamage(pc, from, ctx.Cfg.Damage, DamageType.Magical, stun * Boost(ctx));

    protected static List<PlayerControl> Others(CastContext ctx, Vector2 center, float radius) =>
        Players.InRadius(center, radius, 15, ctx.Caster);

    /// <summary>Players within `width` of the segment a→b (beams).</summary>
    protected static List<PlayerControl> OnLine(CastContext ctx, Vector2 a, Vector2 b, float width)
    {
        var list = new List<PlayerControl>();
        var dir = b - a;
        float len = dir.magnitude;
        if (len < 0.01f) return list;
        dir /= len;
        foreach (var pc in Players.All())
        {
            if (pc == ctx.Caster || !Players.IsAlive(pc)) continue;
            var v = Players.Pos(pc) - a;
            float along = Vector2.Dot(v, dir);
            if (along < 0f || along > len) continue;
            if ((v - dir * along).magnitude <= width) list.Add(pc);
        }
        return list;
    }

    /// <summary>Aim at the closest target in range, else the walking direction.</summary>
    protected Vector2 AimDir(PlayerControl caster, PlayerControl? target) =>
        target != null ? (Players.Pos(target) - (Vector2)caster.transform.position).normalized : AbilityButtons.LastMoveDirection;
}

// ══ POWER (purple) ═══════════════════════════════════════════════════════════

public class PowerBlast : ThanosAbility
{
    public override Stone Stone => Stone.Power;
    public override string Id => "power_blast";
    public override string Name => "Power Blast";
    public override string Label => "BLAST";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
    {
        var dir = AimDir(caster, target);
        point = target != null ? Players.Pos(target) : Players.SafeDestination(caster.transform.position, dir, Cfg.Range);
    }

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target; var from = ctx.CasterPos;
        // fast jab: muzzle flare at the fist, a heavy purple slug, a crunchy floor impact
        Fx.Flare(from + new Vector2(0.35f, 0f), C, 0.8f, 0.15f);
        Sfx.Play(SfxKind.Whoosh, 0.8f, from, 0.5f);
        Fx.Projectile(from, ctx.Point, C, 16f, () =>
        {
            Fx.Release(ctx.Point, C, ImpactStyle.Heavy, 0.8f);
            Fx.Aftermath(ctx.Point, C, Residue.Smoke, 0.6f);
            if (target != null) Hit(ctx, target, from, ctx.Cfg.Duration);
        });
    }
}

public class CosmicBeam : ThanosAbility
{
    public override Stone Stone => Stone.Power;
    public override string Id => "cosmic_beam";
    public override string Name => "Cosmic Beam";
    public override string Label => IsOn(PlayerControl.LocalPlayer) ? "STOP BEAM" : "BEAM";
    public override TargetMode Targeting => TargetMode.Optional;
    /// <summary>Press to fire, press again to stop. Duration is the maximum time it can stay on.</summary>
    public override bool IsToggle => true;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = Players.SafeDestination(caster.transform.position, AimDir(caster, target), Cfg.Range);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var caster = ctx.Caster;
        // charge: purple energy gathers into the gauntlet
        yield return Fx.StoneToGauntlet(caster.transform, C, 0.3f);
        yield return Fx.Charge(caster.transform, C, 0.45f, 10, SfxKind.Rise);
        if (caster == null) { ToggleEnded(caster); yield break; }
        Vector2 dir = (ctx.Point - ctx.CasterPos).normalized;
        if (dir.sqrMagnitude < 0.01f) dir = Vector2.right;
        float hitTimer = 0f, drawTimer = 0f, hum = 0f;
        Fx.Shake(0.3f, 0.4f);
        Sfx.Play(SfxKind.Zap, 0.6f, ctx.CasterPos);
        // sustained: the beam stays on, swings toward the closest player in range, and hits along its length
        for (float t = 0; t < ctx.Cfg.Duration; t += Time.deltaTime)
        {
            if (!IsOn(caster) || caster == null || !Players.IsAlive(caster) || MeetingHud.Instance != null) break;
            StatusManager.Instance?.ApplyStatus(caster, StatusEffect.Rooted, 0.3f);
            Vector2 from = caster.transform.position;
            var lock_ = Players.ClosestTarget(caster, ctx.Cfg.Range);
            if (lock_ != null) dir = Vector2.Lerp(dir, (Players.Pos(lock_) - from).normalized, 4f * Time.deltaTime).normalized;
            var end = Players.SafeDestination(from, dir, ctx.Cfg.Range);
            drawTimer -= Time.deltaTime;
            if (drawTimer <= 0f) { drawTimer = 0.12f; Fx.Beam(from, end, C, 0.55f + Mathf.Sin(t * 30f) * 0.08f, 0.16f); Fx.Sparkles(end, C, 2, 0.4f, 0.25f); }
            hum -= Time.deltaTime;
            if (hum <= 0f) { hum = 0.45f; Sfx.Play(SfxKind.Hum, 0.7f, from, 0.35f); }
            hitTimer -= Time.deltaTime;
            if (hitTimer <= 0f)
            {
                hitTimer = 0.5f;
                foreach (var pc in OnLine(ctx, from, end, 0.55f)) Hit(ctx, pc, from, 0.6f);
            }
            yield return null;
        }
        if (caster != null)
        {
            Fx.Burst(caster.transform.position, C, 1.2f, 0.3f);
            Fx.Aftermath(caster.transform.position, C, Residue.Embers, 0.8f);
            Sfx.Play(SfxKind.Thud, 0.8f, caster.transform.position, 0.5f);
        }
        ToggleEnded(caster);
    }
}

public class Shockwave : ThanosAbility
{
    public override Stone Stone => Stone.Power;
    public override string Id => "shockwave";
    public override string Name => "Shockwave";
    public override string Label => "SLAM";

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    /// <summary>Hop up, hang for a beat, then slam the gauntlet into the floor.</summary>
    private IEnumerator Run(CastContext ctx)
    {
        var caster = ctx.Caster;
        StatusManager.Instance?.ApplyStatus(caster, StatusEffect.Rooted, 0.5f);
        Sfx.Play(SfxKind.Whoosh, 0.7f, ctx.CasterPos, 0.5f);
        for (float t = 0; t < 0.3f; t += Time.deltaTime)
        {
            if (caster == null) yield break;
            float k = t / 0.3f;
            CharacterVisuals.Set(caster, "slam", lift: Mathf.Sin(k * Mathf.PI * 0.5f) * 0.45f, maxSeconds: 1f);
            yield return null;
        }
        yield return new WaitForSeconds(0.08f);
        if (caster == null) yield break;
        CharacterVisuals.Clear(caster, "slam");
        var c = (Vector2)caster.transform.position;
        Fx.Release(c, C, ImpactStyle.Heavy, 1.6f);
        Fx.Wave(c, C, ctx.Cfg.Range, 0.45f);
        Fx.Aftermath(c, C, Residue.Debris, 1.2f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range)) Hit(ctx, pc, c, ctx.Cfg.Duration);
    }
}

public class PowerOverload : ThanosAbility
{
    public override Stone Stone => Stone.Power;
    public override string Id => "power_overload";
    public override string Name => "Power Overload";
    public override string Label => "OVERLOAD";

    protected override void OnCast(CastContext ctx)
    {
        StatusManager.Instance?.ApplyStatus(ctx.Caster, StatusEffect.Haste, ctx.Cfg.Duration);
        Coroutines.Start(Run(ctx));
        if (ctx.LocalIs(ctx.Caster)) Fx.Toast("POWER OVERLOAD: FASTER, STRONGER", C, 2f, 3.5f);
    }

    /// <summary>Flex, purple lightning bursts off him, then crackles around him while it lasts.</summary>
    private IEnumerator Run(CastContext ctx)
    {
        var caster = ctx.Caster;
        CharacterVisuals.Set(caster, "overload", scale: 1.12f, maxSeconds: ctx.Cfg.Duration + 0.5f);
        Fx.Release(ctx.CasterPos, C, ImpactStyle.Electric, 1.2f);
        Sfx.Play(SfxKind.Stab, 1.1f, ctx.CasterPos, 0.6f);
        float arc = 0f;
        for (float t = 0; t < ctx.Cfg.Duration; t += Time.deltaTime)
        {
            if (caster == null || !Players.IsAlive(caster) || MeetingHud.Instance != null) break;
            arc -= Time.deltaTime;
            if (arc <= 0f)
            {
                arc = 0.35f;
                Vector2 p = caster.transform.position;
                float a = Random.Range(0f, Mathf.PI * 2f);
                Fx.Lightning(p + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 0.2f, p + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 0.9f, C, 0.15f, 1, 0.15f);
                Fx.Rising(p, C, 2, 0.6f, 0.6f);
            }
            yield return null;
        }
        if (caster != null) CharacterVisuals.Clear(caster, "overload");
    }
}

public class Planetbreaker : ThanosAbility
{
    /// <summary>A sphere of purple energy that grows above Thanos, compresses, and is slammed down.</summary>
    private IEnumerator Moon(PlayerControl caster, float dur)
    {
        var orb = Fx.MakeSprite(Sprites.Glow, caster.transform.position, new Color(C.r, C.g, C.b, 0f), 0.2f, "PlanetMoon");
        var ring = Fx.MakeSprite(Sprites.Ring, caster.transform.position, new Color(1f, 1f, 1f, 0f), 0.2f, "PlanetMoonRing");
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (caster == null || orb == null || ring == null) break;
            float k = t / dur;
            float size = k < 0.8f ? Mathf.Lerp(0.2f, 2.6f, k / 0.8f) : Mathf.Lerp(2.6f, 1.2f, (k - 0.8f) / 0.2f);   // grow, then compress
            var p = (Vector2)caster.transform.position + new Vector2(0f, 1.6f + 0.1f * Mathf.Sin(t * 6f));
            orb.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.7f);
            ring.transform.position = orb.transform.position + new Vector3(0, 0, -0.01f);
            orb.transform.localScale = Vector3.one * size;
            ring.transform.localScale = Vector3.one * size * 1.1f;
            ring.transform.Rotate(0, 0, 200f * Time.deltaTime);
            orb.color = new Color(C.r, C.g, C.b, Mathf.Min(0.95f, k * 2f));
            ring.color = new Color(1f, 1f, 1f, Mathf.Min(0.6f, k));
            yield return null;
        }
        if (orb != null) Object.Destroy(orb.gameObject);
        if (ring != null) Object.Destroy(ring.gameObject);
    }

    public override Stone Stone => Stone.Power;
    public override string Id => "planetbreaker";
    public override string Name => "Planetbreaker";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        // float into the air (restored by CharacterVisuals even if the wind-up is interrupted)
        CharacterVisuals.Set(caster, "planetbreaker", lift: 0.4f, maxSeconds: ctx.Cfg.CinematicDur + 2f);
        // the floor comes apart and floats up around him while a purple "moon" condenses overhead
        Fx.Levitate(ctx.CasterPos, C, ctx.Cfg.Range * 0.6f, ctx.Cfg.CinematicDur + 0.8f, 14);
        Coroutines.Start(Moon(caster, ctx.Cfg.CinematicDur + 0.6f));
        Ultimate(ctx, "PLANETBREAKER", () =>
        {
            CharacterVisuals.Clear(caster, "planetbreaker");
            Vector2 c = caster.transform.position;
            Fx.Release(c, C, ImpactStyle.Heavy, 2.5f, cinematic: true);
            Fx.Wave(c, C, ctx.Cfg.Range, 0.6f);
            Fx.Cracks(c, C, 16, ctx.Cfg.Range, 2.2f, ctx.Rng.Next());
            Fx.ShockRing(c, Color.white, ctx.Cfg.Range * 2.2f, 0.9f, squash: 0.55f);
            Fx.Aftermath(c, C, Residue.Debris, 2f);
            Fx.Aftermath(c, C, Residue.Embers, 1.5f);
            Fx.Shake(0.8f, 1.2f);
            foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
            {
                if (ctx.Cfg.Damage > 0f && Vector2.Distance(c, Players.Pos(pc)) <= ctx.Cfg.Radius) Kills.Kill(caster, pc);
                else Hit(ctx, pc, c, 2f);
            }
        });
    }
}

// ══ SPACE (blue) ═════════════════════════════════════════════════════════════

public class SpaceBlink : ThanosAbility
{
    public override Stone Stone => Stone.Space;
    public override string Id => "space_blink";
    public override string Name => "Blink";
    public override string Label => "BLINK";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, Cfg.Range);

    protected override void OnCast(CastContext ctx)
    {
        // folds space: implodes where he stood, a thin space-tear streak, pops out the other end
        Fx.Release(ctx.CasterPos, C, ImpactStyle.Cosmic, 0.6f);
        Fx.Beam(ctx.CasterPos, ctx.Point, Color.Lerp(C, Color.white, 0.5f), 0.08f, 0.25f);
        Fx.ShockRing(ctx.Point, C, 1.8f, 0.3f);
        Fx.Sparkles(ctx.Point, C, 8, 1f, 0.4f);
        Sfx.Play(SfxKind.Pop, 0.8f, ctx.Point, 0.6f);
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

/// <summary>Two linked portals (here and a random vent); anyone who steps in comes out the other side.</summary>
public class SpaceRift : ThanosAbility
{
    public override Stone Stone => Stone.Space;
    public override string Id => "space_rift";
    public override string Name => "Space Rift";
    public override string Label => "RIFT";
    public override int MapPoints => 2;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
    {
        point = PickedPoints.Count > 0 ? PickedPoints[0] : (Vector2)caster.transform.position;
        SecondPoint = PickedPoints.Count > 1 ? PickedPoints[1] : point + Vector2.right * 3f;
    }

    protected override void OnCast(CastContext ctx)
    {
        foreach (var p in new[] { ctx.Point, ctx.Point2 }) Fx.Release(p, C, ImpactStyle.Cosmic, 0.8f);
        Coroutines.Start(Portals.Run(ctx.Point, ctx.Point2, C, ctx.Cfg.Duration));
    }
}

public static class Portals
{
    private static readonly Dictionary<byte, float> Cooldown = new();

    /// <summary>A linked pair of portals on every client; each client teleports the players it controls.</summary>
    public static IEnumerator Run(Vector2 a, Vector2 b, Color color, float duration)
    {
        var rings = new List<SpriteRenderer>();
        foreach (var p in new[] { a, b })
        {
            var glow = Fx.MakeSprite(Sprites.Glow, p, new Color(color.r, color.g, color.b, 0.7f), 1.3f, "PortalGlow");
            var runes = Fx.MakeSprite(Sprites.Runes, p, color, 1.1f, "PortalRunes");
            glow.transform.localScale = new Vector3(1.1f, 1.4f, 1f);
            runes.transform.localScale = new Vector3(0.9f, 1.2f, 1f);
            rings.Add(glow); rings.Add(runes);
        }
        for (float t = 0; t < duration; t += Time.deltaTime)
        {
            if (MeetingHud.Instance != null) break;
            foreach (var r in rings) if (r != null && r.name == "PortalRunes") r.transform.Rotate(0, 0, 90f * Time.deltaTime);
            foreach (var pc in Players.All())
            {
                if (!Players.Controls(pc) || !Players.IsAlive(pc)) continue;
                if (Cooldown.TryGetValue(pc.PlayerId, out var until) && Time.time < until) continue;
                Vector2 pos = pc.transform.position;
                Vector2? exit = Vector2.Distance(pos, a) < 0.45f ? b : Vector2.Distance(pos, b) < 0.45f ? a : null;
                if (exit == null) continue;
                Cooldown[pc.PlayerId] = Time.time + 1.5f;
                var outDir = pc.MyPhysics != null && pc.MyPhysics.body != null && pc.MyPhysics.body.velocity.sqrMagnitude > 0.01f
                    ? pc.MyPhysics.body.velocity.normalized : Vector2.down;
                Players.Teleport(pc, Players.SafeDestination(exit.Value, outDir, 0.7f));
                Sfx.Play(SfxKind.Whoosh, 1.4f, exit.Value, 0.6f);
            }
            yield return null;
        }
        foreach (var r in rings)
            if (r != null)
            {
                var rr = r;
                Fx.Animate(rr, 0.3f, k => rr.transform.localScale *= 0.85f);
            }
    }
}

public class GravityWell : ThanosAbility
{
    public override Stone Stone => Stone.Space;
    public override string Id => "gravity_well";
    public override string Name => "Gravity Well";
    public override string Label => "GRAVITY";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, 2.5f);

    protected override void OnCast(CastContext ctx)
    {
        var p = ctx.Point;
        var core = Fx.MakeSprite(Sprites.Glow, p, new Color(0.02f, 0.03f, 0.1f, 0.95f), 0.9f);
        var ring = Fx.MakeSprite(Sprites.Ring, p, C, 1.6f);
        Fx.Animate(core, ctx.Cfg.Duration, k => core.transform.localScale = Vector3.one * (0.9f + 0.1f * Mathf.Sin(Time.time * 8f)));
        Fx.Animate(ring, ctx.Cfg.Duration, k =>
        {
            ring.transform.Rotate(0, 0, -400f * Time.deltaTime);
            ring.transform.localScale = Vector3.one * (1.6f - 0.4f * Mathf.Repeat(Time.time, 1f));
        });
        foreach (var pc in Others(ctx, p, ctx.Cfg.Range))
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Pulled, ctx.Cfg.Duration, p);
        Coroutines.Start(Swirl(p, ctx.Cfg.Duration, ctx.Cfg.Range));
    }

    /// <summary>Debris spirals into the singularity, a low drone pulses, then it collapses with a pop.</summary>
    private IEnumerator Swirl(Vector2 p, float dur, float range)
    {
        var anchor = new GameObject("GravityAnchor");
        anchor.transform.position = p;
        float mote = 0f, drone = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (MeetingHud.Instance != null) break;
            mote -= Time.deltaTime; drone -= Time.deltaTime;
            if (mote <= 0f) { mote = 0.06f; Fx.Debris(p + Random.insideUnitCircle.normalized * range * 0.8f, new Color(0.4f, 0.4f, 0.5f), 1, 0.2f); }
            if (drone <= 0f) { drone = 0.9f; Sfx.Play(SfxKind.Rumble, 1.2f, p, 0.35f); }
            yield return null;
        }
        Object.Destroy(anchor);
        Fx.Release(p, C, ImpactStyle.Cosmic, 1f);
    }
}

public class SpatialPrison : ThanosAbility
{
    public override Stone Stone => Stone.Space;
    public override string Id => "spatial_prison";
    public override string Name => "Spatial Prison";
    public override string Label => "PRISON";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // space folds into a glowing cube around them: rings snap inward, then a box seals shut
        Vector2 p = ctx.Target.transform.position;
        Fx.Beam(ctx.CasterPos, p, C, 0.2f, 0.4f);
        Fx.Release(p, C, ImpactStyle.Cosmic, 0.7f);
        var box = Fx.MakeSprite(Sprites.Box, p, new Color(C.r, C.g, C.b, 0f), 3f, "PrisonBox");
        float dur = ctx.Cfg.Duration * Boost(ctx);
        var target = ctx.Target;
        Fx.Animate(box, dur, k =>
        {
            if (target != null) box.transform.position = target.transform.position + new Vector3(0, 0, -0.05f);
            float seal = Mathf.Clamp01(k * dur / 0.25f);
            box.transform.localScale = Vector3.one * Mathf.Lerp(3f, 1.25f, seal);
            box.color = new Color(C.r, C.g, C.b, 0.75f * seal * (k > 0.9f ? (1f - k) * 10f : 1f));
        });
        Sfx.Play(SfxKind.Glass, 0.7f, p, 0.5f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Prison, dur);
    }
}

public class CosmicExile : ThanosAbility
{
    public override Stone Stone => Stone.Space;
    public override string Id => "cosmic_exile";
    public override string Name => "Cosmic Exile";
    public override string Label => "EXILE";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        int seed = ctx.Rng.Next();
        // a rift tears open behind the target during the wind-up; at the climax they're sucked through it
        Coroutines.Start(Rift(target, ctx.Cfg.CinematicDur + 1f));
        Ultimate(ctx, "COSMIC EXILE", () =>
        {
            if (target == null) return;
            Coroutines.Start(Suck(ctx, target, seed));
        });
    }

    private IEnumerator Rift(PlayerControl target, float dur)
    {
        var tear = Fx.MakeSprite(Sprites.Glow, target.transform.position, new Color(0.02f, 0.02f, 0.08f, 0f), 0.2f, "ExileRift");
        var rim = Fx.MakeSprite(Sprites.Ring, target.transform.position, new Color(C.r, C.g, C.b, 0f), 0.2f, "ExileRim");
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (target == null || tear == null || rim == null) break;
            float k = Mathf.Clamp01(t / (dur * 0.8f));
            var p = target.transform.position + new Vector3(0.5f, 0.3f, 0.02f);
            tear.transform.position = p; rim.transform.position = p - new Vector3(0, 0, 0.01f);
            tear.transform.localScale = new Vector3(0.6f, 1.8f, 1f) * k;
            rim.transform.localScale = new Vector3(0.7f, 2f, 1f) * k;
            rim.transform.Rotate(0, 0, 300f * Time.deltaTime);
            tear.color = new Color(0.02f, 0.02f, 0.08f, 0.95f * k);
            rim.color = new Color(C.r, C.g, C.b, 0.9f * k);
            if (Random.value < 0.15f) Fx.Debris((Vector2)target.transform.position + Random.insideUnitCircle * 1.5f, Color.gray, 1, 0.1f);
            yield return null;
        }
        if (tear != null) Object.Destroy(tear.gameObject);
        if (rim != null) Object.Destroy(rim.gameObject);
    }

    private IEnumerator Suck(CastContext ctx, PlayerControl target, int seed)
    {
        Sfx.Play(SfxKind.Warp, 0.6f, target.transform.position, 0.8f, cinematic: true);
        for (float t = 0; t < 0.45f; t += Time.deltaTime)
        {
            if (target == null) yield break;
            float k = t / 0.45f;
            CharacterVisuals.Set(target, "exile", tilt: k * 540f, scale: Mathf.Lerp(1f, 0.15f, k * k), maxSeconds: 2f);
            yield return null;
        }
        if (target == null) yield break;
        CharacterVisuals.Clear(target, "exile");
        Fx.Release(target.transform.position, C, ImpactStyle.Cosmic, 1.4f, cinematic: true);
        Dimensions.Scenery(Dimensions.Cosmic, C, ctx.Cfg.Duration + 1f, seed);
        Dimensions.Send(target, Dimensions.Cosmic, Vector2.zero, ctx.Cfg.Duration, new Color(0.15f, 0.25f, 0.9f), "EXILED TO THE COSMOS");
    }
}

// ══ REALITY (red) ════════════════════════════════════════════════════════════

public class RealityWarp : ThanosAbility
{
    public override Stone Stone => Stone.Reality;
    public override string Id => "reality_warp";
    public override string Name => "Reality Warp";
    public override string Label => "WARP";

    protected override void OnCast(CastContext ctx)
    {
        var c = ctx.CasterPos;
        // red aether ripples out in uneven, stuttering rings; everyone caught glitches
        for (int i = 0; i < 3; i++) Fx.Wave(c, new Color(C.r, C.g, C.b, 0.7f - i * 0.2f), ctx.Cfg.Range, 0.6f + i * 0.25f);
        Fx.Release(c, C, ImpactStyle.Glitch, 1.2f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
            Fx.Release(pc.transform.position, C, ImpactStyle.Glitch, 0.5f);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, ctx.Cfg.Duration);
            if (ctx.LocalIs(pc))
            {
                Fx.ScreenTint(new Color(1f, 0.1f, 0.15f, 0.35f), ctx.Cfg.Duration, pulses: 4);
                Fx.Shake(ctx.Cfg.Duration, 0.15f);
            }
        }
    }
}

public class Transmutation : ThanosAbility
{
    public override Stone Stone => Stone.Reality;
    public override string Id => "transmutation";
    public override string Name => "Object Transmutation";
    public override string Label => "CRATE";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // POOF: cartoon puff and a spring noise, and they're a crate now
        Vector2 p = ctx.Target.transform.position;
        Fx.Smoke(p, C, 10, 0.7f);
        Fx.Release(p, C, ImpactStyle.Comedy, 1f);
        Fx.Comic(p + new Vector2(0f, 0.9f), "POOF!", Color.white, 1.2f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Transmuted, ctx.Cfg.Duration);
    }
}

public class RealityShatter : ThanosAbility
{
    public override Stone Stone => Stone.Reality;
    public override string Id => "reality_shatter";
    public override string Name => "Reality Shatter";
    public override string Label => "SHATTER";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = Players.SafeDestination(caster.transform.position, AbilityButtons.LastMoveDirection, 2f);

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var p = ctx.Point;
        // telegraph: cracks creep out with glass creaks, then the whole patch of reality breaks
        Fx.Cracks(p, C, 9, ctx.Cfg.Radius, 1.3f, ctx.Rng.Next());
        for (int i = 0; i < 3; i++)
        {
            Sfx.Play(SfxKind.Creak, 1.2f + i * 0.2f, p, 0.4f);
            for (float t = 0; t < 0.33f; t += Time.deltaTime) yield return null;
        }
        Fx.Release(p, C, ImpactStyle.Glitch, ctx.Cfg.Radius);
        Fx.Fragments(p, C, 40, ctx.Cfg.Radius, 0.8f);
        Sfx.Play(SfxKind.Glass, 0.8f, p);
        foreach (var pc in Others(ctx, p, ctx.Cfg.Radius)) Hit(ctx, pc, p, ctx.Cfg.Duration);
    }
}

public class FalseWorld : ThanosAbility
{
    public override Stone Stone => Stone.Reality;
    public override string Id => "false_world";
    public override string Name => "False World";
    public override string Label => "FALSE WORLD";

    protected override void OnCast(CastContext ctx)
    {
        Fx.Wave(ctx.CasterPos, C, ctx.Cfg.Range, 0.7f);
        Fx.Release(ctx.CasterPos, C, ImpactStyle.Glitch, 0.9f);
        var me = PlayerControl.LocalPlayer;
        if (me == null || me == ctx.Caster || !Players.IsAlive(me)) return;
        if (Vector2.Distance(Players.Pos(me), ctx.CasterPos) > ctx.Cfg.Range) return;

        // only the victims see this fake version of the world
        Fx.ScreenTint(new Color(0.8f, 0.05f, 0.1f, 0.3f), ctx.Cfg.Duration, pulses: 2);
        Fx.Toast("IS THIS REAL?", C, 2f, 4f);
        var looks = new List<PlayerControl>();
        foreach (var pc in Players.All()) if (pc != me && Players.IsAlive(pc)) looks.Add(pc);
        for (int i = 0; i < 2 && looks.Count > 0; i++)
            FakeBodies.Spawn(looks[ctx.Rng.Next(looks.Count)], MapSpawnPoints.NearbyOpenPoint(Players.Pos(me), ctx.Rng, 1.5f, 3f), ctx.Cfg.Duration);
        for (int i = 0; i < 3 && looks.Count > 0; i++)
        {
            var ill = IllusionManager.Instance!.SpawnIllusion(looks[ctx.Rng.Next(looks.Count)], MapSpawnPoints.NearbyOpenPoint(Players.Pos(me), ctx.Rng, 1.5f, 3.5f));
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = ctx.Cfg.Duration;
        }
    }
}

public class RealityCollapse : ThanosAbility
{
    public override Stone Stone => Stone.Reality;
    public override string Id => "reality_collapse";
    public override string Name => "Reality Collapse";
    public override string Label => "COLLAPSE";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        int seed = ctx.Rng.Next();
        Ultimate(ctx, "REALITY COLLAPSE", () =>
        {
            Vector2 c = caster.transform.position;
            // the world turns red and breaks like a screen: shards, glitch storms, a slow wave of wrongness
            Fx.Cracks(c, C, 20, 6f, 2f, seed);
            Fx.Release(c, C, ImpactStyle.Glitch, 2.5f, cinematic: true);
            Fx.Darken(0.3f, 3f, new Color(0.25f, 0f, 0.02f));
            for (int i = 0; i < 6; i++) Fx.Release(c + Random.insideUnitCircle * ctx.Cfg.Range * 0.7f, C, ImpactStyle.Glitch, 0.8f);
            Sfx.Play(SfxKind.Reverse, 0.7f, c, 0.8f, cinematic: true);
            Fx.Wave(c, C, ctx.Cfg.Range, 3f, pc =>
            {
                if (pc == caster) return;
                Hit(ctx, pc, c, ctx.Cfg.Duration);
                if (pc == PlayerControl.LocalPlayer) Fx.ScreenTint(new Color(1f, 0.1f, 0.1f, 0.5f), 0.8f);
            });
        });
    }
}

// ══ SOUL (orange) ════════════════════════════════════════════════════════════

public class SoulSight : ThanosAbility
{
    public override Stone Stone => Stone.Soul;
    public override string Id => "soul_sight";
    public override string Name => "Soul Sight";
    public override string Label => "SOUL SIGHT";

    protected override void OnCast(CastContext ctx)
    {
        Fx.Release(ctx.CasterPos, C, ImpactStyle.Eerie, 0.7f);
        if (!ctx.LocalIs(ctx.Caster)) return;
        Sfx.Play(SfxKind.Heartbeat, 1f, null, 0.6f);
        foreach (var pc in Players.All())
            if (pc != ctx.Caster && Players.IsAlive(pc)) Fx.ScreenMarker(pc.transform, C, ctx.Cfg.Duration);
        Fx.ScreenTint(new Color(1f, 0.5f, 0.1f, 0.15f), ctx.Cfg.Duration);
    }
}

public class SoulDrain : ThanosAbility
{
    public override Stone Stone => Stone.Soul;
    public override string Id => "soul_drain";
    public override string Name => "Soul Drain";
    public override string Label => "DRAIN";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        // orange soul threads pulled out of the target into the gauntlet, with a slowing heartbeat
        Coroutines.Start(Fx.Bind(ctx.Caster.transform, target.transform, C, 1.2f, 2));
        for (int i = 0; i < 4; i++)
            Fx.Projectile(Players.Pos(target) + Random.insideUnitCircle * 0.3f, ctx.CasterPos, C, 6f + i, null);
        Sfx.Play(SfxKind.Heartbeat, 0.8f, Players.Pos(target), 0.6f);
        Fx.Aftermath(Players.Pos(target), C, Residue.Embers, 0.5f);
        StatusManager.Instance?.ApplyStatus(target, StatusEffect.Slowed, ctx.Cfg.Duration);
        if (!ctx.LocalIs(ctx.Caster)) return;
        foreach (var tab in ThanosCharacter.Instance.Tabs)
            foreach (var a in tab.Abilities)
                if (!a.IsUltimate && a != this) a.ReduceCooldown(0.5f);
        Fx.Toast("SOUL ENERGY: COOLDOWNS HALVED", C, 1.8f, 3.4f);
    }
}

/// <summary>Leave your body and scout with a spirit (WASD / arrows). Your body stays rooted.</summary>
public class SpiritProjection : ThanosAbility
{
    public override Stone Stone => Stone.Soul;
    public override string Id => "spirit_projection";
    public override string Name => "Spirit Projection";
    public override string Label => "SPIRIT";

    protected override void OnCast(CastContext ctx)
    {
        StatusManager.Instance?.ApplyStatus(ctx.Caster, StatusEffect.Rooted, ctx.Cfg.Duration);
        Fx.Aura(ctx.Caster.transform, Sprites.Glow, new Color(C.r, C.g, C.b, 0.5f), 1.4f, ctx.Cfg.Duration);
        Fx.Rising(ctx.CasterPos, C, 10, 0.8f, 1.4f);
        Sfx.Play(SfxKind.Choir, 1.3f, ctx.CasterPos, 0.3f);
        if (ctx.LocalIs(ctx.Caster)) Coroutines.Start(Run(ctx));
    }

    private static IEnumerator Run(CastContext ctx)
    {
        var hud = HudManager.Instance;
        var spirit = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, ctx.CasterPos, quiet: true);
        spirit.SetBehavior(IllusionBehavior.Controlled);
        spirit.Untouchable = true;
        spirit.Lifetime = ctx.Cfg.Duration + 1f;
        spirit.SetTint(new Color(1f, 0.65f, 0.25f, 0.55f));
        if (hud != null && hud.PlayerCam != null) hud.PlayerCam.SetTarget(spirit);
        Fx.Toast("SPIRIT: MOVE WITH WASD / ARROWS", Stones.Color(Stone.Soul), 2f, 3.4f);
        for (float t = 0; t < ctx.Cfg.Duration; t += Time.deltaTime)
        {
            if (MeetingHud.Instance != null || spirit == null || !Players.IsAlive(ctx.Caster)) break;
            yield return null;
        }
        if (spirit != null) spirit.Vanish();
        var lp = PlayerControl.LocalPlayer;
        if (hud != null && hud.PlayerCam != null && lp != null) hud.PlayerCam.SetTarget(lp);
    }
}

public class SoulChains : ThanosAbility
{
    public override Stone Stone => Stone.Soul;
    public override string Id => "soul_chains";
    public override string Name => "Soul Chains";
    public override string Label => "CHAINS";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // ghostly chains: threads stay wrapped around the target the whole time, clanking
        float dur = ctx.Cfg.Duration * Boost(ctx);
        Coroutines.Start(Fx.Bind(ctx.Caster.transform, ctx.Target.transform, C, Mathf.Min(dur, 1.5f), 3));
        Fx.Aura(ctx.Target.transform, Sprites.Runes, new Color(C.r, C.g, C.b, 0.6f), 1.1f, dur, 90f);
        Sfx.Play(SfxKind.Clang, 1.4f, ctx.Target.transform.position, 0.5f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Chained, dur);
    }
}

public class SoulWorld : ThanosAbility
{
    public override Stone Stone => Stone.Soul;
    public override string Id => "soul_world";
    public override string Name => "Soul World";
    public override string Label => "SOUL WORLD";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        int seed = ctx.Rng.Next();
        Fx.Rising(ctx.CasterPos, C, 20, ctx.Cfg.Range, ctx.Cfg.CinematicDur + 1f);
        Ultimate(ctx, "SOUL WORLD", () =>
        {
            Fx.Release(caster.transform.position, C, ImpactStyle.Eerie, 2.2f, cinematic: true);
            Fx.Flash(new Color(1f, 0.6f, 0.2f, 0.6f), 0.4f);
            Dimensions.Scenery(Dimensions.Soul, C, ctx.Cfg.Duration + 1f, seed);
            var who = Others(ctx, caster.transform.position, ctx.Cfg.Range);
            who.Add(caster);
            for (int i = 0; i < who.Count; i++)
            {
                float a = i * Mathf.PI * 2f / who.Count;
                Dimensions.Send(who[i], Dimensions.Soul, new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 2f,
                    ctx.Cfg.Duration, new Color(1f, 0.5f, 0.1f), "THE SOUL WORLD");
            }
        });
    }
}

// ══ TIME (green) ═════════════════════════════════════════════════════════════

public class TimeFreeze : ThanosAbility
{
    public override Stone Stone => Stone.Time;
    public override string Id => "time_freeze";
    public override string Name => "Time Freeze";
    public override string Label => "FREEZE";

    protected override void OnCast(CastContext ctx)
    {
        var c = ctx.CasterPos;
        var clock = Fx.MakeSprite(Sprites.Runes, c, new Color(C.r, C.g, C.b, 0.7f), ctx.Cfg.Range * 2f);
        Fx.Animate(clock, 1f, k => { clock.transform.Rotate(0, 0, -120f * Time.deltaTime); clock.color = new Color(C.r, C.g, C.b, 0.7f * (1f - k)); });
        Fx.Release(c, C, ImpactStyle.Time, 1.2f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
            Fx.Aftermath(pc.transform.position, C, Residue.Frost, 0.7f);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Frozen, ctx.Cfg.Duration * Boost(ctx));
            if (ctx.LocalIs(pc)) Fx.ScreenTint(new Color(0.5f, 1f, 0.6f, 0.3f), ctx.Cfg.Duration);
        }
    }
}

public class Rewind : ThanosAbility
{
    public override Stone Stone => Stone.Time;
    public override string Id => "rewind";
    public override string Name => "Rewind";
    public override string Label => "REWIND";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = ThanosCharacter.Instance.PositionAgo(Cfg.Duration, caster.transform.position);

    protected override void OnCast(CastContext ctx)
    {
        // afterimages stream backwards along the line while the clock runs in reverse
        for (int i = 1; i <= 5; i++)
        {
            var q = Vector2.Lerp(ctx.CasterPos, ctx.Point, i / 6f);
            Fx.Flare(q, new Color(C.r, C.g, C.b, 0.6f), 0.7f, 0.2f + i * 0.06f);
        }
        Fx.Release(ctx.Point, C, ImpactStyle.Time, 0.9f);
        Sfx.Play(SfxKind.Reverse, 1.2f, ctx.CasterPos, 0.5f);
        var clock = Fx.MakeSprite(Sprites.Runes, ctx.Point, C, 1.4f);
        Fx.Animate(clock, 0.8f, k => { clock.transform.Rotate(0, 0, 600f * Time.deltaTime); clock.color = new Color(C.r, C.g, C.b, 1f - k); });
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

/// <summary>An echo of Thanos walks the path he took over the last few seconds.</summary>
public class TemporalEcho : ThanosAbility
{
    public override Stone Stone => Stone.Time;
    public override string Id => "temporal_echo";
    public override string Name => "Temporal Echo";
    public override string Label => "ECHO";

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = ThanosCharacter.Instance.PositionAgo(4f, caster.transform.position);

    protected override void OnCast(CastContext ctx)
    {
        var path = ctx.LocalIs(ctx.Caster) ? ThanosCharacter.Instance.PathSince(4f) : new List<Vector2> { ctx.Point, ctx.CasterPos };
        if (path.Count < 2) path = new List<Vector2> { ctx.Point, ctx.CasterPos };
        Fx.Release(path[0], C, ImpactStyle.Time, 0.6f);
        var echo = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, path[0]);
        echo.SetTint(new Color(0.5f, 1f, 0.6f, 0.6f));
        echo.Untouchable = true;
        echo.PlayPath(path, 4f);
        // Thanos's echo marks every footstep of the path with a fading green clock tick
        for (int i = 0; i < path.Count; i += Mathf.Max(1, path.Count / 6)) Fx.ShockRing(path[i], new Color(C.r, C.g, C.b, 0.6f), 0.8f, 0.9f, squash: 0.5f);
        Sfx.Play(SfxKind.Tick, 1.3f, path[0], 0.45f);
        echo.Lifetime = ctx.Cfg.Duration;
    }
}

public class AgeManipulation : ThanosAbility
{
    public override Stone Stone => Stone.Time;
    public override string Id => "age_manipulation";
    public override string Name => "Age Manipulation";
    public override string Label => "AGE";

    protected override void OnCast(CastContext ctx)
    {
        var c = ctx.CasterPos;
        for (int i = 0; i < 3; i++)
        {
            var ring = Fx.MakeSprite(Sprites.Runes, c, new Color(C.r, C.g, C.b, 0.5f), 1f + i * 1.3f);
            float dir = i % 2 == 0 ? 1f : -1f;
            Fx.Animate(ring, ctx.Cfg.Duration, k => ring.transform.Rotate(0, 0, dir * 60f * Time.deltaTime));
        }
        Sfx.Play(SfxKind.Creak, 0.6f, c, 0.5f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
            // they age: sand pours off them and they hunch over (temporary, restored by CharacterVisuals)
            Fx.Rising(pc.transform.position, new Color(0.8f, 0.7f, 0.45f), 8, 0.6f, ctx.Cfg.Duration * 0.5f);
            CharacterVisuals.Set(pc, "aged", tilt: 8f, scale: 0.9f, maxSeconds: ctx.Cfg.Duration);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Slowed, ctx.Cfg.Duration);
            if (ctx.LocalIs(pc)) Fx.ScreenTint(new Color(0.6f, 0.45f, 0.2f, 0.35f), ctx.Cfg.Duration);
        }
    }
}

public class EternalLoop : ThanosAbility
{
    public override Stone Stone => Stone.Time;
    public override string Id => "eternal_loop";
    public override string Name => "Eternal Loop";
    public override string Label => "LOOP";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Ultimate(ctx, "ETERNAL LOOP", () =>
        {
            if (target == null) return;
            Fx.Sigil(target.transform.position, C, 2f, ctx.Cfg.Duration * 0.25f);
            Coroutines.Start(Run(ctx, target));
        });
    }

    private IEnumerator Run(CastContext ctx, PlayerControl target)
    {
        Vector2 anchor = target.transform.position;
        if (target == PlayerControl.LocalPlayer) Fx.Toast("YOU'RE STUCK IN A LOOP", C, 2f, 4f);
        for (float t = 0; t < ctx.Cfg.Duration; t += 1.5f)
        {
            for (float w = 0; w < 1.5f; w += Time.deltaTime)
            {
                if (MeetingHud.Instance != null || !Players.IsAlive(target)) yield break;
                yield return null;
            }
            var echo = IllusionManager.Instance!.SpawnIllusion(target, target.transform.position, quiet: true);
            echo.SetTint(new Color(0.5f, 1f, 0.6f, 0.45f));
            echo.Untouchable = true;
            echo.Lifetime = 1.2f;
            Fx.Release(anchor, C, ImpactStyle.Time, 0.7f);
            Players.Teleport(target, anchor);
        }
    }
}

// ══ MIND (yellow) ════════════════════════════════════════════════════════════

public class ThanosMindControl : ThanosAbility
{
    public override Stone Stone => Stone.Mind;
    public override string Id => "thanos_mind_control";
    public override string Name => "Mind Control";
    public override string Label => "CONTROL";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // a golden thread to the forehead, a hypnotic spiral over their head, a goofy "@_@"
        var t = ctx.Target;
        Fx.Tendril(ctx.CasterPos, t.transform.position, C, 0.6f, 12);
        Fx.Aura(t.transform, Sprites.Runes, new Color(C.r, C.g, C.b, 0.7f), 0.5f, ctx.Cfg.Duration, 360f, 0.75f);
        Fx.WorldText(t.transform, "@_@", C, 1.4f, 3f);
        Sfx.Play(SfxKind.Shimmer, 0.7f, t.transform.position, 0.6f);
        StatusManager.Instance?.ApplyStatus(t, StatusEffect.Puppet, ctx.Cfg.Duration, ctx.CasterPos);
    }
}

public class PsychicPulse : ThanosAbility
{
    public override Stone Stone => Stone.Mind;
    public override string Id => "psychic_pulse";
    public override string Name => "Psychic Pulse";
    public override string Label => "PULSE";

    protected override void OnCast(CastContext ctx)
    {
        var c = ctx.CasterPos;
        for (int i = 0; i < 3; i++) Fx.Wave(c, C, ctx.Cfg.Range, 0.4f + i * 0.2f);
        Fx.Release(c, C, ImpactStyle.Magic, 1.2f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
            Fx.WorldText(pc.transform, "?!", C, 1f, 3f);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, 0.8f);
            StatusManager.Instance?.ApplyStatus(pc, StatusEffect.ChaosContract, ctx.Cfg.Duration);
        }
    }
}

public class MemoryErasure : ThanosAbility
{
    public override Stone Stone => Stone.Mind;
    public override string Id => "memory_erasure";
    public override string Name => "Memory Erasure";
    public override string Label => "ERASE";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        if (ctx.Target == null) return;
        // their memory gets wiped like a whiteboard: glitch, sparkles, a blank "..."
        Fx.Sparkles(ctx.Target.transform.position, C, 12, 1f);
        Fx.Release(ctx.Target.transform.position, C, ImpactStyle.Glitch, 0.6f);
        Fx.WorldText(ctx.Target.transform, "...?", Color.white, 1.8f, 3f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Blinded, ctx.Cfg.Duration);
        if (ctx.LocalIs(ctx.Target)) Fx.ScreenTint(new Color(1f, 0.9f, 0.4f, 0.3f), ctx.Cfg.Duration);
    }
}

public class IllusionArmy : ThanosAbility
{
    public override Stone Stone => Stone.Mind;
    public override string Id => "illusion_army";
    public override string Name => "Illusion Army";
    public override string Label => "ARMY";

    protected override void OnCast(CastContext ctx)
    {
        for (int i = 0; i < Mathf.Clamp(ctx.Cfg.MaxTargets, 1, 8); i++)
        {
            var spot = MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.8f, 2.5f);
            Fx.Tendril(ctx.CasterPos, spot, C, 0.35f, 6);
            Fx.ShockRing(spot, C, 1.2f, 0.3f);
            var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, spot);
            ill.SetBehavior(IllusionBehavior.Wander);
            ill.Lifetime = ctx.Cfg.Duration;
        }
    }
}

public class AbsoluteDomination : ThanosAbility
{
    public override Stone Stone => Stone.Mind;
    public override string Id => "absolute_domination";
    public override string Name => "Absolute Domination";
    public override string Label => "DOMINATE";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        Ultimate(ctx, "ABSOLUTE DOMINATION", () =>
        {
            Fx.ScreenTint(new Color(1f, 0.85f, 0.2f, 0.25f), ctx.Cfg.Duration);
            Fx.Release(caster.transform.position, C, ImpactStyle.Magic, 2.4f, cinematic: true);
            Sfx.Play(SfxKind.Choir, 0.8f, caster.transform.position, 0.6f, cinematic: true);
            var looks = new List<PlayerControl>();
            foreach (var pc in Players.All()) if (Players.IsAlive(pc)) looks.Add(pc);
            foreach (var spot in MapSpawnPoints.GetRandom(ctx.Cfg.MaxTargets, ctx.Rng, caster.transform.position))
            {
                var ill = IllusionManager.Instance!.SpawnIllusion(looks.Count > 0 ? looks[ctx.Rng.Next(looks.Count)] : caster, spot);
                ill.SetBehavior(IllusionBehavior.Random);
                ill.Lifetime = ctx.Cfg.Duration;
            }
            // a gigantic spectral Thanos
            var giant = IllusionManager.Instance!.SpawnIllusion(caster, (Vector2)caster.transform.position + new Vector2(0f, 1.5f), quiet: true);
            giant.SetBehavior(IllusionBehavior.Idle);
            giant.Untouchable = true;
            giant.SetSize(3.2f);
            giant.SetTint(new Color(1f, 0.85f, 0.3f, 0.35f));
            giant.Lifetime = ctx.Cfg.Duration;
        });
    }
}

// ══ INFINITY (all six stones) ════════════════════════════════════════════════

public class InfinitySurge : ThanosAbility
{
    public override Stone Stone => Stone.Infinity;
    public override string Id => "infinity_surge";
    public override string Name => "Infinity Surge";
    public override string Label => "SURGE";

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Run(ctx));

    private IEnumerator Run(CastContext ctx)
    {
        var c = ctx.CasterPos;
        // each stone fires its own ring with its own rising note, then everything detonates together
        int i = 0;
        foreach (var s in Stones.Six)
        {
            Fx.Wave(c, Stones.Color(s), ctx.Cfg.Range, 0.7f);
            Sfx.Play(SfxKind.Chime, 0.8f + i++ * 0.1f, c, 0.5f);
            for (float t = 0; t < 0.08f; t += Time.deltaTime) yield return null;
        }
        Fx.Release(c, Color.white, ImpactStyle.Cosmic, 1.6f);
        Fx.Shake(0.5f, 0.7f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range)) Hit(ctx, pc, c, ctx.Cfg.Duration);
    }
}

/// <summary>
/// The Snap: gauntlet close-up, stones light one by one, snap, white flash, then the outcome chosen
/// in config (SnapMode): visual (nobody harmed), practice_kill (freeplay only), match_kill_half, match_stun.
/// </summary>
public class TheSnap : ThanosAbility
{
    public override Stone Stone => Stone.Infinity;
    public override string Id => "the_snap";
    public override string Name => "The Snap";
    public override string Label => "SNAP";
    public override bool IsUltimate => true;

    private static readonly string[] Modes = { "visual", "practice_kill", "match_kill_half", "match_stun" };

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
    {
        int mode = System.Array.IndexOf(Modes, ModConfig.Data.SnapMode);
        point = new Vector2(Mathf.Max(0, mode), 0f);   // the caster's setting decides for everyone
    }

    protected override void OnCast(CastContext ctx) => Kills.Finisher(Run(ctx, (int)ctx.Point.x));

    private IEnumerator Run(CastContext ctx, int mode)
    {
        // close-up of the gauntlet on everyone's screen
        var g = Fx.ScreenSprite(Sprites.Icon("gauntlet_empty", 256f) ?? Sprites.Glow, Color.white, -0.8f, "SnapGauntlet");
        var gems = new List<SpriteRenderer>();
        if (g != null)
        {
            g.transform.localScale = Vector3.one * 3f;
            Vector2[] sockets = { new(762.8f, 413f), new(694.4f, 234.4f), new(572.8f, 234.4f), new(451.2f, 234.4f), new(512f, 489f), new(329.6f, 234.4f) };
            for (int i = 0; i < 6; i++)
            {
                var go = new GameObject("SnapGem") { layer = g.gameObject.layer };
                go.transform.SetParent(g.transform, false);
                go.transform.localPosition = new Vector3(sockets[i].x / 1024f - 0.5f, 0.5f - sockets[i].y / 1024f, -0.01f);
                go.transform.localScale = Vector3.one * (i == 4 ? 0.2f : 0.13f);
                var sr = go.AddComponent<SpriteRenderer>();
                sr.sprite = Sprites.Glow;
                sr.color = new Color(0.2f, 0.2f, 0.2f, 0.6f);
                sr.sortingOrder = 1;
                gems.Add(sr);
            }
        }
        Fx.ScreenTint(new Color(0f, 0f, 0f, 0.6f), 3.2f);
        Sfx.Play(SfxKind.Choir, 0.7f, null, 0.5f, cinematic: true);
        yield return Kills.Wait(0.5f);
        for (int i = 0; i < gems.Count; i++)
        {
            var c = Stones.Color(Stones.Six[i]);
            if (gems[i] != null) { gems[i].color = c; gems[i].transform.localScale *= 1.6f; }
            Sfx.Play(SfxKind.Chime, 0.8f + i * 0.12f);
            yield return Kills.Wait(0.3f);
        }
        yield return Kills.Wait(0.3f);
        if (g != null) Fx.Punch(g.transform, 0.25f, 0.25f);
        Sfx.Play(SfxKind.Snap, 1f);
        yield return Kills.Wait(0.15f);
        if (g != null) Object.Destroy(g.gameObject);
        Fx.ScreenTint(new Color(1f, 1f, 1f, 0.95f), 0.9f);
        Fx.Shake(0.6f, 0.8f);
        yield return Kills.Wait(0.5f);

        // the outcome: half of everyone else (same pick on every client)
        var others = new List<PlayerControl>();
        foreach (var pc in Players.All())
            if (pc != ctx.Caster && Players.IsAlive(pc) && (mode != 2 || pc.Data?.Role == null || !pc.Data.Role.IsImpostor))
                others.Add(pc);
        others.Sort((a, b) => a.PlayerId.CompareTo(b.PlayerId));
        for (int i = others.Count - 1; i > 0; i--) { int j = ctx.Rng.Next(i + 1); (others[i], others[j]) = (others[j], others[i]); }
        var chosen = others.GetRange(0, (others.Count + 1) / 2);

        bool lethal = mode == 2 || (mode == 1 && CharacterRegistry.IsFreeplay);
        Sfx.Play(SfxKind.Reverse, 0.6f, null, 0.6f, cinematic: true);
        foreach (var pc in chosen)
        {
            if (mode == 3) { StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, 4f); continue; }
            Fx.Fragments(pc.transform.position, new Color(0.45f, 0.35f, 0.25f), 50, 1.4f, 1.6f);   // turning to dust
            Fx.Rising(pc.transform.position, new Color(0.5f, 0.4f, 0.3f), 10, 0.8f, 2f);
            if (lethal) Kills.Kill(ctx.Caster, pc, leaveBody: false);
        }
        if (ctx.LocalIs(ctx.Caster))
            Fx.Toast(lethal ? "PERFECTLY BALANCED." : "PERFECTLY BALANCED. (visual only - SnapMode)", Stones.Color(Stone.Infinity), 3f, 4.5f);
    }
}

public class TitansJudgment : ThanosAbility
{
    /// <summary>Every stone sends its energy into the gauntlet, one after another.</summary>
    private static IEnumerator SixStones(PlayerControl caster)
    {
        foreach (var s in Stones.Six)
        {
            if (caster == null) yield break;
            Coroutines.Start(Fx.StoneToGauntlet(caster.transform, Stones.Color(s), 0.45f));
            yield return new WaitForSeconds(0.18f);
        }
    }

    public override Stone Stone => Stone.Infinity;
    public override string Id => "titans_judgment";
    public override string Name => "Titan's Judgment";
    public override string Label => "JUDGMENT";
    public override bool IsUltimate => true;
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = (Vector2)caster.transform.position + AimDir(caster, target) * Cfg.Range;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        CharacterVisuals.Set(caster, "judgment", lift: 0.5f, maxSeconds: ctx.Cfg.CinematicDur + 3f);
        Coroutines.Start(SixStones(caster));
        CinematicPlayer.Play("TITAN'S JUDGMENT", C, caster, ctx.Cfg.CinematicDur + 0.4f, () =>
        {
            CharacterVisuals.Clear(caster, "judgment");
            Vector2 from = caster.transform.position;
            var dir = (ctx.Point - ctx.CasterPos).normalized;
            var to = from + dir * ctx.Cfg.Range;
            var side = new Vector2(-dir.y, dir.x);
            // six braided beams with lightning crawling along them, detonating at the far end
            for (int i = 0; i < 6; i++)
                Fx.Beam(from + side * (i - 2.5f) * 0.12f, to + side * (i - 2.5f) * 0.12f, Stones.Color(Stones.Six[i]), 0.35f, 1.2f);
            for (int i = 0; i < 3; i++) Fx.Lightning(from, to, Stones.Color(Stones.Six[i * 2]), 0.6f, 2, 0.4f);
            Fx.Release(to, Color.white, ImpactStyle.Cosmic, 2f, cinematic: true);
            Fx.Release(from, C, ImpactStyle.Heavy, 1.2f);
            Fx.Shake(0.9f, 1f);
            foreach (var pc in OnLine(ctx, from, to, 0.8f))
            {
                if (ctx.Cfg.Damage > 0f) Kills.Kill(caster, pc);
                else Hit(ctx, pc, from, 3f);
            }
        });
    }
}

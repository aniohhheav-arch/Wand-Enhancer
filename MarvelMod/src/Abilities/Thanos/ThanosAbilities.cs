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
        Fx.Projectile(from, ctx.Point, C, 14f, () =>
        {
            Fx.Cracks(ctx.Point, C, 6, 0.8f, 0.6f, ctx.Rng.Next());
            Sfx.Play(SfxKind.Boom, 1.3f, ctx.Point, 0.6f);
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
        var charge = Fx.Aura(caster.transform, Sprites.Glow, new Color(C.r, C.g, C.b, 0.8f), 0.4f, 0.8f, 0f, 0f, false);
        Sfx.Play(SfxKind.Rise, 0.8f, ctx.CasterPos, 0.6f);
        for (float t = 0; t < 0.6f; t += Time.deltaTime)
        {
            if (charge != null) charge.transform.localScale = Vector3.one * (0.4f + t * 2.6f);
            yield return null;
        }
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
        if (caster != null) { Fx.Burst(caster.transform.position, C, 1.2f, 0.3f); Sfx.Play(SfxKind.Thud, 0.8f, caster.transform.position, 0.5f); }
        ToggleEnded(caster);
    }
}

public class Shockwave : ThanosAbility
{
    public override Stone Stone => Stone.Power;
    public override string Id => "shockwave";
    public override string Name => "Shockwave";
    public override string Label => "SLAM";

    protected override void OnCast(CastContext ctx)
    {
        var c = ctx.CasterPos;
        Fx.Wave(c, C, ctx.Cfg.Range, 0.45f);
        Fx.Cracks(c, C, 10, ctx.Cfg.Range * 0.6f, 0.9f, ctx.Rng.Next());
        Fx.Shake(0.4f, 0.6f);
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
        Fx.Burst(ctx.CasterPos, C, 2f, 0.4f);
        StatusManager.Instance?.ApplyStatus(ctx.Caster, StatusEffect.Haste, ctx.Cfg.Duration);
        if (ctx.LocalIs(ctx.Caster)) Fx.Toast("POWER OVERLOAD: FASTER, STRONGER", C, 2f, 3.5f);
    }
}

public class Planetbreaker : ThanosAbility
{
    public override Stone Stone => Stone.Power;
    public override string Id => "planetbreaker";
    public override string Name => "Planetbreaker";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        var caster = ctx.Caster;
        // float into the air (restored by CharacterVisuals even if the wind-up is interrupted)
        CharacterVisuals.Set(caster, "planetbreaker", lift: 0.4f, maxSeconds: ctx.Cfg.CinematicDur + 1.5f);
        CinematicPlayer.Play("PLANETBREAKER", C, caster, ctx.Cfg.CinematicDur, () =>
        {
            CharacterVisuals.Clear(caster, "planetbreaker");
            Vector2 c = caster.transform.position;
            Fx.Burst(c, C, ctx.Cfg.Range * 2f, 0.8f);
            Fx.Wave(c, C, ctx.Cfg.Range, 0.6f);
            Fx.Cracks(c, C, 16, ctx.Cfg.Range, 1.5f, ctx.Rng.Next());
            Fx.Shake(0.8f, 1.2f);
            Sfx.Play(SfxKind.Boom, 0.6f, c);
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
        Fx.Burst(ctx.CasterPos, C, 1.4f, 0.3f);
        Fx.Burst(ctx.Point, C, 1.4f, 0.3f);
        Fx.Beam(ctx.CasterPos, ctx.Point, C, 0.15f, 0.3f);
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

    protected override void OnCast(CastContext ctx) => Coroutines.Start(Portals.Run(ctx.Point, ctx.Point2, C, ctx.Cfg.Duration));
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
        Fx.Beam(ctx.CasterPos, ctx.Target.transform.position, C, 0.2f, 0.4f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Prison, ctx.Cfg.Duration * Boost(ctx));
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
        CinematicPlayer.Play("COSMIC EXILE", C, ctx.Caster, ctx.Cfg.CinematicDur, () =>
        {
            Dimensions.Scenery(Dimensions.Cosmic, C, ctx.Cfg.Duration + 1f, seed);
            Dimensions.Send(target, Dimensions.Cosmic, Vector2.zero, ctx.Cfg.Duration, new Color(0.15f, 0.25f, 0.9f), "EXILED TO THE COSMOS");
        });
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
        for (int i = 0; i < 3; i++) Fx.Wave(c, new Color(C.r, C.g, C.b, 0.7f - i * 0.2f), ctx.Cfg.Range, 0.6f + i * 0.25f);
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
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
        Fx.Smoke(ctx.Target.transform.position, C, 10, 0.7f);
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
        Fx.Cracks(p, C, 9, ctx.Cfg.Radius, 1.3f, ctx.Rng.Next());
        for (float t = 0; t < 1f; t += Time.deltaTime) yield return null;
        Fx.Burst(p, C, ctx.Cfg.Radius * 2f, 0.4f);
        Fx.Fragments(p, C, 40, ctx.Cfg.Radius, 0.8f);
        Sfx.Play(SfxKind.Slash, 2f, p);
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
        CinematicPlayer.Play("REALITY COLLAPSE", C, caster, ctx.Cfg.CinematicDur, () =>
        {
            Vector2 c = caster.transform.position;
            Fx.Cracks(c, C, 20, 6f, 2f, seed);
            Sfx.Play(SfxKind.Slash, 1.6f, c);
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
        if (!ctx.LocalIs(ctx.Caster)) return;
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
        for (int i = 0; i < 4; i++)
            Fx.Projectile(Players.Pos(target) + Random.insideUnitCircle * 0.3f, ctx.CasterPos, C, 6f + i, null);
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
        Fx.Beam(ctx.CasterPos, ctx.Target.transform.position, C, 0.18f, 0.5f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Chained, ctx.Cfg.Duration * Boost(ctx));
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
        CinematicPlayer.Play("SOUL WORLD", C, caster, ctx.Cfg.CinematicDur, () =>
        {
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
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
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
        Fx.Beam(ctx.CasterPos, ctx.Point, C, 0.2f, 0.5f);
        Fx.Burst(ctx.CasterPos, C, 1.2f, 0.3f);
        Fx.Burst(ctx.Point, C, 1.6f, 0.4f);
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
        var echo = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, path[0]);
        echo.SetTint(new Color(0.5f, 1f, 0.6f, 0.6f));
        echo.Untouchable = true;
        echo.PlayPath(path, 4f);
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
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
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
        CinematicPlayer.Play("ETERNAL LOOP", C, ctx.Caster, ctx.Cfg.CinematicDur, () => Coroutines.Start(Run(ctx, target)));
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
            Fx.Burst(anchor, C, 1.2f, 0.3f);
            Sfx.Play(SfxKind.Chime, 1.2f, anchor, 0.5f);
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
        Fx.Beam(ctx.CasterPos, ctx.Target.transform.position, C, 0.15f, 0.6f);
        StatusManager.Instance?.ApplyStatus(ctx.Target, StatusEffect.Puppet, ctx.Cfg.Duration, ctx.CasterPos);
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
        foreach (var pc in Others(ctx, c, ctx.Cfg.Range))
        {
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
        Fx.Sparkles(ctx.Target.transform.position, C, 12, 1f);
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
            var ill = IllusionManager.Instance!.SpawnIllusion(ctx.Caster, MapSpawnPoints.NearbyOpenPoint(ctx.CasterPos, ctx.Rng, 0.8f, 2.5f));
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
        CinematicPlayer.Play("ABSOLUTE DOMINATION", C, caster, ctx.Cfg.CinematicDur, () =>
        {
            Fx.ScreenTint(new Color(1f, 0.85f, 0.2f, 0.25f), ctx.Cfg.Duration);
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
        foreach (var s in Stones.Six)
        {
            Fx.Wave(c, Stones.Color(s), ctx.Cfg.Range, 0.7f);
            for (float t = 0; t < 0.08f; t += Time.deltaTime) yield return null;
        }
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
        foreach (var pc in chosen)
        {
            if (mode == 3) { StatusManager.Instance?.ApplyStatus(pc, StatusEffect.Stunned, 4f); continue; }
            Fx.Fragments(pc.transform.position, new Color(0.45f, 0.35f, 0.25f), 50, 1.4f, 1.6f);   // turning to dust
            if (lethal) Kills.Kill(ctx.Caster, pc, leaveBody: false);
        }
        if (ctx.LocalIs(ctx.Caster))
            Fx.Toast(lethal ? "PERFECTLY BALANCED." : "PERFECTLY BALANCED. (visual only - SnapMode)", Stones.Color(Stone.Infinity), 3f, 4.5f);
    }
}

public class TitansJudgment : ThanosAbility
{
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
        CharacterVisuals.Set(caster, "judgment", lift: 0.5f, maxSeconds: ctx.Cfg.CinematicDur + 1.5f);
        CinematicPlayer.Play("TITAN'S JUDGMENT", C, caster, ctx.Cfg.CinematicDur, () =>
        {
            CharacterVisuals.Clear(caster, "judgment");
            Vector2 from = caster.transform.position;
            var dir = (ctx.Point - ctx.CasterPos).normalized;
            var to = from + dir * ctx.Cfg.Range;
            var side = new Vector2(-dir.y, dir.x);
            for (int i = 0; i < 6; i++)
                Fx.Beam(from + side * (i - 2.5f) * 0.12f, to + side * (i - 2.5f) * 0.12f, Stones.Color(Stones.Six[i]), 0.35f, 1.2f);
            Fx.Shake(0.9f, 1f);
            Sfx.Play(SfxKind.Boom, 0.5f, from);
            foreach (var pc in OnLine(ctx, from, to, 0.8f))
            {
                if (ctx.Cfg.Damage > 0f) Kills.Kill(caster, pc);
                else Hit(ctx, pc, from, 3f);
            }
        });
    }
}

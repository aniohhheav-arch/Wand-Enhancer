using System.Collections;
using System.Collections.Generic;
using MarvelMod.Abilities.Loki;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Strange;

// ── Doctor Strange cinematic finishers (same director as Loki's: camera, letterbox, HUD hidden) ──

public abstract class StrangeFinisher : FinisherAbility
{
    public override Color CastColor => StrangeAbility.Gold;
    protected static Color Gold => StrangeAbility.Gold;

    protected static SpriteRenderer Circle(Vector2 at, Color c, float size, float spin, float life)
    {
        var sr = Fx.MakeSprite(Sprites.Runes, at, c, 0.1f, "StrangeCircle");
        Fx.Animate(sr, life, k =>
        {
            sr.transform.localScale = new Vector3(size, size * 0.55f, 1f) * Mathf.Min(1f, k * life / 0.4f);
            sr.transform.Rotate(0, 0, spin * Time.deltaTime);
            if (k > 0.85f) sr.color = new Color(c.r, c.g, c.b, c.a * (1f - k) / 0.15f);
        });
        return sr;
    }
}

/// <summary>1. Falls through a portal into a corridor of portals; Strange waits at the last one.</summary>
public class PortalMaze : StrangeFinisher
{
    public override string Id => "portal_maze";
    public override string Name => "The Portal Maze";
    public override string Label => "PORTAL MAZE";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var strange = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        d.Zoom(0.6f, 0.8f);
        Fx.WorldText(strange.transform, "*raises a hand*", Gold, 1f * d.TimeScale, 2.4f);
        yield return d.Wait(0.8f);

        // a portal opens beneath the victim; they fall in
        var hole = Circle(vp + new Vector2(0f, -0.3f), Gold, 1.8f, 120f, 2f * d.TimeScale);
        d.React(victim, "!?");
        Sfx.Play(SfxKind.Whoosh, 0.8f, vp);
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(victim, -k * 0.5f, k * 40f); d.Fade(victim, 1f - k); yield return d.Wait(0.06f); }

        // the camera follows them through portals across the map
        var stops = MapSpawnPoints.GetRandom(3, ctx.Rng, vp);
        foreach (var s in stops)
        {
            Vector2 sp = s;
            Circle(sp, Gold, 1.6f, -150f, 1.2f * d.TimeScale);
            var ghost = Clone(victim, sp, 1f * d.TimeScale);
            ghost.SetTint(new Color(1f, 0.85f, 0.6f, 0.7f));
            ghost.MoveTo(sp + Random.insideUnitCircle.normalized * 1.2f, 3f);
            d.Cut(sp);
            Sfx.Play(SfxKind.Whoosh, 1.2f, null, 0.5f);
            yield return d.Wait(0.9f);
        }

        // Strange waits in the final portal behind a glowing shield
        d.Cut(Pos(strange));
        Fx.Aura(strange.transform, Sprites.Runes, new Color(1f, 0.7f, 0.2f, 0.9f), 1.4f, 2f * d.TimeScale, 80f);
        Circle(Pos(strange) + new Vector2(0.8f, 0f), Gold, 1.6f, 100f, 2f * d.TimeScale);
        yield return d.Wait(1f);

        // he closes his hand: the corridor folds into a single ring that vanishes
        Fx.WorldText(strange.transform, "*closes his hand*", Gold, 1f * d.TimeScale, 2.4f);
        var ring = Fx.MakeSprite(Sprites.Ring, Pos(strange) + new Vector2(0.8f, 0f), Gold, 3f);
        Fx.Animate(ring, 0.8f * d.TimeScale, k => ring.transform.localScale = Vector3.one * 3f * (1f - k));
        d.Finish();
        Sfx.Play(SfxKind.Bell, 1.1f, null, 0.7f);
        yield return d.Wait(1.2f);
    }
}

/// <summary>2. The world folds into mirrors; Strange appears on every surface; the victim shatters.</summary>
public class MirrorDimensionFinisher : StrangeFinisher
{
    public override string Id => "mirror_dimension_finisher";
    public override string Name => "The Mirror Dimension";
    public override string Label => "MIRROR";
    protected override float NominalSeconds => 11f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var strange = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var blue = StrangeAbility.MirrorBlue;
        if (d.Participant) Fx.ScreenTint(new Color(0.5f, 0.7f, 1f, 0.3f), 10f * d.TimeScale);
        d.Zoom(0.7f, 1f);
        Fx.Wave(vp, blue, 6f, 1f * d.TimeScale);   // the floor turns reflective
        yield return d.Wait(1f);

        DimensionalPrison.MirrorScenery(vp, 9f * d.TimeScale, ctx.Rng.Next());
        Fx.Cracks(vp, blue, 12, 3f, 2f * d.TimeScale, ctx.Rng.Next());
        Sfx.Play(SfxKind.Slash, 1.8f, vp);
        yield return d.Wait(1.5f);

        // Strange on every surface, some "upside down"
        var clones = new List<LokiIllusion>();
        for (int i = 0; i < 6; i++)
        {
            float a = i * Mathf.PI / 3f;
            var c = Clone(strange, vp + new Vector2(Mathf.Cos(a) * 2.3f, Mathf.Sin(a) * 1.6f), 8f * d.TimeScale);
            c.SetTint(new Color(0.85f, 0.95f, 1f, 0.75f));
            c.Face(Mathf.Cos(a) > 0f);
            if (i % 2 == 0) c.transform.rotation = Quaternion.Euler(0, 0, 180f);
            clones.Add(c);
        }
        StatusManager.SetHidden(strange, true);
        d.Orbit(vp, 1f, 300f, 3f);
        d.React(victim, "?!");
        yield return d.Wait(3f);

        // snap: the structures fold inward
        Sfx.Play(SfxKind.Snap, 1f, vp);
        foreach (var c in clones) if (c != null) c.MoveTo(vp, 6f);
        d.Zoom(0.45f, 0.6f);
        yield return d.Wait(0.6f);
        foreach (var c in clones) if (c != null) c.Pop();
        Fx.Fragments(vp, blue, 80, 2f, 1.4f * d.TimeScale);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(1.2f);
        StatusManager.SetHidden(strange, false);
        Fx.Burst(Pos(strange), blue, 1.6f, 0.4f);   // the environment restores itself
        yield return d.Wait(1f);
    }
}

/// <summary>3. A crimson circle, bands, rising rings, and a seal that bursts into sparks.</summary>
public class CrimsonBandsFinisher : StrangeFinisher
{
    public override string Id => "crimson_bands_finisher";
    public override string Name => "Crimson Bands";
    public override string Label => "BANDS";
    protected override float NominalSeconds => 8f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var victim = d.Victim; Vector2 vp = Pos(victim);
        var red = StrangeAbility.Crimson;
        d.Zoom(0.6f, 0.8f);
        Circle(vp + new Vector2(0f, -0.3f), new Color(1f, 0.35f, 0.15f, 1f), 3f, 60f, 7f * d.TimeScale);
        Sfx.Play(SfxKind.Hum, 0.9f, vp);
        yield return d.Wait(0.8f);

        for (int i = 0; i < 3; i++)   // bands wrap the victim
        {
            Fx.Aura(victim.transform, Sprites.Ring, new Color(red.r, red.g, red.b, 0.95f), 1.2f - i * 0.15f, 6f * d.TimeScale, (i % 2 == 0 ? 1 : -1) * 200f, -0.1f + i * 0.25f);
            Sfx.Play(SfxKind.Creak, 0.8f + i * 0.2f, vp, 0.5f);
            yield return d.Wait(0.3f);
        }
        d.React(victim, "!");
        d.Orbit(vp, 0.8f, 180f, 2f);
        for (int i = 0; i < 6; i++)   // runes light one by one
        {
            float a = i * Mathf.PI / 3f;
            var rune = Fx.MakeSprite(Sprites.Star, vp + new Vector2(Mathf.Cos(a) * 1.5f, Mathf.Sin(a) * 0.8f - 0.3f), Gold, 0.35f);
            Object.Destroy(rune.gameObject, 4f * d.TimeScale);
            Sfx.Play(SfxKind.Chime, 1f + i * 0.1f, vp, 0.4f);
            yield return d.Wait(0.25f);
        }
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(victim, k * 0.45f, 0f); yield return d.Wait(0.05f); }
        for (int i = 0; i < 3; i++)
            Fx.Aura(victim.transform, Sprites.Runes, new Color(1f, 0.7f, 0.25f, 0.8f), 1.6f + i * 0.4f, 2f * d.TimeScale, (i + 1) * 70f, 0.45f);
        yield return d.Wait(1.2f);

        // the fist closes: bands compress into a glowing seal, then gold sparks
        var seal = Fx.MakeSprite(Sprites.Box, vp + new Vector2(0f, 0.45f), new Color(1f, 0.6f, 0.2f, 1f), 1.6f);
        Fx.Animate(seal, 0.8f * d.TimeScale, k => { seal.transform.localScale = Vector3.one * 1.6f * (1f - k * 0.85f); seal.transform.Rotate(0, 0, 400f * Time.deltaTime); });
        d.Fade(victim, 0f);
        yield return d.Wait(0.8f);
        Fx.Sparkles(vp + new Vector2(0f, 0.45f), Gold, 40, 1.8f, 1f);
        Sfx.Play(SfxKind.Bell, 1f, vp);
        d.Finish();
        yield return d.Wait(1f);
    }
}

/// <summary>4. Time stops; three echoes of the victim converge into light under the Eye.</summary>
public class TemporalFracture : StrangeFinisher
{
    public override string Id => "temporal_fracture";
    public override string Name => "Temporal Fracture";
    public override string Label => "FRACTURE";
    protected override float NominalSeconds => 10f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var strange = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var green = StrangeAbility.TimeGreen;
        Fx.Aura(strange.transform, Sprites.Glow, new Color(green.r, green.g, green.b, 0.9f), 0.35f, 9f * d.TimeScale, 0f, 0.15f);   // the Eye opens
        Sfx.Play(SfxKind.Chime, 0.7f, vp);
        if (d.Participant) Fx.ScreenTint(new Color(0.3f, 1f, 0.5f, 0.25f), 9f * d.TimeScale);
        Fx.Wave(Pos(strange), green, 6f, 1.2f * d.TimeScale);
        StatusManager.Instance?.ApplyStatus(victim, StatusEffect.Frozen, 9f * d.TimeScale);
        yield return d.Wait(1.2f);

        var echoes = new List<LokiIllusion>();
        for (int i = 0; i < 3; i++)
        {
            float a = i * Mathf.PI * 2f / 3f + 0.5f;
            var e = Clone(victim, vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.4f, 7f * d.TimeScale);
            e.SetTint(new Color(0.55f, 1f, 0.65f, 0.55f));
            e.MoveTo(vp + new Vector2(Mathf.Cos(a + 1f), Mathf.Sin(a + 1f)) * 1.6f, 0.4f);   // replaying movements
            echoes.Add(e);
        }
        yield return d.Wait(1f);

        // Strange walks between the frozen echoes while the camera circles him
        StatusManager.Instance?.RemoveStatus(strange, StatusEffect.Rooted);
        StatusManager.Instance?.ApplyStatus(strange, StatusEffect.Puppet, 2f * d.TimeScale, vp + new Vector2(-0.8f, 0.4f));
        d.Orbit(vp, 1.2f, 240f, 2.4f);
        yield return d.Wait(2.4f);
        StatusManager.Instance?.ApplyStatus(strange, StatusEffect.Rooted, 10f);

        // echoes converge into one luminous figure; rings close, reverse, collapse
        foreach (var e in echoes) if (e != null) e.MoveTo(vp, 4f);
        yield return d.Wait(0.6f);
        foreach (var e in echoes) if (e != null) e.Vanish();
        var glow = Fx.MakeSprite(Sprites.Glow, vp, new Color(green.r, green.g, green.b, 0.9f), 1.4f);
        for (int i = 0; i < 3; i++)
        {
            var ring = Fx.MakeSprite(Sprites.Runes, vp, green, 3f - i * 0.7f);
            float dir = i % 2 == 0 ? 1f : -1f;
            Fx.Animate(ring, 1.4f * d.TimeScale, k =>
            {
                ring.transform.Rotate(0, 0, dir * (k < 0.5f ? 300f : -500f) * Time.deltaTime);
                ring.transform.localScale = Vector3.one * (3f - i * 0.7f) * (1f - k);
            });
        }
        d.Fade(victim, 0f);
        yield return d.Wait(1.4f);
        if (glow != null) Object.Destroy(glow.gameObject);
        Fx.Burst(vp, Color.white, 0.6f, 0.2f);
        Sfx.Play(SfxKind.Bell, 1.3f, vp);
        d.Finish();
        yield return d.Wait(1f);   // time resumes; the Eye closes
    }
}

/// <summary>5. A cosmic chamber, a hundred portals, every construct merges into a golden sigil.</summary>
public class SorcererSupreme : StrangeFinisher
{
    public override string Id => "sorcerer_supreme";
    public override string Name => "The Sorcerer Supreme";
    public override string Label => "SUPREME";
    public override bool IsUltimate => true;
    protected override float NominalSeconds => 14f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var strange = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Vector2 sp = Pos(strange);
        if (d.Participant) Fx.ScreenTint(new Color(0.02f, 0.02f, 0.1f, 0.75f), 13f * d.TimeScale);
        Sfx.Play(SfxKind.Choir, 0.9f, vp);
        for (int i = 0; i < 4; i++) Circle(vp, new Color(1f, 0.75f, 0.3f, 0.35f), 3f + i * 2f, (i % 2 == 0 ? 1 : -1) * 12f, 12f * d.TimeScale);
        d.Zoom(1.2f, 1.2f);
        yield return d.Wait(1.2f);

        // Strange levitates, cloak spread
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(strange, k * 0.5f, 0f); yield return d.Wait(0.05f); }
        Fx.Aura(strange.transform, Sprites.Glow, new Color(0.9f, 0.15f, 0.15f, 0.5f), 1.8f, 11f * d.TimeScale, 0f, 0.3f);   // red cloak glow
        // hundreds of portals open behind him
        for (int i = 0; i < 14; i++)
        {
            float a = Mathf.PI * (0.15f + 0.7f * i / 13f);
            Circle(sp + new Vector2(Mathf.Cos(a) * (2f + i % 3), Mathf.Sin(a) * (1.5f + i % 3) + 0.5f), Gold, 0.7f, 200f, 10f * d.TimeScale);
            if (i % 3 == 0) Sfx.Play(SfxKind.Whoosh, 1.2f + i * 0.05f, null, 0.3f);
            yield return d.Wait(0.08f);
        }

        // the target is lifted inside a rotating rune
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(victim, k * 0.5f, 0f); yield return d.Wait(0.05f); }
        Fx.Aura(victim.transform, Sprites.Runes, new Color(1f, 0.75f, 0.3f, 0.9f), 1.6f, 7f * d.TimeScale, 120f, 0.5f);
        d.React(victim, "...");
        d.Pan((sp + vp) * 0.5f, 1f);
        yield return d.Wait(1f);

        // constructs form around Strange: shield, bands, blades, temporal rings
        Fx.Aura(strange.transform, Sprites.Runes, new Color(1f, 0.7f, 0.2f, 0.9f), 1.3f, 5f * d.TimeScale, 60f, 0.5f);
        Fx.Aura(strange.transform, Sprites.Ring, new Color(0.9f, 0.15f, 0.15f, 0.9f), 1.7f, 5f * d.TimeScale, -150f, 0.5f);
        Fx.Aura(strange.transform, Sprites.Ring, new Color(0.3f, 1f, 0.5f, 0.9f), 2.1f, 5f * d.TimeScale, 220f, 0.5f);
        for (int i = 0; i < 6; i++)
        {
            var blade = Fx.MakeSprite(Sprites.Bar, sp, Gold, 1f);
            float a0 = i * Mathf.PI / 3f;
            Fx.Animate(blade, 3f * d.TimeScale, k =>
            {
                float a = a0 + k * 12f;
                var p = (Vector2)strange.transform.position + new Vector2(Mathf.Cos(a), Mathf.Sin(a) + 0.5f) * 1.1f;
                blade.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.7f);
                blade.transform.rotation = Quaternion.Euler(0, 0, a * Mathf.Rad2Deg);
                blade.transform.localScale = new Vector3(0.4f, 0.1f, 1f);
            });
        }
        Sfx.Play(SfxKind.Swell, 1f, vp);
        yield return d.Wait(2.5f);

        // they combine into a giant golden sigil that sweeps across the chamber
        var sigil = Fx.MakeSprite(Sprites.Runes, sp + new Vector2(0f, 0.5f), new Color(1f, 0.85f, 0.4f, 1f), 0.5f);
        Fx.Animate(sigil, 2f * d.TimeScale, k =>
        {
            sigil.transform.localScale = Vector3.one * Mathf.Lerp(0.5f, 9f, k * k);
            sigil.transform.Rotate(0, 0, 90f * Time.deltaTime);
            sigil.color = new Color(1f, 0.85f, 0.4f, 1f - k);
        });
        Fx.WorldText(strange.transform, "*sweeping gesture*", Gold, 1f * d.TimeScale, 2.4f);
        Fx.Shake(0.8f, 0.6f);
        yield return d.Wait(1f);

        // the target dissolves into a stream of golden light into a portal
        var portal = sp + new Vector2(2.5f, 1.5f);
        Circle(portal, Gold, 1.8f, 160f, 2.5f * d.TimeScale);
        d.Fade(victim, 0f);
        Fx.Fragments(Pos(victim) + new Vector2(0f, 0.5f), Gold, 60, 1.2f, 0.8f * d.TimeScale);
        for (int i = 0; i < 10; i++)
        {
            Fx.Projectile(Pos(victim) + new Vector2(0f, 0.5f) + Random.insideUnitCircle * 0.3f, portal, Gold, 10f + i, null);
            yield return d.Wait(0.06f);
        }
        d.Finish();
        yield return d.Wait(1.2f);

        // the portal closes, the chamber vanishes, Strange lands
        Fx.Burst(portal, Gold, 1.5f, 0.4f);
        Sfx.Play(SfxKind.Bell, 0.9f, vp);
        for (float k = 1f; k >= 0f; k -= 0.1f) { CinematicDirector.Pose(strange, k * 0.5f, 0f); yield return d.Wait(0.05f); }
        yield return d.Wait(0.6f);
    }
}

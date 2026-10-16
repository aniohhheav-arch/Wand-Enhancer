using System.Collections;
using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Abilities.Loki;

// ── F: Cinematic finishers (real kills, no gore) ─────────────────────────────
// Killer + victim get the camera choreography; everyone else sees the in-world animation.
// Duration (0 = default) scales the sequence; FinisherMode: full / short / skip.

public abstract class FinisherAbility : AbilityBase
{
    public override string Category => "Executions";
    public override TargetMode Targeting => TargetMode.Required;
    protected abstract float NominalSeconds { get; }
    protected abstract IEnumerator Sequence(CinematicDirector d, CastContext ctx);

    protected override void OnCast(CastContext ctx) => Run(ctx, preview: false);

    /// <summary>Practice: play on a target without killing (local only).</summary>
    public void Preview(PlayerControl caster, PlayerControl target) =>
        Run(new CastContext(caster, target, caster.transform.position, Random.Range(0, int.MaxValue), Cfg), preview: true);

    private void Run(CastContext ctx, bool preview)
    {
        var target = ctx.Target;
        if (target == null) return;
        if (!preview)
        {
            if (!Kills.CanKill(ctx.Caster, target)) return;
            if (StatusManager.Instance != null && StatusManager.Instance.ConsumeShield(target)) return;
        }
        var mode = ModConfig.Data.FinisherMode;
        if (mode == "skip" && !preview)
        {
            Fx.Fragments(target.transform.position, Fx.LokiGreen, 30);
            Sfx.Play(SfxKind.Snap, 1f, target.transform.position);
            Kills.Kill(ctx.Caster, target, leaveBody: false, checkShield: false);
            return;
        }
        float seconds = ctx.Cfg.Duration > 0.5f ? ctx.Cfg.Duration : NominalSeconds;
        if (mode == "short") seconds *= 0.5f;
        var director = new CinematicDirector(ctx.Caster, target, NominalSeconds, seconds, preview);
        CinematicDirector.Play(director, d => Sequence(d, ctx));
    }

    protected static LokiIllusion Clone(PlayerControl template, Vector2 pos, float life = 20f)
    {
        var ill = IllusionManager.Instance!.SpawnIllusion(template, pos);
        ill.SetBehavior(IllusionBehavior.Idle);
        ill.Untouchable = true;
        ill.Lifetime = life;
        return ill;
    }

    protected static SpriteRenderer Prop(string iconId, Vector2 pos, float size)
    {
        var sr = Fx.MakeSprite(Sprites.Icon(iconId, 256f) ?? Sprites.Glow, pos, Color.white, size, $"Prop_{iconId}");
        sr.transform.position = new Vector3(pos.x, pos.y, pos.y / 1000f - 0.4f);
        return sr;
    }

    protected static void Slash(Vector2 p, float angle)
    {
        var s = Fx.MakeSprite(Sprites.Bar, p, new Color(0.8f, 1f, 0.85f, 1f), 1f);
        Fx.Animate(s, 0.2f, k =>
        {
            s.transform.localScale = new Vector3(1.2f * (0.4f + k), 0.15f, 1f);
            s.transform.rotation = Quaternion.Euler(0, 0, angle - 40f + 80f * k);
            s.color = new Color(0.8f, 1f, 0.85f, 1f - k);
        });
    }

    protected static Vector2 Pos(PlayerControl pc) => pc.transform.position;
}

/// <summary>1. Six Lokis close in; only one is real. The victim dissolves into illusion fragments.</summary>
public class InfiniteDeception : FinisherAbility
{
    public override string Id => "infinite_deception";
    public override string Name => "The Infinite Deception";
    public override string Label => "DECEPTION";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var loki = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var dirIn = (ctx.CasterPos - vp).sqrMagnitude > 0.01f ? ctx.CasterPos - vp : Vector2.right;
        var front = Players.SafeDestination(vp, dirIn, 1.2f);
        Players.Teleport(loki, front);
        d.Zoom(0.6f, 1f);
        d.React(victim, "?");
        Sfx.Play(SfxKind.Hum, 0.8f, vp);
        yield return d.Wait(1.2f);

        if (d.Participant) Fx.ScreenTint(new Color(0.1f, 0.8f, 0.3f, 0.35f), 6f * d.TimeScale);
        Fx.Burst(Pos(loki), Fx.LokiGreen, 2.5f, 0.5f);
        Sfx.Play(SfxKind.Chime, 0.9f, vp);
        var clones = new List<LokiIllusion>();
        for (int i = 0; i < 6; i++)
        {
            float a = i * Mathf.PI / 3f;
            var spot = vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 2.2f;
            var c = Clone(loki, Pos(loki));
            c.MoveTo(spot, 6f);
            if (i % 2 == 1) c.Kneel(true);
            c.Face(spot.x > vp.x);
            clones.Add(c);
        }
        StatusManager.SetHidden(loki, true);
        yield return d.Wait(1f);

        d.Orbit(vp, 0.9f, 360f, 2.8f);
        foreach (var c in clones)
        {
            c.Kneel(false);
            c.MoveTo(vp + ((Vector2)c.transform.position - vp).normalized * 0.7f, 0.6f);
        }
        Sfx.Play(SfxKind.Choir, 1f, vp, 0.7f);
        yield return d.Wait(2.8f);

        var behind = vp + (vp - front).normalized * 0.6f;
        foreach (var c in clones) if (c != null) c.Pop();
        Players.Teleport(loki, behind);
        StatusManager.SetHidden(loki, false);
        d.Cut(vp);
        yield return d.Wait(0.6f);

        Fx.WorldText(loki.transform, "<i>\"Kneel.\"</i>", Fx.LokiLight, 1.2f * d.TimeScale, 2.6f);
        yield return d.Wait(1f);
        d.TurnAround(victim);
        d.React(victim, "!", Fx.AlarmRed);
        yield return d.Wait(0.6f);

        Sfx.Play(SfxKind.Snap, 1f, vp);
        var runes = Fx.MakeSprite(Sprites.Runes, vp + new Vector2(0f, -0.3f), new Color(0.3f, 1f, 0.45f, 1f), 0.2f);
        Fx.Animate(runes, 2.5f * d.TimeScale, k =>
        {
            runes.transform.localScale = new Vector3(1.8f, 0.9f, 1f) * Mathf.Min(1f, k * 4f);
            runes.transform.Rotate(0, 0, 90f * Time.deltaTime);
        });
        d.Zoom(0.45f, 0.8f);
        for (float a = 1f; a >= 0f; a -= 0.25f) { d.Fade(victim, a); yield return d.Wait(0.12f); }
        Fx.Fragments(vp, Fx.LokiGreen, 70, 1.8f, 1.2f * d.TimeScale);
        d.Finish();
        yield return d.Wait(1.3f);

        var hand = Pos(loki) + new Vector2(0.35f, 0.15f);
        Fx.Fragments(hand, Fx.LokiGreen, 40, 1.6f, 0.9f * d.TimeScale, reverse: true);
        d.Pan(hand, 0.8f);
        yield return d.Wait(0.9f);
        Fx.Burst(hand, Fx.LokiLight, 0.6f, 0.2f);
        Sfx.Play(SfxKind.Bell, 1.2f, hand, 0.6f);
        yield return d.Wait(0.4f);
    }
}

/// <summary>2. A theatrical dagger duel; afterimages finish it, the victim vanishes in gold sparks.</summary>
public class KingsLastDance : FinisherAbility
{
    public override string Id => "kings_last_dance";
    public override string Name => "The King's Last Dance";
    public override string Label => "LAST DANCE";
    protected override float NominalSeconds => 8f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var loki = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        var side = Players.SafeDestination(vp, Vector2.left, 1.1f);
        Players.Teleport(loki, side);
        d.Zoom(0.55f, 0.8f);
        d.Pan((side + vp) * 0.5f, 0.8f);
        CinematicDirector.Pose(loki, -0.08f, 25f);
        Fx.WorldText(loki.transform, "*bows*", Fx.LokiGold, 1f * d.TimeScale, 2.4f);
        Sfx.Play(SfxKind.Chime, 1.3f, vp, 0.6f);
        yield return d.Wait(1.1f);
        CinematicDirector.Pose(loki, 0f, 0f);

        for (int i = 0; i < 3; i++)
        {
            CinematicDirector.Pose(victim, 0f, i % 2 == 0 ? -20f : 20f);
            d.React(victim, i == 2 ? "!!" : "!");
            var dodge = new Vector2(Mathf.Cos(i * 2.1f), Mathf.Sin(i * 2.1f));
            Fx.Burst(Pos(loki), Fx.LokiGreen, 0.8f, 0.2f);
            Players.Teleport(loki, Players.SafeDestination(vp, dodge, 0.9f));
            Sfx.Play(SfxKind.Slash, 1f + i * 0.2f, vp);
            Slash(vp + dodge * 0.4f, i * 60f);
            yield return d.Wait(0.45f);
        }
        CinematicDirector.Pose(victim, 0f, 0f);

        var dagger = Fx.MakeSprite(Sprites.Bar, Pos(loki), Fx.LokiLight, 1f);
        dagger.transform.localScale = new Vector3(0.5f, 0.12f, 1f);
        Vector2 up0 = Pos(loki);
        var behind = Players.SafeDestination(vp, vp - up0, 0.7f);
        Fx.Animate(dagger, 1f * d.TimeScale, k =>
        {
            var p = Vector2.Lerp(up0, behind, k) + new Vector2(0f, Mathf.Sin(k * Mathf.PI) * 2f);
            dagger.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            dagger.transform.Rotate(0, 0, 900f * Time.deltaTime);
        });
        d.Pan(vp + new Vector2(0f, 0.8f), 0.5f);
        yield return d.Wait(0.5f);
        Fx.Wave(vp, Fx.LokiGreen, 1.4f, 0.4f * d.TimeScale);
        Players.Teleport(loki, behind);
        yield return d.Wait(0.5f);

        d.Zoom(0.42f, 0.4f);
        if (d.Participant) Fx.ScreenTint(new Color(0.6f, 0.9f, 0.6f, 0.25f), 1.6f * d.TimeScale);
        for (int i = 0; i < 4; i++)
        {
            float a = i * Mathf.PI / 2f + 0.4f;
            var ghost = Clone(loki, vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 0.8f, 1.2f * d.TimeScale);
            ghost.SetTint(new Color(0.6f, 1f, 0.7f, 0.45f));
            ghost.Face(Mathf.Cos(a) > 0f);
            Slash(vp, i * 45f);
            Sfx.Play(SfxKind.Slash, 1.4f, vp, 0.6f);
            yield return d.Wait(0.3f);
        }

        var keepsake = Fx.MakeSprite(Sprites.Star, vp + new Vector2(0.5f, -0.35f), Fx.LokiGold, 0.3f);
        Object.Destroy(keepsake.gameObject, 4f);
        StatusManager.SetHidden(loki, true);
        if (victim.cosmetics != null) victim.cosmetics.SetOutline(true, new Il2CppSystem.Nullable<Color>(Fx.LokiGreen));
        yield return d.Wait(0.6f);
        if (victim.cosmetics != null) victim.cosmetics.SetOutline(false, new Il2CppSystem.Nullable<Color>(Fx.LokiGreen));
        Fx.Sparkles(vp, Fx.LokiGold, 40, 1.8f, 1f);
        Sfx.Play(SfxKind.Bell, 1f, vp);
        d.Fade(victim, 0f);
        d.Finish();
        yield return d.Wait(0.6f);
        StatusManager.SetHidden(loki, false);
        CinematicDirector.Pose(loki, -0.08f, 25f);
        Fx.WorldText(loki.transform, "*smug bow*", Fx.LokiGold, 1f * d.TimeScale, 2.4f);
        yield return d.Wait(0.7f);
        CinematicDirector.Pose(loki, 0f, 0f);
    }
}

/// <summary>3. Loki becomes the victim's reflection; the mirror cracks; the victim is pulled into a mirror.</summary>
public class BetrayalParadox : FinisherAbility
{
    public override string Id => "betrayal_paradox";
    public override string Name => "The Betrayal Paradox";
    public override string Label => "PARADOX";
    protected override float NominalSeconds => 10f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var loki = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Fx.Smoke(Pos(loki), Fx.LokiGreen, 8, 0.6f);
        if (victim.Data != null) loki.RawSetOutfit(victim.Data.DefaultOutfit, PlayerOutfitType.Shapeshifted);
        Players.Teleport(loki, Players.SafeDestination(vp, Vector2.right, 2.2f));
        d.Zoom(0.6f, 0.8f);
        Sfx.Play(SfxKind.Hum, 1.1f, vp);
        yield return d.Wait(1f);
        StatusManager.Instance?.RemoveStatus(loki, StatusEffect.Rooted);
        StatusManager.Instance?.ApplyStatus(loki, StatusEffect.Puppet, 1.4f * d.TimeScale, vp + new Vector2(0.9f, 0f));
        d.React(victim, "...?");
        yield return d.Wait(1.4f);
        StatusManager.Instance?.ApplyStatus(loki, StatusEffect.Rooted, 10f);

        var eyes = Fx.Aura(loki.transform, Sprites.Glow, new Color(0.3f, 1f, 0.4f, 0.9f), 0.35f, 4f * d.TimeScale, 0f, 0.3f);
        Fx.WorldText(loki.transform, ":)", Fx.LokiLight, 1f * d.TimeScale, 3f);
        yield return d.Wait(0.8f);

        for (int i = 0; i < 4; i++)
        {
            d.Cut(i % 2 == 0 ? Pos(loki) : Pos(victim));
            d.TurnAround(i % 2 == 0 ? loki : victim);
            Sfx.Play(SfxKind.Whoosh, 0.8f + i * 0.1f, vp, 0.4f);
            yield return d.Wait(0.45f);
        }
        d.Pan((Pos(loki) + Pos(victim)) * 0.5f, 0.3f);

        Fx.Cracks(Pos(loki) + new Vector2(0f, 0.2f), Fx.LokiGreen, 9, 0.9f, 1.4f * d.TimeScale, ctx.Rng.Next());
        Sfx.Play(SfxKind.Slash, 2f, vp);
        yield return d.Wait(1f);

        Fx.Fragments(Pos(loki), new Color(0.75f, 0.95f, 1f), 50, 1.2f, 1f * d.TimeScale);
        if (eyes != null) Object.Destroy(eyes);
        if (loki.Data != null) loki.RawSetOutfit(loki.Data.DefaultOutfit, PlayerOutfitType.Default);
        var behind = Players.SafeDestination(vp, Vector2.left, 0.6f);
        Players.Teleport(loki, behind);
        d.Cut(vp);
        yield return d.Wait(0.8f);

        var thread = Fx.MakeSprite(Sprites.Bar, (vp + behind) * 0.5f, Fx.LokiLight, 1f);
        float len = Vector2.Distance(vp, behind) + 0.6f;
        Fx.Animate(thread, 1f * d.TimeScale, k =>
        {
            thread.transform.localScale = new Vector3(len * (1f - k), 0.05f, 1f);
            thread.transform.Rotate(0, 0, 200f * Time.deltaTime);
        });
        yield return d.Wait(1f);

        var mirrorPos = vp + new Vector2(0f, 0.15f);
        var mirror = Prop("prop_mirror", mirrorPos, 0.1f);
        Fx.Animate(mirror, 3f * d.TimeScale, k => mirror.transform.localScale = Vector3.one * Mathf.Min(1.5f, k * 8f));
        Sfx.Play(SfxKind.Chime, 0.7f, vp);
        for (float a = 1f; a >= 0f; a -= 0.2f) { d.Fade(victim, a); yield return d.Wait(0.1f); }
        d.Finish();
        yield return d.Wait(0.4f);
        Players.Teleport(loki, mirrorPos);
        yield return d.Wait(0.4f);
        if (mirror != null) Object.Destroy(mirror.gameObject);
        Fx.Fragments(mirrorPos, new Color(0.7f, 0.9f, 1f), 60, 1.5f, 1.1f * d.TimeScale);
        Sfx.Play(SfxKind.Slash, 2.6f, vp);
        var echo = Clone(loki, mirrorPos + new Vector2(0.5f, 0f), 1.2f * d.TimeScale);
        echo.SetTint(new Color(0.6f, 1f, 0.7f, 0.4f));
        Fx.WorldText(echo.transform, "hehehe", Fx.LokiLight, 1f * d.TimeScale, 2.2f);
        yield return d.Wait(1.2f);
    }
}

/// <summary>4. Marionette threads lift the victim, who becomes a tiny wooden puppet in Loki's coat.</summary>
public class PuppetMaster : FinisherAbility
{
    public override string Id => "puppet_master";
    public override string Name => "The God Who Pulls the Strings";
    public override string Label => "PUPPETEER";
    protected override float NominalSeconds => 9f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var loki = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Players.Teleport(loki, Players.SafeDestination(vp, Vector2.left, 1.6f));
        if (d.Participant) Fx.ScreenTint(new Color(0f, 0f, 0f, 0.55f), 8f * d.TimeScale);
        var under = Fx.Aura(loki.transform, Sprites.Glow, new Color(0.2f, 1f, 0.35f, 0.7f), 1.4f, 8f * d.TimeScale, 0f, -0.35f);
        d.Zoom(0.6f, 1f);
        Sfx.Play(SfxKind.Swell, 0.8f, vp, 0.6f);
        yield return d.Wait(1f);

        var threads = new List<SpriteRenderer>();
        for (int i = 0; i < 4; i++)
        {
            var a0 = vp + new Vector2(-0.3f + i * 0.2f, 0f);
            var t = Fx.MakeSprite(Sprites.Bar, a0, Fx.LokiLight, 1f, "PuppetThread");
            t.transform.rotation = Quaternion.Euler(0, 0, 90f);
            threads.Add(t);
            Fx.Animate(t, 7f * d.TimeScale, k =>
            {
                float l = 3f * Mathf.Min(1f, k * 7f);
                float lift = Mathf.Clamp01((k - 0.75f) / 0.25f) * 3f;
                t.transform.position = new Vector3(a0.x, a0.y + 3.2f - l * 0.5f + lift, a0.y / 1000f - 0.6f);
                t.transform.localScale = new Vector3(l, 0.04f, 1f);
            });
            Sfx.Play(SfxKind.Creak, 0.9f + i * 0.1f, vp, 0.5f);
            yield return d.Wait(0.2f);
        }
        d.React(victim, "?!");
        for (float k = 0; k <= 1f; k += 0.1f) { CinematicDirector.Pose(victim, k * 0.35f, 0f); yield return d.Wait(0.05f); }
        d.Pan(vp + new Vector2(0f, 0.6f), 0.8f);

        foreach (var tilt in new[] { -25f, 20f, -10f, 30f, -30f, 0f })
        {
            CinematicDirector.Pose(victim, 0.35f + Random.Range(-0.05f, 0.1f), tilt);
            d.TurnAround(victim);
            Sfx.Play(SfxKind.Creak, 1.1f + tilt / 100f, vp, 0.45f);
            yield return d.Wait(0.3f);
        }

        var frame = Fx.MakeSprite(Sprites.Runes, Pos(loki) + new Vector2(0f, 1.6f), new Color(0.3f, 1f, 0.45f, 0f), 2.4f);
        Fx.Animate(frame, 2.5f * d.TimeScale, k =>
        {
            frame.color = new Color(0.3f, 1f, 0.45f, Mathf.Sin(k * Mathf.PI) * 0.8f);
            frame.transform.Rotate(0, 0, 40f * Time.deltaTime);
        });
        d.Zoom(0.8f, 0.6f);
        yield return d.Wait(1.2f);

        Fx.Smoke(vp, Fx.LokiGreen, 12, 0.9f);
        d.Fade(victim, 0f);
        var puppet = Prop("prop_puppet", vp + new Vector2(0f, 0.3f), 0.45f);
        d.Finish();
        yield return d.Wait(1f);

        Vector2 from = puppet.transform.position;
        var coat = Pos(loki) + new Vector2(0.2f, 0f);
        Fx.Animate(puppet, 0.8f * d.TimeScale, k =>
        {
            var p = Vector2.Lerp(from, coat, k) + new Vector2(0f, Mathf.Sin(k * Mathf.PI) * 0.6f);
            puppet.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            puppet.transform.localScale = Vector3.one * 0.45f * (1f - k * 0.6f);
        });
        d.Pan(coat, 0.6f);
        yield return d.Wait(0.9f);
        Sfx.Play(SfxKind.Bell, 1f, coat);
        var peek = Prop("prop_puppet", coat + new Vector2(0.15f, 0.2f), 0.18f);
        Fx.Animate(peek, 1f * d.TimeScale, k =>
        {
            float h = k < 0.5f ? k * 2f : (1f - k) * 2f;
            peek.transform.position = new Vector3(coat.x + 0.15f, coat.y + 0.05f + h * 0.2f, coat.y / 1000f - 0.6f);
        });
        yield return d.Wait(1f);
        if (under != null) Object.Destroy(under);
        foreach (var t in threads) if (t != null) Object.Destroy(t.gameObject);
    }
}

/// <summary>5. Ultimate: a throne room full of Lokis folds into a tiny cube.</summary>
public class KneelBeforeTheTrickster : FinisherAbility
{
    public override string Id => "kneel_before_trickster";
    public override string Name => "Kneel Before the Trickster";
    public override string Label => "KNEEL";
    public override bool IsUltimate => true;
    protected override float NominalSeconds => 13f;

    protected override IEnumerator Sequence(CinematicDirector d, CastContext ctx)
    {
        var loki = ctx.Caster; var victim = d.Victim; Vector2 vp = Pos(victim);
        Sfx.Play(SfxKind.Swell, 0.9f, vp);
        Fx.Wave(Pos(loki), Fx.LokiGreen, 8f, 1.2f * d.TimeScale);
        d.Zoom(0.8f, 1f);
        yield return d.Wait(1.2f);

        if (d.Participant)
        {
            Fx.ScreenTint(new Color(0.02f, 0.03f, 0.12f, 0.7f), 11f * d.TimeScale);
            Fx.Toast("THE THRONE OF ASGARD", Fx.LokiGold, 2f * d.TimeScale, 4.5f, 2f);
        }
        var throne = Prop("prop_throne", vp + new Vector2(0f, 2.4f), 0.1f);
        Fx.Animate(throne, 10f * d.TimeScale, k => throne.transform.localScale = Vector3.one * Mathf.Min(1.6f, k * 16f));
        var halls = new List<SpriteRenderer>();
        for (int i = 0; i < 3; i++)
        {
            var ring = Fx.MakeSprite(Sprites.Runes, vp, new Color(1f, 0.8f, 0.3f, 0.35f), 3f + i * 2.2f);
            halls.Add(ring);
            float spin = i % 2 == 0 ? 10f : -14f;
            Fx.Animate(ring, 10f * d.TimeScale, k => ring.transform.Rotate(0, 0, spin * Time.deltaTime));
        }

        var army = new List<LokiIllusion>();
        for (int ri = 0; ri < 2; ri++)
        {
            int n = ri == 0 ? 8 : 12; float r = ri == 0 ? 2.2f : 3.6f;
            for (int i = 0; i < n; i++)
            {
                float a = i * Mathf.PI * 2f / n + ri * 0.2f;
                var c = Clone(loki, vp + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * r, 12f * d.TimeScale);
                c.Face(Mathf.Cos(a) > 0f);
                army.Add(c);
            }
            yield return d.Wait(0.4f);
        }
        d.React(victim, "?");
        Sfx.Play(SfxKind.Choir, 1f, vp);
        for (int i = 0; i < army.Count; i += 2)
        {
            if (army[i] != null) Fx.WorldText(army[i].transform, "<i>Kneel.</i>", Fx.LokiLight, 0.9f * d.TimeScale, 2f);
            yield return d.Wait(0.08f);
        }
        d.Zoom(1.7f, 1.4f);
        yield return d.Wait(1.5f);

        foreach (var c in army) if (c != null) c.Kneel(true);
        Sfx.Play(SfxKind.Boom, 0.7f, vp, 0.6f);
        StatusManager.SetHidden(loki, true);
        yield return d.Wait(0.5f);
        Players.Teleport(loki, vp + new Vector2(0f, 2.1f));
        yield return d.Wait(0.3f);
        StatusManager.SetHidden(loki, false);
        Fx.Burst(Pos(loki), Fx.LokiGold, 2f, 0.5f);
        d.Zoom(0.7f, 1f);
        d.Pan(vp + new Vector2(0f, 1f), 1f);
        yield return d.Wait(1f);

        StatusManager.Instance?.RemoveStatus(loki, StatusEffect.Rooted);
        StatusManager.Instance?.ApplyStatus(loki, StatusEffect.Puppet, 1.2f * d.TimeScale, vp + new Vector2(0f, 0.6f));
        yield return d.Wait(1.3f);
        StatusManager.Instance?.ApplyStatus(loki, StatusEffect.Rooted, 10f);
        Fx.WorldText(loki.transform, "*offers a hand*", Fx.LokiGold, 1.2f * d.TimeScale, 2.2f);
        yield return d.Wait(1f);

        var rune = Fx.MakeSprite(Sprites.Runes, vp + new Vector2(0f, 0.2f), new Color(0.3f, 1f, 0.45f, 1f), 0.2f);
        Fx.Animate(rune, 2f * d.TimeScale, k =>
        {
            rune.transform.localScale = new Vector3(2.4f, 1.3f, 1f) * Mathf.Min(1f, k * 3f);
            rune.transform.Rotate(0, 0, 120f * Time.deltaTime);
        });
        Sfx.Play(SfxKind.Chime, 0.8f, vp);
        yield return d.Wait(0.8f);

        foreach (var c in army) if (c != null) { c.Kneel(false); c.MoveTo(vp, 7f); }
        foreach (var h in halls) if (h != null) Fx.Animate(h, 0.8f * d.TimeScale, k => h.transform.localScale *= 0.94f);
        d.Zoom(0.5f, 0.8f);
        yield return d.Wait(0.8f);
        foreach (var c in army) if (c != null) c.Pop();
        if (throne != null) Object.Destroy(throne.gameObject, 0.5f);
        d.Fade(victim, 0f);
        var cube = Fx.MakeSprite(Sprites.Box, vp, Fx.LokiGreen, 0.3f);
        Fx.Animate(cube, 3f * d.TimeScale, k => cube.transform.Rotate(0, 0, 180f * Time.deltaTime));
        d.Finish();
        yield return d.Wait(1.5f);   // silence

        Vector2 c0 = cube.transform.position, c1 = c0 + new Vector2(2f, 1.2f);
        Fx.Animate(cube, 0.5f * d.TimeScale, k =>
        {
            var p = Vector2.Lerp(c0, c1, k);
            cube.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
        });
        yield return d.Wait(0.5f);
        Sfx.Play(SfxKind.Snap, 1f, vp);
        Fx.Burst(c1, Fx.LokiGreen, 3f, 0.6f);
        Fx.Sparkles(c1, Fx.LokiLight, 50, 3f, 1.2f);
        yield return d.Wait(0.6f);
    }
}

/// <summary>Hidden ability: click the map to teleport (no cooldown).</summary>
public class MapTeleport : AbilityBase
{
    public override string Id => "map_teleport";
    public override string Name => "Map Teleport";
    public override string Category => "Hidden";
    public Vector2 PendingDestination { get; set; }

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
        => point = PendingDestination;

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, ctx.CasterPos);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, ctx.Point);
        Sfx.Play(SfxKind.Whoosh, 1.2f, ctx.Point, 0.6f);
        Players.Teleport(ctx.Caster, ctx.Point);
    }
}

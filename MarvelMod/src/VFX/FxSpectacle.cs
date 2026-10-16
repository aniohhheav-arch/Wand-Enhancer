using System;
using System.Collections;
using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.Config;
using Reactor.Utilities;
using UnityEngine;
using Object = UnityEngine.Object;
using Random = UnityEngine.Random;

namespace MarvelMod.VFX;

/// <summary>What an impact looks like (each has its own shapes and motion, not just a colour).</summary>
public enum ImpactStyle
{
    /// <summary>Brute force: flash, flat shock ring, radial streaks, ground cracks, flying debris.</summary>
    Heavy,
    /// <summary>Spellwork: sigil snaps in, glyph stars fly out, soft bloom.</summary>
    Magic,
    /// <summary>Lightning: branching arcs jump out of the point, white flash.</summary>
    Electric,
    /// <summary>Reality corruption: glitch bars, glass shards, offset ghost rings.</summary>
    Glitch,
    /// <summary>Space: implosion first, then a wide slow ring.</summary>
    Cosmic,
    /// <summary>Cartoon: comic word, bouncing stars.</summary>
    Comedy,
    /// <summary>Soul / dread: dark pulse, rising wisps.</summary>
    Eerie,
    /// <summary>Clock rings that freeze and reverse.</summary>
    Time,
    /// <summary>Chaos magic: red tendril burst, warped rings.</summary>
    Chaos,
}

/// <summary>What stays behind after an impact.</summary>
public enum Residue { Embers, Smoke, Runes, Debris, Static, Frost }

/// <summary>
/// Shared "big moment" building blocks: charge-up, anticipation, release, lightning, aftermath.
/// All of it is cosmetic and local (every client runs it from the same synced cast), sprites come
/// from a small pool, and Fx.ClearAll() removes anything still on screen (arena reset, meetings).
/// </summary>
public static partial class Fx
{
    // ── pooling + live registry ──────────────────────────────────────────────

    private static readonly Stack<SpriteRenderer> Pool = new();
    private static readonly List<GameObject> Live = new();
    private const int PoolCap = 160;

    /// <summary>Short-lived particle sprite from the pool. Only Fx internals use this (never handed out).</summary>
    private static SpriteRenderer Pooled(Sprite sprite, Vector2 pos, Color color, float scale)
    {
        SpriteRenderer? sr = null;
        while (Pool.Count > 0 && sr == null) sr = Pool.Pop();
        if (sr == null) return MakeSprite(sprite, pos, color, scale, "MarvelFxP");
        var go = sr.gameObject;
        go.SetActive(true);
        go.layer = WorldLayer;
        var t = go.transform;
        t.position = new Vector3(pos.x, pos.y, pos.y / 1000f - 0.5f);
        t.rotation = Quaternion.identity;
        t.localScale = Vector3.one * scale;
        sr.sprite = sprite;
        sr.color = color;
        return sr;
    }

    private static void Recycle(SpriteRenderer sr)
    {
        if (sr == null) return;
        if (Pool.Count >= PoolCap) { Object.Destroy(sr.gameObject); return; }
        sr.gameObject.SetActive(false);
        Pool.Push(sr);
    }

    private static void AnimatePooled(SpriteRenderer sr, float dur, Action<float> step)
        => Coroutines.Start(CoAnimatePooled(sr, dur, step));

    private static IEnumerator CoAnimatePooled(SpriteRenderer sr, float dur, Action<float> step)
    {
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (sr == null) yield break;
            step(t / dur);
            yield return null;
        }
        Recycle(sr);
    }

    private static void Track(GameObject go)
    {
        if (Live.Count > 400) Live.RemoveAll(g => g == null);
        Live.Add(go);
    }

    /// <summary>Removes every effect still on screen and stops sounds (arena reset / meeting).</summary>
    public static int ClearAll()
    {
        int n = 0;
        foreach (var go in Live)
            if (go != null) { Object.Destroy(go); n++; }
        Live.Clear();
        Pool.Clear();
        Sfx.StopAll();
        return n;
    }

    /// <summary>Where console tests play (a little in front of you).</summary>
    public static Vector2 TestOffset => new(1.6f, 0f);

    /// <summary>Duration multiplier from the animation speed setting (speed 2 = half as long). Multiply durations by this.</summary>
    public static float Tempo => 1f / Mathf.Clamp(ModConfig.Data.AnimationSpeed, 0.4f, 2.5f);

    // ── build-up ─────────────────────────────────────────────────────────────

    /// <summary>
    /// Energy gathering: motes stream inward into the caster, a glow swells, a rising tone plays,
    /// then a short anticipation squeeze. Yield on it inside a coroutine.
    /// </summary>
    public static IEnumerator Charge(Transform caster, Color c, float dur, int motes = 14, SfxKind sound = SfxKind.Rise)
    {
        dur *= Tempo;
        if (caster == null) yield break;
        Sfx.Play(sound, 1f, caster.position, 0.55f);
        SpriteRenderer? glow = Enabled ? MakeSprite(Sprites.Glow, caster.position, A(c, 0f), 0.2f, "MarvelCharge") : null;
        if (glow != null) Track(glow.gameObject);
        int n = Enabled ? Mathf.Max(3, Mathf.RoundToInt(motes * Quality)) : 0;
        float every = dur * 0.8f / Mathf.Max(1, n), next = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (caster == null) break;
            Vector2 p = caster.position;
            float k = t / dur;
            if (glow != null)
            {
                glow.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.55f);
                glow.transform.localScale = Vector3.one * Mathf.Lerp(0.2f, 1.6f, k) * (1f + 0.08f * Mathf.Sin(t * 30f));
                glow.color = A(c, Mathf.Lerp(0f, 0.85f, k));
            }
            if (t >= next && k < 0.85f)
            {
                next += every;
                Mote(caster, c, Random.Range(1.4f, 2.4f), 0.35f * Tempo);
            }
            yield return null;
        }
        // anticipation: a quick inward squeeze and a tiny beat of stillness
        if (glow != null)
        {
            for (float t = 0; t < 0.12f; t += Time.deltaTime)
            {
                if (glow == null) break;
                glow.transform.localScale = Vector3.one * Mathf.Lerp(1.6f, 0.7f, t / 0.12f);
                yield return null;
            }
            if (glow != null) Object.Destroy(glow.gameObject);
        }
        yield return new WaitForSeconds(0.05f);
    }

    /// <summary>One mote flying from a ring around `to` into its centre.</summary>
    private static void Mote(Transform to, Color c, float radius, float dur)
    {
        if (!ModConfig.Data.VfxParticles) return;
        float ang = Random.Range(0f, Mathf.PI * 2f);
        var off = new Vector2(Mathf.Cos(ang), Mathf.Sin(ang)) * radius;
        var col = Color.Lerp(c, Color.white, Random.Range(0f, 0.4f));
        var sr = Pooled(Sprites.Star, (Vector2)to.position + off, col, 0.22f);
        AnimatePooled(sr, dur, k =>
        {
            if (to == null) return;
            var p = (Vector2)to.position + off * (1f - k * k);
            sr.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            sr.transform.localScale = Vector3.one * 0.22f * (1f - k * 0.5f);
            sr.color = A(col, Mathf.Min(1f, k * 4f));
        });
    }

    /// <summary>
    /// Thanos: an orb in the stone's colour leaves the stone's spot above the gauntlet, spirals in and
    /// slams into the fist. Yield on it so the release happens when the energy arrives.
    /// </summary>
    public static IEnumerator StoneToGauntlet(Transform caster, Color c, float dur = 0.55f)
    {
        dur *= Tempo;
        if (!Enabled || caster == null) { yield return new WaitForSeconds(dur); yield break; }
        var orb = MakeSprite(Sprites.Glow, caster.position, A(c, 1f), 0.5f, "MarvelStoneOrb");
        var core = MakeSprite(Sprites.Star, caster.position, Color.white, 0.25f, "MarvelStoneCore");
        Track(orb.gameObject); Track(core.gameObject);
        Sfx.Play(SfxKind.Shimmer, 1.3f, caster.position, 0.4f);
        float trail = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (caster == null || orb == null || core == null) break;
            float k = t / dur;
            float ang = Mathf.Lerp(1.6f, 6.2f, k);
            float rad = Mathf.Lerp(1.3f, 0f, k * k);
            var hand = (Vector2)caster.position + new Vector2(0.35f, -0.05f);
            var p = hand + new Vector2(Mathf.Cos(ang), Mathf.Sin(ang)) * rad + new Vector2(0f, (1f - k) * 0.6f);
            orb.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.6f);
            core.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.61f);
            core.transform.Rotate(0, 0, 900f * Time.deltaTime);
            trail += Time.deltaTime;
            if (trail > 0.025f / Quality)
            {
                trail = 0f;
                var bit = Pooled(Sprites.Glow, p, A(c, 0.6f), 0.3f);
                AnimatePooled(bit, 0.25f, q => { bit.transform.localScale = Vector3.one * 0.3f * (1f - q); bit.color = A(c, 0.6f * (1f - q)); });
            }
            yield return null;
        }
        if (orb != null) Object.Destroy(orb.gameObject);
        if (core != null) Object.Destroy(core.gameObject);
        if (caster != null)
        {
            var hand = (Vector2)caster.position + new Vector2(0.35f, -0.05f);
            Burst(hand, c, 0.9f, 0.25f);
            Sfx.Play(SfxKind.Pop, 0.7f, hand, 0.5f);
        }
    }

    /// <summary>Glowing symbols that fly in and lock into a rotating sigil, then hold for `hold` seconds.</summary>
    public static GameObject? Sigil(Vector2 p, Color c, float size, float hold, bool spinBack = true)
    {
        if (!Enabled) return null;
        var root = new GameObject("MarvelSigil") { layer = WorldLayer };
        root.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.45f);
        Track(root);
        var outer = MakeSprite(Sprites.Runes, p, A(c, 0f), size, "SigilOuter");
        var inner = MakeSprite(Sprites.Runes, p, A(Color.Lerp(c, Color.white, 0.3f), 0f), size * 0.62f, "SigilInner");
        var ring = MakeSprite(Sprites.Ring, p, A(c, 0f), size * 1.15f, "SigilRing");
        foreach (var s in new[] { outer, inner, ring }) s.transform.SetParent(root.transform, true);
        int glyphs = Mathf.Max(4, Mathf.RoundToInt(8 * Quality));
        for (int i = 0; i < glyphs; i++)
        {
            float a = i * Mathf.PI * 2f / glyphs;
            var slot = new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * size * 0.42f;
            var from = slot * 3.2f;
            var g = MakeSprite(Sprites.Star, p + from, A(Color.white, 0f), 0.25f * size, "SigilGlyph");
            g.transform.SetParent(root.transform, true);
            float delay = i * 0.03f;
            Coroutines.Start(CoGlyph(g, root.transform, from, slot, delay));
        }
        Coroutines.Start(CoSigil(root, outer, inner, ring, c, size, hold * Tempo, spinBack));
        return root;
    }

    private static IEnumerator CoGlyph(SpriteRenderer g, Transform root, Vector2 from, Vector2 slot, float delay)
    {
        for (float t = 0; t < 0.35f + delay; t += Time.deltaTime)
        {
            if (g == null || root == null) yield break;
            float k = Mathf.Clamp01((t - delay) / 0.35f);
            var q = Vector2.Lerp(from, slot, EaseOut(k));
            g.transform.localPosition = new Vector3(q.x, q.y, -0.02f);
            g.transform.Rotate(0, 0, 600f * Time.deltaTime);
            g.color = A(Color.white, k);
            yield return null;
        }
    }

    private static IEnumerator CoSigil(GameObject root, SpriteRenderer outer, SpriteRenderer inner, SpriteRenderer ring, Color c, float size, float hold, bool spinBack)
    {
        float total = 0.35f + hold + 0.3f;
        for (float t = 0; t < total; t += Time.deltaTime)
        {
            if (root == null) yield break;
            float appear = Mathf.Clamp01(t / 0.35f), fade = t > total - 0.3f ? (total - t) / 0.3f : 1f;
            float a = appear * fade;
            if (outer != null) { outer.color = A(c, 0.9f * a); outer.transform.Rotate(0, 0, 70f * Time.deltaTime); outer.transform.localScale = Vector3.one * size * Mathf.Lerp(1.6f, 1f, EaseOut(appear)); }
            if (inner != null) { inner.color = A(Color.Lerp(c, Color.white, 0.3f), 0.85f * a); inner.transform.Rotate(0, 0, (spinBack ? -130f : 130f) * Time.deltaTime); }
            if (ring != null) { ring.color = A(c, 0.7f * a * (0.75f + 0.25f * Mathf.Sin(t * 12f))); }
            if (fade < 1f)
                foreach (var g in root.GetComponentsInChildren<SpriteRenderer>())
                    if (g != null && g.name == "SigilGlyph") g.color = A(Color.white, fade);
            yield return null;
        }
        if (root != null) Object.Destroy(root);
    }

    // ── release + impact ─────────────────────────────────────────────────────

    /// <summary>
    /// The payoff: layered visuals + layered sound in the chosen style. `size` 0.5 (jab) .. 2.5 (ultimate).
    /// </summary>
    public static void Release(Vector2 p, Color c, ImpactStyle style, float size = 1f, bool cinematic = false)
    {
        var cue = style switch
        {
            ImpactStyle.Heavy => SfxCue.Heavy, ImpactStyle.Magic => SfxCue.Magic, ImpactStyle.Electric => SfxCue.Electric,
            ImpactStyle.Glitch => SfxCue.Glitch, ImpactStyle.Cosmic => SfxCue.Cosmic, ImpactStyle.Comedy => SfxCue.Comedy,
            ImpactStyle.Eerie => SfxCue.Eerie, ImpactStyle.Time => SfxCue.Time, _ => SfxCue.Chaos,
        };
        Sfx.Cue(cue, p, size, cinematic);
        if (!Enabled) return;
        Flare(p, Color.Lerp(c, Color.white, 0.6f), 0.6f * size, 0.18f);
        switch (style)
        {
            case ImpactStyle.Heavy:
                ShockRing(p, c, 2.2f * size, 0.45f, squash: 0.6f);
                Streaks(p, Color.Lerp(c, Color.white, 0.4f), 10, 1.4f * size);
                Cracks(p + new Vector2(0f, -0.25f), c, 6 + (int)(size * 2), 1.2f * size, 1.4f, Random.Range(0, 99999));
                Debris(p, Color.Lerp(c, new Color(0.4f, 0.35f, 0.3f), 0.5f), Mathf.RoundToInt(8 * size), 1.5f * size);
                Shake(0.25f + 0.1f * size, 0.35f * size);
                break;
            case ImpactStyle.Magic:
                Sigil(p, c, 1.3f * size, 0.15f);
                Sparkles(p, c, Mathf.RoundToInt(10 * size), 1.4f * size, 0.6f);
                ShockRing(p, c, 1.6f * size, 0.5f);
                break;
            case ImpactStyle.Electric:
                for (int i = 0; i < 3 + (int)size * 2; i++)
                {
                    float a = Random.Range(0f, Mathf.PI * 2f);
                    Lightning(p, p + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * Random.Range(1f, 2.2f) * size, Color.Lerp(c, Color.white, 0.3f), 0.25f, 2);
                }
                ShockRing(p, Color.white, 1.4f * size, 0.25f);
                Shake(0.2f, 0.25f * size);
                break;
            case ImpactStyle.Glitch:
                for (int i = 0; i < 3; i++) GhostRing(p + Random.insideUnitCircle * 0.25f, i == 0 ? c : i == 1 ? Color.cyan : Color.magenta, 1.5f * size);
                Fragments(p, c, Mathf.RoundToInt(16 * size), 1.4f * size, 0.9f);
                Bars(p, c, size);
                break;
            case ImpactStyle.Cosmic:
                Coroutines.Start(CoImplodeThenBlow(p, c, size));
                break;
            case ImpactStyle.Comedy:
                Comic(p, ComicWords[Random.Range(0, ComicWords.Length)], Color.Lerp(c, Color.yellow, 0.4f), size);
                Bounce(p, Color.yellow, 6);
                break;
            case ImpactStyle.Eerie:
                ShockRing(p, new Color(0.05f, 0.02f, 0.1f, 1f), 2f * size, 0.7f);
                Rising(p, c, Mathf.RoundToInt(8 * size), 1.6f * size, 1.2f);
                break;
            case ImpactStyle.Time:
                for (int i = 0; i < 2; i++)
                {
                    var r = Pooled(Sprites.Runes, p, A(c, 0.9f), 1f + i * 0.6f);
                    float dir = i == 0 ? 1f : -1f;
                    AnimatePooled(r, 0.8f, k =>
                    {
                        float spin = k < 0.35f ? 520f : k < 0.5f ? 0f : -1100f;   // turn, freeze, rewind
                        r.transform.Rotate(0, 0, dir * spin * Time.deltaTime);
                        r.color = A(c, 0.9f * (1f - k));
                    });
                }
                ShockRing(p, c, 1.6f * size, 0.6f);
                break;
            case ImpactStyle.Chaos:
                for (int i = 0; i < 5 + (int)(size * 2); i++)
                {
                    float a = i * Mathf.PI * 2f / (5 + (int)(size * 2));
                    Tendril(p, p + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.8f * size, c, 0.45f);
                }
                ShockRing(p, c, 2f * size, 0.5f, squash: 0.85f);
                Shake(0.2f, 0.2f * size);
                break;
        }
    }

    private static readonly string[] ComicWords = { "BONK!", "POW!", "WHAM!", "BOOP!", "KAPOW!", "YEET!" };

    /// <summary>Short white-hot core flash.</summary>
    public static void Flare(Vector2 p, Color c, float size, float dur)
    {
        if (!Enabled) return;
        var sr = Pooled(Sprites.Glow, p, A(c, 1f), size);
        AnimatePooled(sr, dur, k => { sr.transform.localScale = Vector3.one * size * (1f + k * 1.5f); sr.color = A(c, 1f - k); });
    }

    /// <summary>A ring travelling outward; squash &lt; 1 makes it a flattened ground ring.</summary>
    public static void ShockRing(Vector2 p, Color c, float size, float dur, float squash = 1f)
    {
        if (!Enabled) return;
        var sr = Pooled(Sprites.Ring, p, A(c, 1f), 0.1f);
        AnimatePooled(sr, dur, k =>
        {
            float s = Mathf.Lerp(0.1f, size, EaseOut(k));
            sr.transform.localScale = new Vector3(s, s * squash, 1f);
            sr.color = A(c, 1f - k);
        });
    }

    /// <summary>Radial speed lines bursting outward.</summary>
    public static void Streaks(Vector2 p, Color c, int count, float len)
    {
        if (!Enabled || !ModConfig.Data.VfxParticles) return;
        count = Mathf.Max(3, Mathf.RoundToInt(count * Quality));
        for (int i = 0; i < count; i++)
        {
            float a = Random.Range(0f, 360f);
            var dir = new Vector2(Mathf.Cos(a * Mathf.Deg2Rad), Mathf.Sin(a * Mathf.Deg2Rad));
            float l = len * Random.Range(0.6f, 1.2f);
            var sr = Pooled(Sprites.Bar, p, A(c, 0.9f), 1f);
            sr.transform.rotation = Quaternion.Euler(0, 0, a);
            AnimatePooled(sr, 0.28f, k =>
            {
                var q = p + dir * l * EaseOut(k);
                sr.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.6f);
                sr.transform.localScale = new Vector3(l * 0.5f * (1f - k), 0.06f, 1f);
                sr.color = A(c, 0.9f * (1f - k));
            });
        }
    }

    /// <summary>Chunks thrown up that fall back with gravity, bounce once and fade.</summary>
    public static void Debris(Vector2 p, Color c, int count, float force)
    {
        if (!Enabled || !ModConfig.Data.VfxParticles) return;
        count = Mathf.Max(2, Mathf.RoundToInt(count * Quality));
        for (int i = 0; i < count; i++)
        {
            var v = new Vector2(Random.Range(-1f, 1f) * force, Random.Range(1.2f, 2.6f) * force);
            float size = Random.Range(0.1f, 0.24f), spin = Random.Range(-720f, 720f), life = Random.Range(0.8f, 1.2f);
            var col = Color.Lerp(c, Color.black, Random.Range(0f, 0.3f));
            var sr = Pooled(Sprites.Shard, p, col, size);
            float groundY = p.y - 0.35f;
            AnimatePooled(sr, life, k =>
            {
                float t = k * life;
                float y = p.y + v.y * t - 5.5f * t * t;
                if (y < groundY) y = groundY + Mathf.Abs(Mathf.Sin(t * 9f)) * 0.08f * (1f - k);
                var q = new Vector2(p.x + v.x * t * 0.6f, y);
                sr.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.55f);
                sr.transform.Rotate(0, 0, spin * Time.deltaTime * (1f - k));
                sr.color = A(col, k > 0.7f ? (1f - k) / 0.3f : 1f);
            });
        }
    }

    /// <summary>Lightning: jagged main bolt with forks, flickering for `dur` seconds.</summary>
    public static void Lightning(Vector2 a, Vector2 b, Color c, float dur = 0.3f, int forks = 3, float jag = 0.35f)
    {
        if (!Enabled) return;
        var pts = Jagged(a, b, Mathf.Clamp((int)(Vector2.Distance(a, b) * 3f), 4, 14), jag);
        DrawBolt(pts, c, 0.12f, dur);
        int f = Mathf.RoundToInt(forks * Mathf.Min(1.2f, Quality));
        for (int i = 0; i < f && pts.Count > 3; i++)
        {
            var from = pts[Random.Range(1, pts.Count - 1)];
            var dir = (b - a).normalized;
            var side = new Vector2(-dir.y, dir.x) * (Random.value < 0.5f ? -1f : 1f);
            var to = from + (dir * 0.5f + side) * Random.Range(0.5f, 1.2f);
            DrawBolt(Jagged(from, to, 4, jag * 0.7f), c, 0.06f, dur * 0.8f);
        }
    }

    private static List<Vector2> Jagged(Vector2 a, Vector2 b, int segs, float jag)
    {
        var pts = new List<Vector2> { a };
        var dir = b - a;
        var n = new Vector2(-dir.y, dir.x).normalized;
        for (int i = 1; i < segs; i++)
            pts.Add(a + dir * ((float)i / segs) + n * Random.Range(-jag, jag));
        pts.Add(b);
        return pts;
    }

    private static void DrawBolt(List<Vector2> pts, Color c, float width, float dur)
    {
        for (int i = 0; i < pts.Count - 1; i++)
        {
            var p = pts[i]; var q = pts[i + 1];
            var d = q - p;
            float len = d.magnitude, ang = Mathf.Atan2(d.y, d.x) * Mathf.Rad2Deg;
            foreach (var (col, w) in new[] { (A(c, 0.8f), width * 2.4f), (A(Color.white, 1f), width) })
            {
                var sr = Pooled(Sprites.Bar, (p + q) * 0.5f, col, 1f);
                sr.transform.rotation = Quaternion.Euler(0, 0, ang);
                sr.transform.localScale = new Vector3(len, w, 1f);
                float baseA = col.a;
                AnimatePooled(sr, dur, k =>
                {
                    float flicker = (Mathf.Sin(k * 70f) > -0.3f) ? 1f : 0.25f;
                    sr.color = A(col, baseA * flicker * (1f - k * k));
                });
            }
        }
    }

    /// <summary>A storm bolt from the top of the screen down to a point, with a warning marker first.</summary>
    public static IEnumerator StrikeFromSky(Vector2 p, Color c, float warn, float size = 1f)
    {
        warn *= Tempo;
        if (Enabled && warn > 0f)
        {
            var mark = MakeSprite(Sprites.Ring, p, A(c, 0.6f), 0.6f * size, "MarvelStrikeMark");
            Track(mark.gameObject);
            Sfx.Play(SfxKind.Sizzle, 1.2f, p, 0.4f);
            for (float t = 0; t < warn; t += Time.deltaTime)
            {
                if (mark == null) break;
                float k = t / warn;
                mark.transform.localScale = new Vector3(1f, 0.6f, 1f) * Mathf.Lerp(1.4f, 0.6f, k) * size;
                mark.color = A(c, 0.4f + 0.5f * Mathf.Abs(Mathf.Sin(t * (8f + k * 20f))));
                yield return null;
            }
            if (mark != null) Object.Destroy(mark.gameObject);
        }
        var cam = Camera.main;
        float top = cam != null ? cam.transform.position.y + cam.orthographicSize + 1f : p.y + 6f;
        var sky = new Vector2(p.x + Random.Range(-0.8f, 0.8f), top);
        Lightning(sky, p, c, 0.35f, 4, 0.5f);
        Lightning(sky + new Vector2(0.3f, 0f), p, Color.white, 0.2f, 1, 0.3f);
        Flash(new Color(0.85f, 0.9f, 1f, 0.35f), 0.15f);
        Release(p, c, ImpactStyle.Electric, size);
        Scorch(p, size);
    }

    /// <summary>Dark burn mark left on the floor.</summary>
    public static void Scorch(Vector2 p, float size)
    {
        if (!Enabled) return;
        var sr = Pooled(Sprites.Glow, p + new Vector2(0f, -0.3f), new Color(0f, 0f, 0f, 0.55f), 1f);
        sr.transform.localScale = new Vector3(1.4f, 0.55f, 1f) * size;
        AnimatePooled(sr, 2.5f, k => sr.color = new Color(0f, 0f, 0f, 0.55f * (1f - k)));
    }

    /// <summary>Wavy energy thread from A to B (chaos magic), drawn as a moving dotted line.</summary>
    public static void Tendril(Vector2 a, Vector2 b, Color c, float dur, int dots = 9)
    {
        if (!Enabled) return;
        dots = Mathf.Max(4, Mathf.RoundToInt(dots * Quality));
        float phase = Random.Range(0f, 6f), amp = Random.Range(0.15f, 0.35f);
        var n = new Vector2(-(b - a).y, (b - a).x).normalized;
        for (int i = 0; i < dots; i++)
        {
            float f = (float)i / (dots - 1);
            var sr = Pooled(Sprites.Glow, a, A(c, 0f), 0.28f * (1f - f * 0.5f));
            AnimatePooled(sr, dur, k =>
            {
                float reach = Mathf.Clamp01(k * 2.2f);
                if (f > reach) { sr.color = A(c, 0f); return; }
                var q = Vector2.Lerp(a, b, f) + n * Mathf.Sin(f * 7f + phase + k * 18f) * amp * (1f - f * 0.3f);
                sr.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.6f);
                sr.color = A(Color.Lerp(c, Color.white, 0.2f * (1f - f)), 0.9f * (1f - k));
            });
        }
    }

    /// <summary>Tendrils that keep wrapping a moving target for `dur` seconds.</summary>
    public static IEnumerator Bind(Transform from, Transform target, Color c, float dur, int threads = 3)
    {
        float tick = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (from == null || target == null) yield break;
            tick -= Time.deltaTime;
            if (tick <= 0f)
            {
                tick = 0.18f;
                for (int i = 0; i < threads; i++)
                    Tendril((Vector2)from.position + new Vector2(0.3f, 0.1f), (Vector2)target.position + Random.insideUnitCircle * 0.3f, c, 0.3f, 7);
            }
            yield return null;
        }
    }

    // ── aftermath + environment ──────────────────────────────────────────────

    /// <summary>What lingers after the hit: embers, smoke, fading runes, settled debris, static, frost.</summary>
    public static void Aftermath(Vector2 p, Color c, Residue r, float size = 1f)
    {
        if (!Enabled) return;
        switch (r)
        {
            case Residue.Embers: Rising(p, Color.Lerp(c, new Color(1f, 0.6f, 0.2f), 0.4f), Mathf.RoundToInt(10 * size), 1.2f * size, 1.6f); break;
            case Residue.Smoke: Smoke(p, Color.Lerp(c, Color.gray, 0.6f), Mathf.RoundToInt(6 * size), 1.6f); break;
            case Residue.Runes:
                for (int i = 0; i < 4; i++)
                {
                    var q = p + Random.insideUnitCircle * size;
                    var sr = Pooled(Sprites.Runes, q, A(c, 0.6f), 0.4f);
                    AnimatePooled(sr, 1.4f, k => { sr.transform.Rotate(0, 0, 40f * Time.deltaTime); sr.color = A(c, 0.6f * (1f - k)); sr.transform.position += new Vector3(0, 0.25f * Time.deltaTime, 0); });
                }
                break;
            case Residue.Debris: Debris(p, Color.Lerp(c, Color.gray, 0.6f), Mathf.RoundToInt(5 * size), 0.6f); Smoke(p, Color.gray, 3, 1.2f); break;
            case Residue.Static:
                for (int i = 0; i < 4; i++)
                {
                    var q = p + Random.insideUnitCircle * size;
                    Lightning(q, q + Random.insideUnitCircle * 0.6f, c, 0.12f, 0, 0.2f);
                }
                Sfx.Play(SfxKind.Crackle, 1.4f, p, 0.25f);
                break;
            case Residue.Frost:
                Sparkles(p, Color.Lerp(c, Color.white, 0.6f), Mathf.RoundToInt(8 * size), 1f * size, 1.4f);
                break;
        }
    }

    /// <summary>Particles drifting upward and swaying (embers / wisps).</summary>
    public static void Rising(Vector2 p, Color c, int count, float spread, float dur)
    {
        if (!ModConfig.Data.VfxParticles) return;
        count = Mathf.Max(2, Mathf.RoundToInt(count * Quality));
        for (int i = 0; i < count; i++)
        {
            var start = p + new Vector2(Random.Range(-spread, spread) * 0.5f, Random.Range(-0.3f, 0.2f));
            float phase = Random.Range(0f, 6f), life = dur * Random.Range(0.7f, 1.2f), size = Random.Range(0.1f, 0.22f);
            var sr = Pooled(Sprites.Glow, start, A(c, 0.9f), size);
            AnimatePooled(sr, life, k =>
            {
                var q = start + new Vector2(Mathf.Sin(k * 8f + phase) * 0.15f, k * 1.3f);
                sr.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.6f);
                sr.color = A(c, 0.9f * (1f - k));
            });
        }
    }

    /// <summary>Debris rising off the floor and hovering (things floating during a big power), then dropping.</summary>
    public static void Levitate(Vector2 center, Color tint, float radius, float dur, int count = 10)
    {
        if (!Enabled || !ModConfig.Data.VfxParticles) return;
        dur *= Tempo;
        count = Mathf.Max(3, Mathf.RoundToInt(count * Quality));
        for (int i = 0; i < count; i++)
        {
            var start = center + Random.insideUnitCircle * radius;
            float h = Random.Range(0.5f, 1.4f), phase = Random.Range(0f, 6f), spin = Random.Range(-60f, 60f);
            float size = Random.Range(0.14f, 0.34f);
            var col = Color.Lerp(new Color(0.45f, 0.45f, 0.5f), tint, 0.35f);
            var sr = Pooled(i % 3 == 0 ? Sprites.Box : Sprites.Shard, start, col, size);
            AnimatePooled(sr, dur, k =>
            {
                float lift = k < 0.15f ? EaseOut(k / 0.15f) : k > 0.85f ? 1f - (k - 0.85f) / 0.15f * ((k - 0.85f) / 0.15f) : 1f;
                var q = start + new Vector2(0f, h * lift + Mathf.Sin(k * 10f + phase) * 0.06f);
                sr.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.5f);
                sr.transform.Rotate(0, 0, spin * Time.deltaTime);
                sr.color = A(col, k > 0.92f ? (1f - k) / 0.08f : 0.95f);
            });
        }
    }

    /// <summary>Darkens the arena for big ultimates (world overlay under the HUD).</summary>
    public static void Darken(float alpha, float dur, Color? tint = null)
    {
        var c = tint ?? new Color(0.02f, 0.02f, 0.06f);
        ScreenTint(new Color(c.r, c.g, c.b, alpha), dur * Tempo);
    }

    /// <summary>Quick full-screen flash.</summary>
    public static void Flash(Color c, float dur = 0.15f)
    {
        if (!ModConfig.Data.CameraEffects) return;
        ScreenTint(c, dur);
    }

    /// <summary>Afterimages left behind a moving transform.</summary>
    public static void Trail(Transform t, Color c, float dur, float every = 0.05f)
        => Coroutines.Start(CoTrail(t, c, dur, every));

    private static IEnumerator CoTrail(Transform tr, Color c, float dur, float every)
    {
        float next = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (tr == null) yield break;
            next -= Time.deltaTime;
            if (next <= 0f && Enabled)
            {
                next = every / Quality;
                var sr = Pooled(Sprites.Glow, tr.position, A(c, 0.55f), 0.7f);
                AnimatePooled(sr, 0.35f, k => { sr.transform.localScale = Vector3.one * 0.7f * (1f - k * 0.6f); sr.color = A(c, 0.55f * (1f - k)); });
            }
            yield return null;
        }
    }

    // ── internal pieces used by the styles ───────────────────────────────────

    private static void GhostRing(Vector2 p, Color c, float size)
    {
        var sr = Pooled(Sprites.Ring, p, A(c, 0.8f), 0.3f);
        AnimatePooled(sr, 0.45f, k =>
        {
            sr.transform.localScale = Vector3.one * Mathf.Lerp(0.3f, size, k) + new Vector3(Random.Range(-0.1f, 0.1f), 0f, 0f);
            sr.color = A(c, 0.8f * (1f - k) * (Random.value > 0.2f ? 1f : 0.2f));
        });
    }

    private static void Bars(Vector2 p, Color c, float size)
    {
        int n = Mathf.Max(3, Mathf.RoundToInt(6 * Quality));
        for (int i = 0; i < n; i++)
        {
            float y = (i - n / 2f) * 0.2f * size;
            var b = Pooled(Sprites.Square, p, A(c, 0.7f), 1f);
            AnimatePooled(b, 0.45f, k =>
            {
                var q = p + new Vector2(Random.Range(-0.5f, 0.5f) * size, y);
                b.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.6f);
                b.transform.localScale = new Vector3(Random.Range(0.4f, 2f) * size, 0.07f, 1f);
                b.color = A(i % 2 == 0 ? c : Color.white, 0.7f * (1f - k));
            });
        }
    }

    private static IEnumerator CoImplodeThenBlow(Vector2 p, Color c, float size)
    {
        var r = Pooled(Sprites.Ring, p, A(c, 0f), 2.4f * size);
        for (float t = 0; t < 0.22f; t += Time.deltaTime)
        {
            if (r == null) yield break;
            float k = t / 0.22f;
            r.transform.localScale = Vector3.one * Mathf.Lerp(2.4f, 0.1f, k * k) * size;
            r.transform.Rotate(0, 0, 600f * Time.deltaTime);
            r.color = A(c, k);
            yield return null;
        }
        Recycle(r);
        ShockRing(p, c, 3.2f * size, 0.8f);
        ShockRing(p, Color.white, 1.6f * size, 0.35f);
        Sparkles(p, Color.Lerp(c, Color.white, 0.5f), Mathf.RoundToInt(12 * size), 2f * size, 0.9f);
        Shake(0.3f, 0.3f * size);
    }

    private static void Bounce(Vector2 p, Color c, int count)
    {
        for (int i = 0; i < count; i++)
        {
            var v = new Vector2(Random.Range(-1.5f, 1.5f), Random.Range(2f, 3f));
            var sr = Pooled(Sprites.Star, p, c, 0.3f);
            AnimatePooled(sr, 0.9f, k =>
            {
                float t = k * 0.9f;
                float y = Mathf.Abs(v.y * t - 6f * t * t);
                var q = p + new Vector2(v.x * t, y);
                sr.transform.position = new Vector3(q.x, q.y, q.y / 1000f - 0.6f);
                sr.transform.Rotate(0, 0, 400f * Time.deltaTime);
                sr.color = A(c, 1f - k * k);
            });
        }
    }

    /// <summary>Comic-book word popping up at a point ("BONK!").</summary>
    public static void Comic(Vector2 p, string word, Color c, float size = 1f)
    {
        var anchor = new GameObject("MarvelComic") { layer = WorldLayer };
        anchor.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.7f);
        Track(anchor);
        WorldText(anchor.transform, word, c, 0.9f, 3.2f * Mathf.Clamp(size, 0.8f, 1.6f));
        anchor.transform.rotation = Quaternion.Euler(0, 0, Random.Range(-14f, 14f));
        Object.Destroy(anchor, 1.1f);
    }
}

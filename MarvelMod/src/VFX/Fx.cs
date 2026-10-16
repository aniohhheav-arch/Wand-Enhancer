using System;
using System.Collections;
using MarvelMod.Assets;
using MarvelMod.Config;
using Reactor.Utilities;
using TMPro;
using UnityEngine;
using Object = UnityEngine.Object;
using Random = UnityEngine.Random;

namespace MarvelMod.VFX;

/// <summary>
/// Sprite-based visual effects. Everything is drawn with generated sprites on the
/// player layer, so it renders with the game's own camera and needs no asset bundles.
/// </summary>
public static class Fx
{
    public static readonly Color LokiGreen  = new(0.20f, 0.85f, 0.35f, 1f);
    public static readonly Color LokiLight  = new(0.60f, 1.00f, 0.65f, 1f);
    public static readonly Color LokiDark   = new(0.05f, 0.40f, 0.15f, 1f);
    public static readonly Color LokiGold   = new(1.00f, 0.80f, 0.20f, 1f);
    public static readonly Color ChaosPurple = new(0.65f, 0.35f, 1.00f, 1f);
    public static readonly Color AlarmRed   = new(1.00f, 0.15f, 0.15f, 1f);

    private static float Intensity => Mathf.Clamp(ModConfig.Data.VfxIntensity / 100f, 0.2f, 2f);
    private static bool Enabled => ModConfig.Data.VfxEnabled;

    private static int WorldLayer =>
        PlayerControl.LocalPlayer != null ? PlayerControl.LocalPlayer.gameObject.layer : 0;

    // ── primitives ───────────────────────────────────────────────────────────

    /// <summary>Creates a world-space sprite just in front of anything standing at that height.</summary>
    public static SpriteRenderer MakeSprite(Sprite sprite, Vector2 pos, Color color, float scale, string name = "MarvelFx")
    {
        var go = new GameObject(name) { layer = WorldLayer };
        go.transform.position = new Vector3(pos.x, pos.y, pos.y / 1000f - 0.5f);
        go.transform.localScale = Vector3.one * scale;
        var sr = go.AddComponent<SpriteRenderer>();
        sr.sprite = sprite;
        sr.color = color;
        return sr;
    }

    /// <summary>Runs step(0..1) every frame for dur seconds, then destroys the sprite.</summary>
    public static void Animate(SpriteRenderer sr, float dur, Action<float> step)
        => Coroutines.Start(CoAnimate(sr, dur, step));

    private static IEnumerator CoAnimate(SpriteRenderer sr, float dur, Action<float> step)
    {
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (sr == null) yield break;
            step(t / dur);
            yield return null;
        }
        if (sr != null) Object.Destroy(sr.gameObject);
    }

    private static Color A(Color c, float a) => new(c.r, c.g, c.b, a);
    private static float EaseOut(float k) => 1f - (1f - k) * (1f - k);

    // ── effects ──────────────────────────────────────────────────────────────

    /// <summary>Expanding glow + shockwave ring.</summary>
    public static void Burst(Vector2 pos, Color color, float size = 1.6f, float dur = 0.45f)
    {
        if (!Enabled) return;
        size *= Intensity;
        var glow = MakeSprite(Sprites.Glow, pos, A(color, 0.9f), 0.3f);
        Animate(glow, dur, k =>
        {
            glow.transform.localScale = Vector3.one * Mathf.Lerp(0.3f, size, EaseOut(k));
            glow.color = A(color, 0.9f * (1f - k));
        });
        var ring = MakeSprite(Sprites.Ring, pos, A(Color.Lerp(color, Color.white, 0.4f), 1f), 0.2f);
        Animate(ring, dur * 1.3f, k =>
        {
            ring.transform.localScale = Vector3.one * Mathf.Lerp(0.2f, size * 1.4f, EaseOut(k));
            ring.color = A(ring.color, 1f - k);
        });
    }

    /// <summary>Spinning sparkle stars flying outward.</summary>
    public static void Sparkles(Vector2 pos, Color color, int count = 10, float radius = 1.3f, float dur = 0.7f)
    {
        if (!Enabled || !ModConfig.Data.VfxParticles) return;
        count = Mathf.Max(1, Mathf.RoundToInt(count * Intensity));
        for (int i = 0; i < count; i++)
        {
            float ang = Random.Range(0f, Mathf.PI * 2f);
            var dir = new Vector2(Mathf.Cos(ang), Mathf.Sin(ang));
            float dist = Random.Range(0.4f, 1f) * radius;
            float size = Random.Range(0.15f, 0.32f);
            float spin = Random.Range(-360f, 360f);
            float life = dur * Random.Range(0.7f, 1.2f);
            var c = Color.Lerp(color, Color.white, Random.Range(0f, 0.5f));
            var star = MakeSprite(Sprites.Star, pos, c, size);
            Animate(star, life, k =>
            {
                var p = pos + dir * (dist * EaseOut(k));
                star.transform.position = new Vector3(p.x, p.y, star.transform.position.z);
                star.transform.localScale = Vector3.one * size * (1f - k * 0.7f);
                star.transform.Rotate(0, 0, spin * Time.deltaTime);
                star.color = A(c, 1f - k * k);
            });
        }
    }

    /// <summary>Soft rising puffs.</summary>
    public static void Smoke(Vector2 pos, Color color, int count = 8, float dur = 0.9f)
    {
        if (!Enabled || !ModConfig.Data.VfxParticles) return;
        count = Mathf.Max(1, Mathf.RoundToInt(count * Intensity));
        for (int i = 0; i < count; i++)
        {
            var start = pos + Random.insideUnitCircle * 0.35f;
            var drift = new Vector2(Random.Range(-0.4f, 0.4f), Random.Range(0.3f, 0.9f));
            float size = Random.Range(0.6f, 1.1f);
            float life = dur * Random.Range(0.7f, 1.2f);
            var c = Color.Lerp(color, Color.black, Random.Range(0.1f, 0.4f));
            var puff = MakeSprite(Sprites.Glow, start, A(c, 0.8f), size * 0.4f);
            Animate(puff, life, k =>
            {
                var p = start + drift * k;
                puff.transform.position = new Vector3(p.x, p.y, puff.transform.position.z);
                puff.transform.localScale = Vector3.one * size * Mathf.Lerp(0.4f, 1.3f, EaseOut(k));
                puff.color = A(c, 0.8f * (1f - k));
            });
        }
    }

    /// <summary>A glowing orb travelling from A to B with a sparkle trail.</summary>
    public static void Projectile(Vector2 from, Vector2 to, Color color, float speed, Action? onArrive)
        => Coroutines.Start(CoProjectile(from, to, color, speed, onArrive));

    private static IEnumerator CoProjectile(Vector2 from, Vector2 to, Color color, float speed, Action? onArrive)
    {
        float dur = Mathf.Max(0.05f, Vector2.Distance(from, to) / speed);
        var orb = MakeSprite(Sprites.Glow, from, A(color, 1f), 0.8f);
        var core = MakeSprite(Sprites.Star, from, Color.white, 0.45f);
        float trail = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (orb == null || core == null) yield break;
            var p = Vector2.Lerp(from, to, t / dur);
            var z = p.y / 1000f - 0.6f;
            orb.transform.position = new Vector3(p.x, p.y, z);
            core.transform.position = new Vector3(p.x, p.y, z - 0.01f);
            core.transform.Rotate(0, 0, 720f * Time.deltaTime);
            trail += Time.deltaTime;
            if (trail > 0.03f && Enabled)
            {
                trail = 0f;
                var bit = MakeSprite(Sprites.Glow, p, A(color, 0.6f), 0.45f);
                Animate(bit, 0.3f, k =>
                {
                    bit.transform.localScale = Vector3.one * 0.45f * (1f - k);
                    bit.color = A(color, 0.6f * (1f - k));
                });
            }
            yield return null;
        }
        if (orb != null) Object.Destroy(orb.gameObject);
        if (core != null) Object.Destroy(core.gameObject);
        Burst(to, color, 1.2f, 0.35f);
        Sparkles(to, color, 6, 0.8f, 0.4f);
        onArrive?.Invoke();
    }

    /// <summary>
    /// A ring/glow attached to a transform (status auras). Returns the object so the
    /// caller can destroy it early; otherwise it removes itself after dur seconds.
    /// </summary>
    public static GameObject Aura(Transform follow, Sprite sprite, Color color, float scale, float dur,
                                  float spin = 0f, float yOffset = 0f, bool pulse = true)
    {
        var sr = MakeSprite(sprite, follow.position, color, scale, "MarvelAura");
        sr.transform.SetParent(follow, true);
        sr.transform.localPosition = new Vector3(0, yOffset, -0.05f);
        float baseA = color.a;
        Animate(sr, dur, k =>
        {
            if (spin != 0f) sr.transform.Rotate(0, 0, spin * Time.deltaTime);
            float a = pulse ? baseA * (0.65f + 0.35f * Mathf.Sin(Time.time * 6f)) : baseA;
            if (k > 0.9f) a *= (1f - k) * 10f;   // fade the last 10 %
            sr.color = A(color, a);
        });
        return sr.gameObject;
    }

    /// <summary>
    /// Camera-attached sprite at a depth relative to the HUD buttons: behind them (+) or in front (-).
    /// Keeps tints from washing out the buttons.
    /// </summary>
    private static Transform? ScreenLayer(float depthFromButtons, out int layer)
    {
        layer = 0;
        var hud = HudManager.Instance;
        if (hud == null) return null;
        var cam = UiCamera;
        if (cam == null) return null;
        layer = hud.gameObject.layer;
        float buttonsZ = hud.UseButton != null ? hud.UseButton.transform.position.z : cam.transform.position.z + 5f;
        float z = buttonsZ + depthFromButtons;
        if (cam == Camera.main && PlayerControl.LocalPlayer != null)   // shared camera: never go behind the world
            z = Mathf.Min(z, PlayerControl.LocalPlayer.transform.position.z - 1f);
        var go = new GameObject("MarvelScreenFx") { layer = layer };
        go.transform.SetParent(cam.transform, false);
        go.transform.position = new Vector3(cam.transform.position.x, cam.transform.position.y, z);
        return go.transform;
    }

    /// <summary>The camera that draws the HUD (separate from the world camera in current builds).</summary>
    public static Camera? UiCamera =>
        HudManager.Instance != null && HudManager.Instance.UICamera != null ? HudManager.Instance.UICamera : Camera.main;

    /// <summary>A screen-space sprite (letterbox bars, overlays). depth &lt; 0 = in front of the HUD buttons.</summary>
    public static SpriteRenderer? ScreenSprite(Sprite sprite, Color color, float depthFromButtons, string name = "MarvelScreenSprite")
    {
        var root = ScreenLayer(depthFromButtons, out _);
        if (root == null) return null;
        root.name = name;
        var sr = root.gameObject.AddComponent<SpriteRenderer>();
        sr.sprite = sprite;
        sr.color = color;
        return sr;
    }

    /// <summary>
    /// A sprite glued to the world camera (between the camera and the world), re-fitted every frame so it
    /// follows cinematic zooms. depthFraction: 0 = at the camera, 1 = at the world (smaller = drawn on top).
    /// </summary>
    public static SpriteRenderer? CameraOverlay(string name, float depthFraction, Color color)
    {
        var cam = Camera.main;
        if (cam == null) return null;
        var lp = PlayerControl.LocalPlayer;
        float camZ = cam.transform.position.z;
        float worldZ = lp != null ? lp.transform.position.z : camZ + 10f;
        float dz = Mathf.Max(cam.nearClipPlane + 0.2f, (worldZ - camZ) * depthFraction);
        var go = new GameObject(name) { layer = lp != null ? lp.gameObject.layer : 0 };
        go.transform.SetParent(cam.transform, false);
        go.transform.localPosition = new Vector3(0f, 0f, dz);
        var sr = go.AddComponent<SpriteRenderer>();
        sr.sprite = Sprites.Square;
        sr.color = color;
        sr.sortingOrder = 1000;
        return sr;
    }

    /// <summary>Size an overlay relative to the current camera view (1,1 = whole screen).</summary>
    public static void FitToCamera(SpriteRenderer sr, float widthFrac, float heightFrac, float yFrac = 0f)
    {
        var cam = Camera.main;
        if (cam == null || sr == null) return;
        float h = cam.orthographicSize * 2f;
        sr.transform.localScale = new Vector3(h * cam.aspect * widthFrac, h * heightFrac, 1f);
        var lp = sr.transform.localPosition;
        sr.transform.localPosition = new Vector3(0f, yFrac * h * 0.5f, lp.z);
    }

    /// <summary>Full-screen colour tint over the world, under the HUD (fades in, holds, fades out).</summary>
    public static void ScreenTint(Color color, float dur, int pulses = 0)
    {
        if (!Enabled || !ModConfig.Data.VfxDistortion) return;
        var sr = CameraOverlay("MarvelTint", 0.6f, A(color, 0f));
        if (sr == null) return;
        float maxA = color.a;
        Animate(sr, dur, k =>
        {
            FitToCamera(sr, 1.3f, 1.3f);
            float env = Mathf.Min(1f, k / 0.1f) * Mathf.Min(1f, (1f - k) / 0.25f);
            if (pulses > 0) env *= Mathf.Abs(Mathf.Sin(k * Mathf.PI * pulses));
            sr.color = A(color, maxA * env);
        });
    }

    /// <summary>Big centred text on this player's screen, in front of everything.</summary>
    public static void Toast(string text, Color color, float dur = 2.5f, float size = 4f, float yOffset = 1.4f)
    {
        var hud = HudManager.Instance;
        if (hud == null || hud.KillButton == null) return;
        var root = ScreenLayer(-1f, out int layer);
        if (root == null) return;

        var tmp = UnityHelpers.CloneStripped(hud.KillButton.buttonLabelText, root);
        if (tmp == null) return;
        tmp.gameObject.layer = layer;
        tmp.name = "MarvelToast";
        tmp.transform.localPosition = new Vector3(0f, yOffset, 0f);
        tmp.transform.localScale = Vector3.one;
        tmp.enableAutoSizing = false;
        tmp.enableWordWrapping = false;
        tmp.alignment = TextAlignmentOptions.Center;
        tmp.fontSize = size;
        tmp.rectTransform.sizeDelta = new Vector2(14f, 2f);
        tmp.text = text;
        tmp.color = color;
        Coroutines.Start(CoToast(tmp, root.gameObject, color, dur));
    }

    private static IEnumerator CoToast(TMP_Text tmp, GameObject root, Color color, float dur)
    {
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (tmp == null) yield break;
            float k = t / dur;
            float pop = k < 0.08f ? Mathf.Lerp(1.4f, 1f, k / 0.08f) : 1f;
            tmp.transform.localScale = Vector3.one * pop;
            tmp.color = A(color, k > 0.75f ? (1f - k) / 0.25f : 1f);
            yield return null;
        }
        if (root != null) Object.Destroy(root);
    }

    // ── shapes ───────────────────────────────────────────────────────────────

    /// <summary>A straight energy beam from A to B (glow + white core) that fades.</summary>
    public static void Beam(Vector2 from, Vector2 to, Color color, float width = 0.5f, float dur = 0.5f)
    {
        if (!Enabled) return;
        var mid = (from + to) * 0.5f;
        var dir = to - from;
        float len = dir.magnitude;
        float ang = Mathf.Atan2(dir.y, dir.x) * Mathf.Rad2Deg;
        foreach (var (c, w) in new[] { (A(color, 0.85f), width), (A(Color.white, 0.95f), width * 0.35f) })
        {
            var sr = MakeSprite(Sprites.Bar, mid, c, 1f, "MarvelBeam");
            sr.transform.rotation = Quaternion.Euler(0, 0, ang);
            sr.transform.localScale = new Vector3(len / 1f, w, 1f);
            float baseA = c.a;
            Animate(sr, dur, k =>
            {
                sr.transform.localScale = new Vector3(len, w * (1f + 0.15f * Mathf.Sin(k * 40f)) * (1f - k * 0.6f), 1f);
                sr.color = A(c, baseA * (1f - k * k));
            });
        }
    }

    /// <summary>Jagged crack lines spreading from a point, then fading.</summary>
    public static void Cracks(Vector2 center, Color color, int count = 7, float radius = 1.6f, float dur = 1.2f, int seed = 0)
    {
        if (!Enabled) return;
        var rng = new System.Random(seed);
        for (int i = 0; i < count; i++)
        {
            float ang = (float)(i * Mathf.PI * 2 / count + rng.NextDouble() * 0.5);
            var p = center;
            int segs = 3;
            for (int sgi = 0; sgi < segs; sgi++)
            {
                float segLen = radius / segs * (0.7f + (float)rng.NextDouble() * 0.6f);
                ang += (float)(rng.NextDouble() - 0.5) * 0.9f;
                var q = p + new Vector2(Mathf.Cos(ang), Mathf.Sin(ang)) * segLen;
                var sr = MakeSprite(Sprites.Bar, (p + q) * 0.5f, A(color, 0f), 1f, "MarvelCrack");
                sr.transform.rotation = Quaternion.Euler(0, 0, ang * Mathf.Rad2Deg);
                sr.transform.localScale = new Vector3(segLen, 0.08f, 1f);
                float delay = sgi * 0.08f;
                Animate(sr, dur + delay, k =>
                {
                    float t = k * (dur + delay);
                    float a = t < delay ? 0f : Mathf.Min(1f, (t - delay) / 0.06f) * (1f - Mathf.Max(0f, k - 0.7f) / 0.3f);
                    sr.color = A(color, a);
                });
                p = q;
            }
        }
    }

    /// <summary>A ring that sweeps outward; onReach is called once for each living player it passes.</summary>
    public static void Wave(Vector2 center, Color color, float maxRadius, float dur, Action<PlayerControl>? onReach = null)
        => Coroutines.Start(CoWave(center, color, maxRadius, dur, onReach));

    private static IEnumerator CoWave(Vector2 center, Color color, float maxRadius, float dur, Action<PlayerControl>? onReach)
    {
        var ring = MakeSprite(Sprites.Ring, center, A(color, 0.9f), 0.1f, "MarvelWave");
        var hit = new System.Collections.Generic.HashSet<byte>();
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (ring == null) yield break;
            float r = maxRadius * (t / dur);
            ring.transform.localScale = Vector3.one * r * 2f / 0.82f;
            ring.color = A(color, 0.9f * (1f - t / dur * 0.6f));
            if (onReach != null)
                foreach (var pc in Characters.Players.All())
                {
                    if (!Characters.Players.IsAlive(pc) || hit.Contains(pc.PlayerId)) continue;
                    if (Vector2.Distance(center, pc.transform.position) <= r)
                    {
                        hit.Add(pc.PlayerId);
                        try { onReach(pc); } catch (Exception) { /* keep the wave going */ }
                    }
                }
            yield return null;
        }
        if (ring != null) Object.Destroy(ring.gameObject);
    }

    /// <summary>Glowing marker drawn above walls/shadows on the local screen (Soul Sight).</summary>
    public static void ScreenMarker(Transform follow, Color color, float dur)
        => Coroutines.Start(CoMarker(follow, color, dur));

    private static IEnumerator CoMarker(Transform follow, Color color, float dur)
    {
        var sr = ScreenSprite(Sprites.Glow, A(color, 0.85f), 0.3f, "MarvelSoulMarker");
        var ring = ScreenSprite(Sprites.Ring, A(color, 1f), 0.29f, "MarvelSoulRing");
        if (sr == null || ring == null) yield break;
        sr.transform.SetParent(null, true);
        ring.transform.SetParent(null, true);
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (follow == null || sr == null || ring == null) break;
            var p = follow.position;
            sr.transform.position = new Vector3(p.x, p.y, sr.transform.position.z);
            ring.transform.position = new Vector3(p.x, p.y, ring.transform.position.z);
            float pulse = 0.9f + 0.2f * Mathf.Sin(Time.time * 5f);
            sr.transform.localScale = Vector3.one * 1.1f * pulse;
            ring.transform.localScale = Vector3.one * 0.9f * pulse;
            yield return null;
        }
        if (sr != null) Object.Destroy(sr.gameObject);
        if (ring != null) Object.Destroy(ring.gameObject);
    }

    /// <summary>Cinematic black bars on the world camera; they slide in/out and follow zoom.</summary>
    public static GameObject? Letterbox(float hold)
    {
        var cam = Camera.main;
        if (cam == null) return null;
        var root = new GameObject("MarvelLetterbox");
        root.transform.SetParent(cam.transform, false);
        for (int i = 0; i < 2; i++)
        {
            var bar = CameraOverlay("MarvelBar", 0.35f, Color.black);
            if (bar == null) continue;
            bar.sortingOrder = 1001;
            bar.transform.SetParent(root.transform, true);
            float sign = i == 0 ? 1f : -1f;
            Animate(bar, hold, k =>
            {
                float t = k * hold;
                float slide = Mathf.Min(1f, t / 0.4f) * Mathf.Min(1f, (hold - t) / 0.4f);   // 0 hidden .. 1 in place
                const float barFrac = 0.13f;                                                 // of screen height
                FitToCamera(bar, 1.4f, barFrac, sign * (1f - barFrac + barFrac * (1f - slide) * 2f));
            });
        }
        Object.Destroy(root, hold + 0.2f);
        return root;
    }

    /// <summary>A body breaking into glowing fragments that drift outward and fade.</summary>
    public static void Fragments(Vector2 pos, Color color, int count = 40, float spread = 1.6f, float dur = 1.4f, bool reverse = false)
    {
        if (!Enabled) return;
        for (int i = 0; i < count; i++)
        {
            var start = pos + new Vector2(Random.Range(-0.3f, 0.3f), Random.Range(-0.4f, 0.5f));
            var end = start + Random.insideUnitCircle * spread + new Vector2(0f, Random.Range(0.2f, 0.9f));
            if (reverse) (start, end) = (end, start);
            float size = Random.Range(0.08f, 0.2f);
            float spin = Random.Range(-540f, 540f);
            var c = Color.Lerp(color, Color.white, Random.Range(0f, 0.4f));
            var sr = MakeSprite(Sprites.Shard, start, c, size, "MarvelFragment");
            float life = dur * Random.Range(0.7f, 1f);
            Animate(sr, life, k =>
            {
                var p = Vector2.Lerp(start, end, reverse ? k * k : EaseOut(k));
                sr.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.55f);
                sr.transform.Rotate(0, 0, spin * Time.deltaTime);
                sr.color = A(c, reverse ? Mathf.Min(1f, k * 3f) : 1f - k * k);
            });
        }
    }

    /// <summary>Short text floating above a world position (victim reactions: "?", "!").</summary>
    public static void WorldText(Transform follow, string text, Color color, float dur = 1.2f, float size = 3f)
    {
        var hud = HudManager.Instance;
        if (hud == null || hud.KillButton == null || follow == null) return;
        var tmp = UnityHelpers.CloneStripped(hud.KillButton.buttonLabelText, follow);
        if (tmp == null) return;
        tmp.gameObject.layer = follow.gameObject.layer;
        tmp.name = "MarvelWorldText";
        tmp.transform.localPosition = new Vector3(0f, 0.85f, -0.5f);
        tmp.enableAutoSizing = false;
        tmp.enableWordWrapping = false;
        tmp.alignment = TextAlignmentOptions.Center;
        tmp.fontSize = size;
        tmp.rectTransform.sizeDelta = new Vector2(6f, 1f);
        tmp.text = text;
        tmp.color = color;
        Coroutines.Start(CoToast(tmp, tmp.gameObject, color, dur));
    }

    /// <summary>Quick "punch" scale on a HUD button when it's used.</summary>
    private static readonly System.Collections.Generic.HashSet<IntPtr> Punching = new();

    public static void Punch(Transform t, float amount = 0.18f, float dur = 0.18f)
    {
        if (Punching.Add(t.Pointer)) Coroutines.Start(CoPunch(t, amount, dur));
    }

    private static IEnumerator CoPunch(Transform t, float amount, float dur)
    {
        var ptr = t.Pointer;
        var baseScale = t.localScale;
        for (float e = 0; e < dur; e += Time.deltaTime)
        {
            if (t == null) yield break;
            t.localScale = baseScale * (1f + amount * Mathf.Sin(e / dur * Mathf.PI));
            yield return null;
        }
        if (t != null) t.localScale = baseScale;
        Punching.Remove(ptr);
    }

    /// <summary>Camera shake using the game's own follower camera.</summary>
    public static void Shake(float dur, float severity)
    {
        if (!Enabled || !ModConfig.Data.VfxScreenShake) return;
        var cam = HudManager.Instance != null ? HudManager.Instance.PlayerCam : null;
        if (cam != null) cam.ShakeScreen(dur, severity * Intensity);
    }
}

public static class UnityHelpers
{
    /// <summary>
    /// Instantiates a copy of a component's GameObject without running any game scripts on it
    /// (only TextMeshPro and renderers are kept), so e.g. translators can't overwrite our text.
    /// </summary>
    public static T? CloneStripped<T>(T original, Transform parent) where T : Component
    {
        var holder = new GameObject("MarvelCloneHolder");
        holder.SetActive(false);
        var go = Object.Instantiate(original.gameObject, holder.transform);
        StripScripts(go);
        go.transform.SetParent(parent, false);
        Object.Destroy(holder);
        go.SetActive(true);
        return go.GetComponent<T>();
    }

    /// <summary>Removes every MonoBehaviour except TextMeshPro ones.</summary>
    public static void StripScripts(GameObject go)
    {
        foreach (var mb in go.GetComponentsInChildren<MonoBehaviour>(true))
        {
            if (mb == null) continue;
            var ns = mb.GetIl2CppType().Namespace;
            if (ns == "TMPro") continue;
            try { Object.DestroyImmediate(mb); } catch { /* component other scripts depend on */ }
        }
    }
}

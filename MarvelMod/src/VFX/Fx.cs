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
        var cam = Camera.main;
        var hud = HudManager.Instance;
        if (cam == null || hud == null) return null;
        layer = hud.gameObject.layer;
        float buttonsZ = hud.UseButton != null ? hud.UseButton.transform.position.z : cam.transform.position.z + 1f;
        float z = buttonsZ + depthFromButtons;
        if (PlayerControl.LocalPlayer != null)   // never go behind the world
            z = Mathf.Min(z, PlayerControl.LocalPlayer.transform.position.z - 1f);
        var go = new GameObject("MarvelScreenFx") { layer = layer };
        go.transform.SetParent(cam.transform, false);
        go.transform.position = new Vector3(cam.transform.position.x, cam.transform.position.y, z);
        return go.transform;
    }

    /// <summary>Full-screen colour tint behind the HUD (fades in, holds, fades out). pulses > 0 makes it flash.</summary>
    public static void ScreenTint(Color color, float dur, int pulses = 0)
    {
        if (!Enabled || !ModConfig.Data.VfxDistortion) return;
        var root = ScreenLayer(0.5f, out int layer);
        var cam = Camera.main;
        if (root == null || cam == null) return;
        var sr = root.gameObject.AddComponent<SpriteRenderer>();
        sr.sprite = Sprites.Square;
        float h = cam.orthographicSize * 2.4f;
        root.localScale = new Vector3(h * cam.aspect, h, 1f);
        float maxA = color.a;
        sr.color = A(color, 0f);
        Animate(sr, dur, k =>
        {
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

    // ── gore ─────────────────────────────────────────────────────────────────

    private static readonly System.Collections.Generic.List<GameObject> Stains = new();
    public static readonly Color Blood     = new(0.62f, 0.02f, 0.04f, 1f);
    public static readonly Color BloodDark = new(0.35f, 0.00f, 0.02f, 1f);

    /// <summary>Blood spray: droplets fly in an arc and leave stains on the floor (cleared at meetings).</summary>
    public static void BloodSplatter(Vector2 pos, float power = 1f, Vector2? direction = null)
    {
        if (!Enabled) return;
        int count = Mathf.RoundToInt(18 * power * Intensity);
        for (int i = 0; i < count; i++)
        {
            Vector2 dir;
            if (direction.HasValue)
            {
                float spread = Random.Range(-0.7f, 0.7f);
                var d = direction.Value.normalized;
                dir = new Vector2(d.x * Mathf.Cos(spread) - d.y * Mathf.Sin(spread), d.x * Mathf.Sin(spread) + d.y * Mathf.Cos(spread));
            }
            else dir = Random.insideUnitCircle.normalized;

            float dist = Random.Range(0.3f, 1.6f) * power;
            float arc = Random.Range(0.3f, 0.9f);
            float size = Random.Range(0.12f, 0.3f);
            float life = Random.Range(0.35f, 0.6f);
            var c = Color.Lerp(Blood, BloodDark, Random.value);
            var start = pos + new Vector2(0f, 0.2f);
            var land = pos + dir * dist + new Vector2(0f, -0.25f);
            var drop = MakeSprite(Sprites.Glow, start, c, size);
            float landSize = size * Random.Range(1.2f, 2.2f);
            Animate(drop, life, k =>
            {
                var p = Vector2.Lerp(start, land, k) + new Vector2(0f, Mathf.Sin(k * Mathf.PI) * arc);
                drop.transform.position = new Vector3(p.x, p.y, p.y / 1000f - 0.5f);
                if (k > 0.97f) Stain(land, c, landSize);
            });
        }
        // central spurt
        Burst(pos, Blood, 1.3f * power, 0.3f);
        Stain(pos + new Vector2(0f, -0.3f), BloodDark, 1.1f * power);
    }

    private static void Stain(Vector2 pos, Color c, float size)
    {
        var sr = MakeSprite(Sprites.Glow, pos, A(c, 0.85f), size, "MarvelBloodStain");
        sr.transform.position = new Vector3(pos.x, pos.y, pos.y / 1000f + 0.4f);   // on the floor, behind players
        sr.transform.localScale = new Vector3(size * 1.4f, size * 0.8f, 1f);
        Stains.Add(sr.gameObject);
        Object.Destroy(sr.gameObject, 45f);
    }

    public static void ClearStains()
    {
        foreach (var s in Stains)
            if (s != null) Object.Destroy(s);
        Stains.Clear();
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

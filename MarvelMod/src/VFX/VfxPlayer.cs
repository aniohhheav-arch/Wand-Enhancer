using Reactor.Utilities.Attributes;
using System;
using Object = UnityEngine.Object;
using Reactor.Utilities;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.VFX;

public enum VfxType
{
    // Loki green-gold
    LokiSpawn, LokiMagic, LokiShapeshift, LokiTeleport, LokiSmoke,
    LokiCast, LokiRibbons, LokiShield, LokiDagger, LokiDaggerStorm,
    LokiUltimate, LokiMapGreen, FakeKillFlash, LokiMiss,
}

public enum DistortionType { Environmental }

public static class VfxPlayer
{
    public static void PlayLocal(VfxType type, Vector3 position)
    {
        if (!ModConfig.Data.VfxEnabled) return;
        SpawnParticles(type, position, global: false);
    }

    public static void PlayGlobal(VfxType type, float duration)
    {
        if (!ModConfig.Data.VfxEnabled) return;
        // Apply a screen-wide color tint matching the VFX type
        var cam = Camera.main;
        if (cam == null) return;
        Color tint = type switch
        {
            VfxType.LokiMapGreen => new Color(0.3f, 1f, 0.3f, 0.25f),
            _                    => new Color(0.5f, 0.3f, 0.8f, 0.2f),
        };
        ScreenTint.Apply(tint, duration);
    }

    private static void SpawnParticles(VfxType type, Vector3 pos, bool global)
    {
        if (!ModConfig.Data.VfxParticles) return;
        float intensity = ModConfig.Data.VfxIntensity / 100f;

        // In a real build, load prefabs from AssetBundles.
        // Here we create minimal particle systems in code.
        var go = new GameObject($"VFX_{type}");
        go.transform.position = pos;
        var ps = go.AddComponent<ParticleSystem>();
        var main = ps.main;
        main.startLifetimeMultiplier = 0.8f * intensity;
        main.startSpeedMultiplier    = 3f * intensity;
        main.startSizeMultiplier     = 0.3f * intensity;
        main.startColor    = GetColor(type);
        main.maxParticles  = Mathf.RoundToInt(30 * intensity);
        var emission = ps.emission;
        emission.rateOverTime = 0;
        ps.Emit(Mathf.RoundToInt(20 * intensity));

        if (ModConfig.Data.VfxScreenShake && type is VfxType.LokiUltimate or VfxType.LokiDaggerStorm)
            ScreenShake.Trigger(0.3f, 0.4f);

        Object.Destroy(go, 2f);
    }

    private static Color GetColor(VfxType type) => type switch
    {
        VfxType.LokiShapeshift   => new Color(0.2f, 1f,   0.4f),
        VfxType.LokiTeleport     => new Color(0.2f, 0.9f, 0.2f),
        VfxType.LokiSmoke        => new Color(0.3f, 0.8f, 0.3f, 0.6f),
        VfxType.LokiDagger       => new Color(0.1f, 0.8f, 0.3f),
        VfxType.LokiDaggerStorm  => new Color(0.2f, 1f,   0.5f),
        VfxType.LokiUltimate     => new Color(0.1f, 1f,   0.3f),
        VfxType.FakeKillFlash    => new Color(1f,   0.9f, 0.1f, 0.8f),
        VfxType.LokiRibbons      => new Color(0.4f, 1f,   0.6f),
        VfxType.LokiShield       => new Color(0.5f, 1f,   0.7f),
        _                        => new Color(0.3f, 0.9f, 0.4f),
    };
}

public static class ScreenTint
{
    public static void Apply(Color color, float duration)
    {
        // Overlays a translucent colored quad on the UI camera layer
        var go = new GameObject("ScreenTint");
        var sr = go.AddComponent<SpriteRenderer>();
        sr.color = color;
        sr.sortingOrder = 999;
        go.transform.localScale = new Vector3(30, 20, 1);
        Object.Destroy(go, duration);
    }
}

public static class ScreenShake
{
    public static void Trigger(float magnitude, float duration)
    {
        if (!ModConfig.Data.VfxScreenShake) return;
        var cam = Camera.main;
        if (cam == null) return;
        var shaker = cam.GetComponent<CameraShaker>() ?? cam.gameObject.AddComponent<CameraShaker>();
        shaker.Shake(magnitude, duration);
    }
}

[RegisterInIl2Cpp]
public class CameraShaker : MonoBehaviour
{
    public CameraShaker(IntPtr ptr) : base(ptr) { }

    private float _mag, _dur, _elapsed;
    private Vector3 _origin;

    public void Shake(float mag, float dur)
    {
        _mag = mag; _dur = dur; _elapsed = 0;
        _origin = transform.localPosition;
    }

    public void Update()
    {
        if (_elapsed >= _dur) { transform.localPosition = _origin; return; }
        _elapsed += Time.deltaTime;
        float fade = 1f - _elapsed / _dur;
        transform.localPosition = _origin + (Vector3)UnityEngine.Random.insideUnitCircle * _mag * fade;
    }
}

public static class ScreenDistortion
{
    public static void Apply(PlayerControl target, DistortionType type, float duration)
    {
        // Local-client visual distortion: scale wobble
        if (target != PlayerControl.LocalPlayer) return;
        Coroutines.Start(Wobble(target, duration));
    }

    public static void ShowGhostPlayer(PlayerControl viewer, PlayerControl witnessed, Vector3 pos, float duration)
    {
        if (viewer != PlayerControl.LocalPlayer) return;
        // Create a translucent clone sprite at pos
        var go = new GameObject("GhostPlayer");
        go.transform.position = pos;
        var sr = go.AddComponent<SpriteRenderer>();
        sr.color = new Color(1, 1, 1, 0.4f);
        Object.Destroy(go, duration);
    }

    private static System.Collections.IEnumerator Wobble(PlayerControl target, float duration)
    {
        float elapsed = 0;
        var orig = target.transform.localScale;
        while (elapsed < duration)
        {
            elapsed += Time.deltaTime;
            float s = 1f + Mathf.Sin(elapsed * 12f) * 0.06f;
            target.transform.localScale = orig * s;
            yield return null;
        }
        target.transform.localScale = orig;
    }
}

public static class HudMessageBroadcaster
{
    /// <summary>Shows a simulated alert to a target player (local only — NOT a real game event).</summary>
    public static void ShowFakeAlert(PlayerControl? target, string msg, float duration)
    {
        if (target != PlayerControl.LocalPlayer) return;
        // Display via Among Us HUD notification system
        var hud = HudManager.Instance;
        if (hud == null) return;
        hud.Notifier?.AddDisconnectMessage(msg);
    }
}

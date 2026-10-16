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

/// <summary>Named effect presets. Abilities run on every client, so these play for everyone.</summary>
public static class VfxPlayer
{
    public static void PlayLocal(VfxType type, Vector3 position)
    {
        if (!ModConfig.Data.VfxEnabled) return;
        Vector2 p = position;
        switch (type)
        {
            case VfxType.LokiSpawn:
                Fx.Burst(p, Fx.LokiGreen, 1.8f);
                Fx.Sparkles(p, Fx.LokiGold, 10);
                break;
            case VfxType.LokiMagic:
                Fx.Burst(p, Fx.LokiLight, 1.2f, 0.35f);
                Fx.Sparkles(p, Fx.LokiGreen, 8, 1f);
                break;
            case VfxType.LokiShapeshift:
                Fx.Smoke(p, Fx.LokiGreen, 10, 1f);
                Fx.Burst(p, Fx.LokiLight, 1.6f);
                Fx.Sparkles(p, Fx.LokiGold, 6, 1f);
                break;
            case VfxType.LokiTeleport:
                Fx.Burst(p, Fx.LokiGreen, 1.4f, 0.3f);
                Fx.Sparkles(p, Fx.LokiLight, 12, 1.1f, 0.5f);
                break;
            case VfxType.LokiSmoke:
                Fx.Smoke(p, Fx.LokiDark, 12, 1.1f);
                break;
            case VfxType.LokiCast:
                Fx.Burst(p, Fx.LokiGold, 0.9f, 0.3f);
                break;
            case VfxType.LokiRibbons:
                Fx.Sparkles(p, Fx.LokiGold, 12, 1.2f);
                Fx.Burst(p, Fx.LokiGold, 1.3f);
                break;
            case VfxType.LokiShield:
                Fx.Burst(p, Fx.LokiLight, 2f, 0.5f);
                break;
            case VfxType.LokiDagger:
                Fx.Burst(p, Fx.LokiLight, 1.1f, 0.25f);
                Fx.Sparkles(p, Color.white, 8, 0.9f, 0.4f);
                break;
            case VfxType.LokiDaggerStorm:
                Fx.Burst(p, Fx.LokiGreen, 1.5f, 0.35f);
                Fx.Sparkles(p, Fx.LokiLight, 10, 1.2f, 0.5f);
                break;
            case VfxType.LokiUltimate:
                Fx.Burst(p, Fx.LokiGold, 3.5f, 0.7f);
                Fx.Burst(p, Fx.LokiGreen, 2.5f, 0.5f);
                Fx.Sparkles(p, Fx.LokiGold, 24, 2.5f, 1f);
                Fx.Shake(0.4f, 0.4f);
                break;
            case VfxType.FakeKillFlash:
                Fx.ScreenTint(new Color(1f, 0.1f, 0.1f, 0.55f), 0.5f);
                Fx.Shake(0.3f, 0.5f);
                break;
            case VfxType.LokiMiss:
                Fx.Smoke(p, new Color(0.5f, 0.5f, 0.5f, 1f), 5, 0.6f);
                break;
        }
    }

    public static void PlayGlobal(VfxType type, float duration)
    {
        if (!ModConfig.Data.VfxEnabled) return;
        if (type == VfxType.LokiMapGreen)
            Fx.ScreenTint(new Color(0.15f, 0.9f, 0.25f, 0.28f), duration);
    }
}

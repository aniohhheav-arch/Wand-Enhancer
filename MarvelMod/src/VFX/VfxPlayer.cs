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
            case VfxType.LokiSpawn:   // a duplicate "unfolds": gold seam, green bloom, glyph sparks
                Fx.Flare(p, Fx.LokiGold, 0.7f, 0.2f);
                Fx.ShockRing(p, Fx.LokiGold, 1.6f, 0.35f, squash: 0.5f);
                Fx.Burst(p, Fx.LokiGreen, 1.4f);
                Fx.Sparkles(p, Fx.LokiGold, 8);
                Sfx.Play(SfxKind.Shimmer, 1.2f * Sfx.Vary(), p, 0.35f);
                break;
            case VfxType.LokiMagic:
                Fx.Burst(p, Fx.LokiLight, 1.2f, 0.35f);
                Fx.Sparkles(p, Fx.LokiGreen, 8, 1f);
                break;
            case VfxType.LokiShapeshift:   // green smoke curtain whirls shut, gold ring snaps, the new face is revealed
                Fx.Smoke(p, Fx.LokiGreen, 10, 1f);
                Fx.Sigil(p, Fx.LokiGreen, 1.3f, 0.1f, spinBack: false);
                Fx.Flare(p, Fx.LokiLight, 1f, 0.25f);
                Fx.Sparkles(p, Fx.LokiGold, 6, 1f);
                Sfx.Play(SfxKind.Warp, 1.1f * Sfx.Vary(), p, 0.5f);
                break;
            case VfxType.LokiTeleport:   // folds away into a gold point
                Fx.Release(p, Fx.LokiGreen, ImpactStyle.Cosmic, 0.5f);
                Fx.Sparkles(p, Fx.LokiGold, 10, 1.1f, 0.5f);
                break;
            case VfxType.LokiSmoke:   // smoke bomb
                Fx.Smoke(p, Fx.LokiDark, 14, 1.2f);
                Fx.ShockRing(p, Fx.LokiDark, 1.8f, 0.3f, squash: 0.6f);
                Sfx.Play(SfxKind.Pop, 0.6f, p, 0.5f);
                Sfx.Play(SfxKind.Whoosh, 1.2f, p, 0.4f);
                break;
            case VfxType.LokiCast:
                Fx.Burst(p, Fx.LokiGold, 0.9f, 0.3f);
                break;
            case VfxType.LokiRibbons:   // gold ribbons whip around and cinch
                for (int i = 0; i < 4; i++)
                {
                    float a = i * Mathf.PI / 2f + 0.4f;
                    Fx.Tendril(p + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * 1.2f, p, Fx.LokiGold, 0.5f, 8);
                }
                Fx.ShockRing(p, Fx.LokiGold, 1f, 0.4f, squash: 0.4f);
                Sfx.Play(SfxKind.Whoosh, 1.6f, p, 0.4f);
                Sfx.Play(SfxKind.Creak, 1.4f, p, 0.4f);
                break;
            case VfxType.LokiShield:   // a gold-green sigil plate locks in front of him
                Fx.Sigil(p, Fx.LokiGold, 1.6f, 0.3f);
                Fx.Burst(p, Fx.LokiLight, 2f, 0.5f);
                Sfx.Play(SfxKind.Glass, 1.4f, p, 0.4f);
                break;
            case VfxType.LokiDagger:   // sharp: white streaks, small green burst, steel ring
                Fx.Flare(p, Color.white, 0.5f, 0.12f);
                Fx.Streaks(p, Fx.LokiLight, 6, 0.9f);
                Fx.Burst(p, Fx.LokiGreen, 0.9f, 0.25f);
                Sfx.Play(SfxKind.Slash, 1.3f * Sfx.Vary(), p, 0.7f);
                Sfx.Play(SfxKind.Clang, 1.8f, p, 0.3f);
                break;
            case VfxType.LokiDaggerStorm:
                Fx.Streaks(p, Fx.LokiLight, 8, 1.2f);
                Fx.Burst(p, Fx.LokiGreen, 1.5f, 0.35f);
                Fx.Sparkles(p, Fx.LokiGold, 8, 1.2f, 0.5f);
                Sfx.Play(SfxKind.Slash, 1.1f * Sfx.Vary(), p, 0.6f);
                break;
            case VfxType.LokiUltimate:   // horned sigil + gold detonation
                Fx.Sigil(p, Fx.LokiGold, 2.6f, 0.4f);
                Fx.Release(p, Fx.LokiGold, ImpactStyle.Magic, 2.2f, cinematic: true);
                Fx.Burst(p, Fx.LokiGreen, 2.5f, 0.5f);
                Fx.Shake(0.4f, 0.4f);
                break;
            case VfxType.FakeKillFlash:
                Fx.ScreenTint(new Color(1f, 0.1f, 0.1f, 0.55f), 0.5f);
                Fx.Shake(0.3f, 0.5f);
                break;
            case VfxType.LokiMiss:
                Fx.Smoke(p, new Color(0.5f, 0.5f, 0.5f, 1f), 5, 0.6f);
                Sfx.Play(SfxKind.Pop, 0.8f, p, 0.3f);
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

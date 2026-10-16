using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using UnityEngine;

namespace MarvelMod.Assets;

/// <summary>
/// Ability icons (embedded PNGs from Resources/Icons) and procedurally generated effect sprites.
/// Everything is flagged HideAndDontSave so Unity never unloads it on scene change.
/// </summary>
public static class Sprites
{
    private static readonly Dictionary<string, Sprite> Cache = new();

    /// <summary>Icon for an ability id (or "switch_tab"), sized with the given pixels-per-unit.</summary>
    public static Sprite? Icon(string id, float pixelsPerUnit)
    {
        var key = $"icon:{id}:{pixelsPerUnit:F2}";
        if (Cache.TryGetValue(key, out var cached) && cached != null) return cached;

        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream($"MarvelMod.Resources.Icons.{id}.png");
        if (stream == null) return null;
        using var ms = new MemoryStream();
        stream.CopyTo(ms);

        var tex = NewTexture(2, 2);
        if (!ImageConversion.LoadImage(tex, ms.ToArray(), false)) return null;
        return Cache[key] = MakeSprite(tex, pixelsPerUnit);
    }

    /// <summary>Soft round glow (white, tint with SpriteRenderer.color).</summary>
    public static Sprite Glow => Generated("glow", 128, (x, y) =>
    {
        float r = Mathf.Sqrt(x * x + y * y);
        float a = Mathf.Clamp01(1f - r);
        return a * a;
    });

    /// <summary>Thin soft-edged ring.</summary>
    public static Sprite Ring => Generated("ring", 128, (x, y) =>
    {
        float r = Mathf.Sqrt(x * x + y * y);
        float a = 1f - Mathf.Abs(r - 0.82f) / 0.12f;
        return Mathf.Clamp01(a);
    });

    /// <summary>Four-point sparkle star.</summary>
    public static Sprite Star => Generated("star", 64, (x, y) =>
    {
        float ax = Mathf.Abs(x), ay = Mathf.Abs(y);
        float shape = Mathf.Sqrt(ax) + Mathf.Sqrt(ay);   // astroid: 4 sharp points
        float a = Mathf.Clamp01((1f - shape) * 3f);
        float core = Mathf.Clamp01(1f - Mathf.Sqrt(ax * ax + ay * ay) * 3f);
        return Mathf.Clamp01(a + core);
    });

    /// <summary>Solid white square (for full-screen tints).</summary>
    public static Sprite Square => Generated("square", 4, (_, _) => 1f);

    private static Sprite Generated(string name, int size, Func<float, float, float> alphaAt)
    {
        if (Cache.TryGetValue(name, out var cached) && cached != null) return cached;

        var tex = NewTexture(size, size);
        var pixels = new Color[size * size];
        for (int py = 0; py < size; py++)
        for (int px = 0; px < size; px++)
        {
            // map pixel centre to [-1, 1]
            float x = (px + 0.5f) / size * 2f - 1f;
            float y = (py + 0.5f) / size * 2f - 1f;
            pixels[py * size + px] = new Color(1f, 1f, 1f, alphaAt(x, y));
        }
        tex.SetPixels(pixels);
        tex.Apply(false, false);
        return Cache[name] = MakeSprite(tex, size);   // 1 world unit across at scale 1
    }

    private static Texture2D NewTexture(int w, int h)
    {
        var tex = new Texture2D(w, h, TextureFormat.RGBA32, false)
        {
            hideFlags = HideFlags.HideAndDontSave,
            wrapMode = TextureWrapMode.Clamp,
            filterMode = FilterMode.Bilinear,
        };
        return tex;
    }

    private static Sprite MakeSprite(Texture2D tex, float pixelsPerUnit)
    {
        var sprite = Sprite.Create(tex, new Rect(0, 0, tex.width, tex.height), new Vector2(0.5f, 0.5f), pixelsPerUnit);
        sprite.hideFlags = HideFlags.HideAndDontSave;
        return sprite;
    }
}

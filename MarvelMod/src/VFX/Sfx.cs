using System;
using System.Collections.Generic;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.VFX;

public enum SfxKind { Zap, Boom, Whoosh, Chime, Hum, Snap, Slash, Splat, Choir, Bell, Creak, Swell,
    Crackle, Rumble, Shimmer, Glass, Warp, Tick, Gong, Heartbeat, Rise, Thud }

/// <summary>
/// Procedurally synthesised sound effects (no audio files needed). Each stone / effect picks a
/// kind + base pitch; clips are generated once and cached.
/// </summary>
public static class Sfx
{
    private const int Rate = 22050;
    private static AudioSource? _source;
    private static readonly Dictionary<string, AudioClip> Clips = new();
    private static readonly System.Random Noise = new(1234);

    public static void Init(GameObject host)
    {
        _source = host.AddComponent<AudioSource>();
        _source.playOnAwake = false;
        _source.spatialBlend = 0f;
    }

    // ── limiter: stops stacked effects from becoming loud or chaotic ──
    private static readonly List<float> _activeUntil = new();
    private static readonly Dictionary<string, float> _lastPlayed = new();
    private const int MaxVoices = 10;

    /// <summary>Plays a sound; quieter the further `at` is from the local player.</summary>
    public static void Play(SfxKind kind, float pitch = 1f, Vector2? at = null, float volume = 1f, bool cinematic = false)
    {
        if (_source == null || !ModConfig.Data.SfxEnabled) return;
        float vol = volume * Mathf.Clamp01(ModConfig.Data.SfxVolume) * (cinematic ? Mathf.Clamp01(ModConfig.Data.CinematicVolume) : 1f);
        string key = $"{kind}:{pitch:F2}";
        if (_lastPlayed.TryGetValue(key, out var last) && Time.time - last < 0.06f) return;   // the same sound twice in one frame burst
        _activeUntil.RemoveAll(t => t < Time.time);
        if (_activeUntil.Count >= MaxVoices) return;
        vol /= Mathf.Sqrt(1f + _activeUntil.Count * 0.35f);                                       // duck when busy
        if (at.HasValue && PlayerControl.LocalPlayer != null)
        {
            float d = Vector2.Distance(PlayerControl.LocalPlayer.transform.position, at.Value);
            vol *= Mathf.Clamp01(1.2f - d / 10f);
        }
        if (vol <= 0.01f) return;
        var clip = Get(kind, Mathf.Round(pitch * 20f) / 20f);
        if (clip == null) return;
        _source.PlayOneShot(clip, vol);
        _lastPlayed[key] = Time.time;
        _activeUntil.Add(Time.time + clip.length);
    }

    private static AudioClip? Get(SfxKind kind, float pitch)
    {
        var key = $"{kind}:{pitch:F2}";
        if (Clips.TryGetValue(key, out var c) && c != null) return c;
        float[] data = kind switch
        {
            SfxKind.Zap    => Zap(pitch),
            SfxKind.Boom   => Boom(pitch),
            SfxKind.Whoosh => Whoosh(pitch),
            SfxKind.Chime  => Chime(pitch),
            SfxKind.Hum    => Hum(pitch),
            SfxKind.Snap   => SnapSound(),
            SfxKind.Slash  => Slash(pitch),
            SfxKind.Choir  => Choir(pitch),
            SfxKind.Bell   => Bell(pitch),
            SfxKind.Creak  => Creak(pitch),
            SfxKind.Swell  => Swell(pitch),
            SfxKind.Crackle => Crackle(pitch),
            SfxKind.Rumble => Rumble(pitch),
            SfxKind.Shimmer => Shimmer(pitch),
            SfxKind.Glass  => Glass(pitch),
            SfxKind.Warp   => Warp(pitch),
            SfxKind.Tick   => Tick(pitch),
            SfxKind.Gong   => Gong(pitch),
            SfxKind.Heartbeat => Heartbeat(pitch),
            SfxKind.Rise   => Rise(pitch),
            SfxKind.Thud   => Thud(pitch),
            _              => Splat(),
        };
        var clip = AudioClip.Create(key, data.Length, 1, Rate, false);
        clip.SetData(data, 0);
        clip.hideFlags = HideFlags.HideAndDontSave;
        return Clips[key] = clip;
    }

    private static float Rnd() => (float)(Noise.NextDouble() * 2 - 1);
    private static float[] Buf(float seconds) => new float[(int)(Rate * seconds)];

    private static float[] Zap(float p)
    {
        var b = Buf(0.4f);
        double phase = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            float f = Mathf.Lerp(1400f, 180f, t) * p;
            phase += 2 * Math.PI * f / Rate;
            b[i] = (float)(Math.Sin(phase) * 0.6 + Rnd() * 0.15) * Mathf.Exp(-t * 5f);
        }
        return b;
    }

    private static float[] Boom(float p)
    {
        var b = Buf(1.2f);
        float lp = 0;
        double phase = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            lp += (Rnd() - lp) * 0.06f;
            phase += 2 * Math.PI * Mathf.Lerp(90f, 40f, t) * p / Rate;
            b[i] = (lp * 1.6f + (float)Math.Sin(phase) * 0.7f) * Mathf.Exp(-t * 4f);
        }
        return b;
    }

    private static float[] Whoosh(float p)
    {
        var b = Buf(0.6f);
        float lp = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            float cutoff = Mathf.Lerp(0.02f, 0.25f, Mathf.Sin(t * Mathf.PI)) * p;
            lp += (Rnd() - lp) * cutoff;
            b[i] = lp * 1.5f * Mathf.Sin(t * Mathf.PI);
        }
        return b;
    }

    private static float[] Chime(float p)
    {
        var b = Buf(1.3f);
        float f = 660f * p;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float env = Mathf.Exp(-t * 3f);
            b[i] = (Mathf.Sin(2 * Mathf.PI * f * t) * 0.5f + Mathf.Sin(2 * Mathf.PI * f * 2.01f * t) * 0.25f
                    + Mathf.Sin(2 * Mathf.PI * f * 3.02f * t) * 0.12f) * env;
        }
        return b;
    }

    private static float[] Hum(float p)
    {
        var b = Buf(1f);
        float f = 110f * p;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float env = Mathf.Sin(Mathf.PI * t);
            float trem = 0.7f + 0.3f * Mathf.Sin(2 * Mathf.PI * 7f * t);
            b[i] = (Mathf.Sin(2 * Mathf.PI * f * t) + 0.5f * Mathf.Sin(2 * Mathf.PI * f * 1.5f * t)) * 0.4f * env * trem;
        }
        return b;
    }

    private static float[] SnapSound()
    {
        var b = Buf(1.4f);
        float lp = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float click = t < 0.03f ? Rnd() * (1f - t / 0.03f) : 0f;
            lp += (Rnd() - lp) * 0.03f;
            float rumble = t > 0.05f ? lp * 1.4f * Mathf.Exp(-(t - 0.05f) * 2.2f) : 0f;
            b[i] = click + rumble;
        }
        return b;
    }

    private static float[] Slash(float p)
    {
        var b = Buf(0.18f);
        float prev = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            float n = Rnd();
            b[i] = (n - prev) * 0.6f * Mathf.Sin(t * Mathf.PI) * p;   // crude high-pass
            prev = n;
        }
        return b;
    }

    /// <summary>"Aah" choir pad: minor chord of detuned voices with vibrato.</summary>
    private static float[] Choir(float p)
    {
        var b = Buf(3f);
        float[] notes = { 220f, 261.6f, 329.6f, 440f };
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float env = Mathf.Min(1f, t / 0.6f) * Mathf.Min(1f, (3f - t) / 0.8f);
            float v = 0f;
            foreach (var n in notes)
                for (int voice = -1; voice <= 1; voice++)
                {
                    float f = n * p * (1f + voice * 0.004f) * (1f + 0.006f * Mathf.Sin(2 * Mathf.PI * 5.5f * t + voice));
                    v += Mathf.Sin(2 * Mathf.PI * f * t) + 0.3f * Mathf.Sin(4 * Mathf.PI * f * t);
                }
            b[i] = v / 18f * env;
        }
        return b;
    }

    private static float[] Bell(float p)
    {
        var b = Buf(2.5f);
        float f = 880f * p;
        float[] partials = { 1f, 2.76f, 5.4f, 8.93f };
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float v = 0f;
            for (int k = 0; k < partials.Length; k++)
                v += Mathf.Sin(2 * Mathf.PI * f * partials[k] * t) * Mathf.Exp(-t * (1.5f + k * 1.2f)) / (k + 1);
            b[i] = v * 0.5f;
        }
        return b;
    }

    /// <summary>Creaking magical strings: sawtooth with wobbling pitch.</summary>
    private static float[] Creak(float p)
    {
        var b = Buf(0.9f);
        double phase = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            float f = (140f + 60f * Mathf.Sin(t * 9f) + Rnd() * 8f) * p;
            phase += f / Rate;
            float saw = (float)(phase - Math.Floor(phase)) * 2f - 1f;
            b[i] = saw * 0.25f * Mathf.Sin(t * Mathf.PI) * (0.6f + 0.4f * Mathf.Sin(t * 70f));
        }
        return b;
    }

    /// <summary>Orchestral swell: rising stacked fifths.</summary>
    private static float[] Swell(float p)
    {
        var b = Buf(2.5f);
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float k = t / 2.5f;
            float env = k * k * (k < 0.92f ? 1f : (1f - k) / 0.08f);
            float f = 110f * p * (1f + k * 0.5f);
            b[i] = (Mathf.Sin(2 * Mathf.PI * f * t) + 0.7f * Mathf.Sin(2 * Mathf.PI * f * 1.5f * t)
                    + 0.5f * Mathf.Sin(2 * Mathf.PI * f * 2f * t)) * 0.3f * env;
        }
        return b;
    }

    /// <summary>Electric crackle: sparse random spikes with a buzzing tone.</summary>
    private static float[] Crackle(float p)
    {
        var b = Buf(0.6f);
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            float spike = Noise.NextDouble() < 0.02 ? Rnd() : 0f;
            float buzz = Mathf.Sign(Mathf.Sin(2 * Mathf.PI * 120f * p * i / Rate)) * 0.08f;
            b[i] = (spike * 0.9f + buzz) * (1f - t);
        }
        return b;
    }

    private static float[] Rumble(float p)
    {
        var b = Buf(1.4f);
        float lp = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            lp += (Rnd() - lp) * 0.01f * p;
            b[i] = lp * 4f * Mathf.Sin(t * Mathf.PI);
        }
        return b;
    }

    /// <summary>High sparkling arpeggio.</summary>
    private static float[] Shimmer(float p)
    {
        var b = Buf(0.9f);
        float[] notes = { 1320f, 1760f, 1980f, 2640f };
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            int n = Mathf.Min(notes.Length - 1, (int)(t / 0.12f));
            float local = t - n * 0.12f;
            b[i] = Mathf.Sin(2 * Mathf.PI * notes[n] * p * t) * Mathf.Exp(-local * 9f) * 0.35f * (1f - t / 0.9f);
        }
        return b;
    }

    /// <summary>Shattering glass: bright inharmonic pings over noise.</summary>
    private static float[] Glass(float p)
    {
        var b = Buf(0.8f);
        float[] f = { 2300f, 3170f, 4120f, 5300f };
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float v = Rnd() * 0.3f * Mathf.Exp(-t * 25f);
            for (int k = 0; k < f.Length; k++) v += Mathf.Sin(2 * Mathf.PI * f[k] * p * t + k) * 0.12f * Mathf.Exp(-t * (6f + k * 2f));
            b[i] = v;
        }
        return b;
    }

    /// <summary>Pitch-bending warble (space / reality warping).</summary>
    private static float[] Warp(float p)
    {
        var b = Buf(0.9f);
        double phase = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            float f = (300f + 220f * Mathf.Sin(t * 18f) * (1f - t)) * p;
            phase += 2 * Math.PI * f / Rate;
            b[i] = (float)Math.Sin(phase) * 0.35f * Mathf.Sin(t * Mathf.PI);
        }
        return b;
    }

    /// <summary>Clock ticks.</summary>
    private static float[] Tick(float p)
    {
        var b = Buf(1f);
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float local = t % 0.25f;
            b[i] = Mathf.Sin(2 * Mathf.PI * 2200f * p * t) * Mathf.Exp(-local * 140f) * 0.5f;
        }
        return b;
    }

    private static float[] Gong(float p)
    {
        var b = Buf(3f);
        float f = 110f * p;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float shimmer = 1f + 0.003f * Mathf.Sin(t * 5f);
            b[i] = (Mathf.Sin(2 * Mathf.PI * f * t) * 0.5f + Mathf.Sin(2 * Mathf.PI * f * 2.4f * shimmer * t) * 0.3f
                    + Mathf.Sin(2 * Mathf.PI * f * 3.9f * t) * 0.15f) * Mathf.Exp(-t * 1.1f);
        }
        return b;
    }

    private static float[] Heartbeat(float p)
    {
        var b = Buf(1.2f);
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / Rate;
            float a = Mathf.Exp(-((t - 0.1f) * (t - 0.1f)) * 900f) + 0.7f * Mathf.Exp(-((t - 0.32f) * (t - 0.32f)) * 900f);
            b[i] = Mathf.Sin(2 * Mathf.PI * 55f * p * t) * a * 0.9f;
        }
        return b;
    }

    /// <summary>Charging riser: rising noise + tone.</summary>
    private static float[] Rise(float p)
    {
        var b = Buf(1f);
        double phase = 0;
        float lp = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            phase += 2 * Math.PI * Mathf.Lerp(150f, 900f, t * t) * p / Rate;
            lp += (Rnd() - lp) * Mathf.Lerp(0.02f, 0.3f, t);
            b[i] = ((float)Math.Sin(phase) * 0.3f + lp * 0.4f) * t;
        }
        return b;
    }

    /// <summary>Heavy body impact.</summary>
    private static float[] Thud(float p)
    {
        var b = Buf(0.5f);
        double phase = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            phase += 2 * Math.PI * Mathf.Lerp(120f, 45f, t) * p / Rate;
            b[i] = ((float)Math.Sin(phase) + Rnd() * 0.3f * Mathf.Exp(-t * 30f)) * Mathf.Exp(-t * 6f) * 0.9f;
        }
        return b;
    }

    private static float[] Splat()
    {
        var b = Buf(0.3f);
        float lp = 0;
        for (int i = 0; i < b.Length; i++)
        {
            float t = (float)i / b.Length;
            lp += (Rnd() - lp) * 0.15f;
            b[i] = lp * 1.4f * Mathf.Exp(-t * 7f);
        }
        return b;
    }
}

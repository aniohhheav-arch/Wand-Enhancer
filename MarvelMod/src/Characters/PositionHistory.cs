using System.Collections.Generic;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>The local player's recent positions (Rewind / Temporal Echo for any character).</summary>
public static class PositionHistory
{
    private static readonly List<(float t, Vector2 p)> History = new();
    private static float _lastSample;

    public static void Tick()
    {
        var lp = PlayerControl.LocalPlayer;
        if (lp == null || Time.time - _lastSample < 0.1f) return;
        _lastSample = Time.time;
        History.Add((Time.time, lp.transform.position));
        while (History.Count > 0 && Time.time - History[0].t > 12f) History.RemoveAt(0);
    }

    public static Vector2 Ago(float seconds, Vector2 fallback)
    {
        float t = Time.time - seconds;
        foreach (var h in History) if (h.t >= t) return h.p;
        return History.Count > 0 ? History[0].p : fallback;
    }

    public static List<Vector2> Since(float seconds)
    {
        float t = Time.time - seconds;
        var path = new List<Vector2>();
        foreach (var h in History) if (h.t >= t) path.Add(h.p);
        return path;
    }
}

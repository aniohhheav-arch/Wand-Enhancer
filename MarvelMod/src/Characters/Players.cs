using System.Collections.Generic;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>Player lookups, targeting and safe (wall-aware) movement.</summary>
public static class Players
{
    public static IEnumerable<PlayerControl> All()
    {
        foreach (var pc in PlayerControl.AllPlayerControls.ToArray())
            if (pc != null) yield return pc;
    }

    public static PlayerControl? ById(byte id)
    {
        foreach (var pc in All())
            if (pc.PlayerId == id) return pc;
        return null;
    }

    public static bool IsAlive(PlayerControl? pc) =>
        pc != null && pc.Data != null && !pc.Data.IsDead && !pc.Data.Disconnected;

    public static Vector2 Pos(PlayerControl pc) => pc.transform.position;

    public static bool WallBetween(Vector2 a, Vector2 b) =>
        PhysicsHelpers.AnythingBetween(a, b, Constants.ShipAndObjectsMask, false);

    /// <summary>Closest living, visible, non-vented player within range and line of sight.</summary>
    public static PlayerControl? ClosestTarget(PlayerControl caster, float range)
    {
        PlayerControl? best = null;
        float bestDist = range;
        var from = Pos(caster);
        foreach (var pc in All())
        {
            if (pc == caster || !IsAlive(pc) || pc.inVent || !pc.Visible) continue;
            float d = Vector2.Distance(from, Pos(pc));
            if (d > bestDist || WallBetween(from, Pos(pc))) continue;
            best = pc;
            bestDist = d;
        }
        return best;
    }

    public static List<PlayerControl> InRadius(Vector2 center, float radius, int max, PlayerControl? exclude = null)
    {
        var result = new List<PlayerControl>();
        foreach (var pc in All())
        {
            if (pc == exclude || !IsAlive(pc)) continue;
            if (Vector2.Distance(center, Pos(pc)) <= radius)
            {
                result.Add(pc);
                if (result.Count >= max) break;
            }
        }
        return result;
    }

    /// <summary>Furthest point from origin along dir (up to dist) that doesn't go through a wall.</summary>
    public static Vector2 SafeDestination(Vector2 origin, Vector2 dir, float dist)
    {
        if (dir.sqrMagnitude < 0.0001f) return origin;
        dir = dir.normalized;
        var hit = Physics2D.Raycast(origin, dir, dist, Constants.ShipAndObjectsMask);
        if (hit.collider != null) dist = Mathf.Max(0f, hit.distance - 0.4f);
        return origin + dir * dist;
    }

    /// <summary>Moves a player; only the owning client sends the movement, so call this on every client.</summary>
    public static void Teleport(PlayerControl pc, Vector2 dest)
    {
        if (pc == null || !pc.AmOwner) return;
        pc.NetTransform.RpcSnapTo(dest);
    }

    public static void Knockback(PlayerControl target, Vector2 from, float dist)
    {
        if (target == null || !target.AmOwner) return;
        var p = Pos(target);
        var dir = p - from;
        if (dir.sqrMagnitude < 0.0001f) dir = Vector2.right;
        Teleport(target, SafeDestination(p, dir, dist));
    }
}

/// <summary>Walkable points on the current map (vents), or open points near a position in the lobby.</summary>
public static class MapSpawnPoints
{
    public static List<Vector3> GetRandom(int count, System.Random rng, Vector2 near)
    {
        var pool = new List<Vector3>();
        var ship = ShipStatus.Instance;
        if (ship != null && ship.AllVents != null)
            foreach (var v in ship.AllVents)
                if (v != null) pool.Add(v.transform.position + new Vector3(0f, 0.3f, 0f));

        var list = new List<Vector3>();
        for (int i = 0; i < count; i++)
        {
            if (pool.Count > 0)
            {
                int idx = rng.Next(pool.Count);
                list.Add(pool[idx]);
                pool.RemoveAt(idx);
            }
            else
            {
                list.Add(NearbyOpenPoint(near, rng, 1f, 3.5f));
            }
        }
        return list;
    }

    /// <summary>A random point at minDist..maxDist from center with nothing in between.</summary>
    public static Vector3 NearbyOpenPoint(Vector2 center, System.Random rng, float minDist, float maxDist)
    {
        for (int tries = 0; tries < 12; tries++)
        {
            float ang = (float)(rng.NextDouble() * Mathf.PI * 2);
            float dist = minDist + (float)rng.NextDouble() * (maxDist - minDist);
            var p = center + new Vector2(Mathf.Cos(ang), Mathf.Sin(ang)) * dist;
            if (!Players.WallBetween(center, p)) return p;
        }
        return center;
    }
}

using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace MarvelMod.Characters;

// ── Stub support systems ──────────────────────────────────────────────────────
// These are intentionally thin — they provide real hooks into Among Us APIs
// where available, and safe no-ops where the API isn't exposed.

public enum StatusEffect { Bound, Stunned, Shielded, ChaosContract, Invisible }
public enum DamageType   { Melee, Magical, Projectile }

public class StatusManager : MonoBehaviour
{
    public static StatusManager? Instance { get; private set; }
    private readonly Dictionary<byte, List<ActiveStatus>> _statuses = new();
    private void Awake() => Instance = this;

    public void ApplyStatus(PlayerControl target, StatusEffect effect, float duration)
    {
        if (!_statuses.ContainsKey(target.PlayerId))
            _statuses[target.PlayerId] = new();
        _statuses[target.PlayerId].Add(new ActiveStatus { Effect = effect, ExpiresAt = Time.realtimeSinceStartup + duration });

        if (effect == StatusEffect.Bound || effect == StatusEffect.Stunned)
            target.moveable = false;
    }

    public bool HasStatus(PlayerControl target, StatusEffect effect)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return false;
        return list.Exists(s => s.Effect == effect && Time.realtimeSinceStartup < s.ExpiresAt);
    }

    private void Update()
    {
        float now = Time.realtimeSinceStartup;
        foreach (var kv in _statuses)
        {
            kv.Value.RemoveAll(s => now >= s.ExpiresAt);
            if (kv.Value.Count == 0)
            {
                var pc = PlayerControl.AllPlayerControls.ToArray().FirstOrDefault(p => p.PlayerId == kv.Key);
                if (pc != null) pc.moveable = true;
            }
        }
    }
    private class ActiveStatus { public StatusEffect Effect; public float ExpiresAt; }
}

public class DamageSystem : MonoBehaviour
{
    public static DamageSystem? Instance { get; private set; }
    private void Awake() => Instance = this;

    public void ApplyDamage(PlayerControl target, float amount, DamageType type)
    {
        // In Among Us there is no HP system — damage is presented as a cosmetic hit flash.
        // In Practice Mode with dummies, it tracks dummy HP.
        Practice.PracticeModeManager.Instance?.RecordDummyHit(target, amount);
    }
}

public class InvisibilityManager : MonoBehaviour
{
    public static InvisibilityManager? Instance { get; private set; }
    private void Awake() => Instance = this;

    public void ApplyInvisibility(PlayerControl target, float duration)
    {
        var renderer = target.GetComponent<SpriteRenderer>();
        if (renderer != null)
        {
            var c = renderer.color;
            renderer.color = new Color(c.r, c.g, c.b, 0.15f);
        }
        UnityEngine.Object.FindObjectOfType<MonoBehaviour>()
            ?.StartCoroutine(RestoreAfter(target, duration));
    }

    private static System.Collections.IEnumerator RestoreAfter(PlayerControl target, float duration)
    {
        yield return new WaitForSeconds(duration);
        var renderer = target.GetComponent<SpriteRenderer>();
        if (renderer != null)
        {
            var c = renderer.color;
            renderer.color = new Color(c.r, c.g, c.b, 1f);
        }
    }
}

public static class TargetFinder
{
    public static List<PlayerControl> GetPlayersInRadius(Vector3 center, float radius, int maxCount)
    {
        var result = new List<PlayerControl>();
        foreach (var pc in PlayerControl.AllPlayerControls.ToArray())
        {
            if (pc.Data == null || pc.Data.Disconnected) continue;
            if (Vector2.Distance(center, pc.transform.position) <= radius)
            {
                result.Add(pc);
                if (result.Count >= maxCount) break;
            }
        }
        return result;
    }
}

public static class MapSpawnPoints
{
    private static readonly Vector3[] _fallback =
    {
        new(-3,  1, 0), new( 3,  1, 0), new( 0, -2, 0),
        new(-5, -1, 0), new( 5, -1, 0), new( 0,  4, 0),
    };

    public static List<Vector3> GetRandom(int count)
    {
        var list = new List<Vector3>();
        var pool = new List<Vector3>(_fallback);
        for (int i = 0; i < count && pool.Count > 0; i++)
        {
            int idx = UnityEngine.Random.Range(0, pool.Count);
            list.Add(pool[idx]);
            pool.RemoveAt(idx);
        }
        return list;
    }
}

public class ProjectileManager : MonoBehaviour
{
    public static ProjectileManager? Instance { get; private set; }
    private void Awake() => Instance = this;

    public void FireProjectile(PlayerControl source, Vector2 dir, float range, float damage, ProjectileType type)
    {
        // Raycast to find first player in direction within range
        var hit = Physics2D.Raycast(source.transform.position, dir, range, LayerMask.GetMask("Players"));
        if (hit.collider != null)
        {
            var target = hit.collider.GetComponent<PlayerControl>();
            if (target != null)
                DamageSystem.Instance?.ApplyDamage(target, damage, DamageType.Projectile);
        }
    }
}

public enum ProjectileType { GreenBolt }

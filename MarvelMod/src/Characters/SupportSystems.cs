using System;
using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.VFX;
using Reactor.Utilities.Attributes;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Characters;

public enum StatusEffect { Bound, Stunned, Shielded, ChaosContract, Invisible }
public enum DamageType   { Melee, Magical, Projectile }

/// <summary>
/// Timed status effects with visible auras. Movement effects (Bound/Stunned = rooted,
/// ChaosContract = inverted controls) are enforced on the affected player's own client
/// by <see cref="Patches.MovementStatusPatch"/>.
/// </summary>
[RegisterInIl2Cpp]
public class StatusManager : MonoBehaviour
{
    public StatusManager(IntPtr ptr) : base(ptr) { }

    public static StatusManager? Instance { get; private set; }
    private readonly Dictionary<byte, List<ActiveStatus>> _statuses = new();
    public void Awake() => Instance = this;

    /// <summary>Applies an effect. Hostile effects are absorbed by Mystic Shield. Returns false if blocked.</summary>
    public bool ApplyStatus(PlayerControl target, StatusEffect effect, float duration)
    {
        if (!Players.IsAlive(target)) return false;
        bool hostile = effect is StatusEffect.Bound or StatusEffect.Stunned or StatusEffect.ChaosContract;
        if (hostile && ConsumeShield(target)) return false;

        if (duration <= 0f) duration = 30f;
        if (!_statuses.TryGetValue(target.PlayerId, out var list))
            _statuses[target.PlayerId] = list = new List<ActiveStatus>();

        // refresh instead of stacking
        var existing = list.Find(s => s.Effect == effect);
        if (existing != null)
        {
            existing.ExpiresAt = Time.time + duration;
            if (existing.Aura != null) Object.Destroy(existing.Aura);
            existing.Aura = MakeAura(target, effect, duration);
            return true;
        }

        list.Add(new ActiveStatus
        {
            Effect = effect,
            ExpiresAt = Time.time + duration,
            Aura = MakeAura(target, effect, duration),
        });

        if (target == PlayerControl.LocalPlayer)
        {
            switch (effect)
            {
                case StatusEffect.Bound:         Fx.Toast("BOUND BY LOKI'S RIBBONS!", Fx.LokiGold, 1.8f, 3.5f); break;
                case StatusEffect.Stunned:       Fx.Toast("STUNNED!", Fx.LokiLight, 1.2f, 3.5f); break;
                case StatusEffect.ChaosContract: Fx.Toast("CHAOS CONTRACT: YOUR CONTROLS ARE CURSED!", Fx.ChaosPurple, 2.5f, 3.2f); break;
            }
        }
        return true;
    }

    /// <summary>If the target is shielded, the shield pops instead. Returns true when something was blocked.</summary>
    public bool ConsumeShield(PlayerControl target)
    {
        if (!HasStatus(target, StatusEffect.Shielded)) return false;
        RemoveStatus(target, StatusEffect.Shielded);
        Fx.Burst(target.transform.position, Fx.LokiLight, 2.2f, 0.5f);
        Fx.Sparkles(target.transform.position, Color.white, 12, 1.4f);
        if (target == PlayerControl.LocalPlayer) Fx.Toast("SHIELD BLOCKED IT!", Fx.LokiLight, 1.5f, 3.5f);
        return true;
    }

    private static GameObject? MakeAura(PlayerControl target, StatusEffect effect, float duration) => effect switch
    {
        StatusEffect.Bound         => Fx.Aura(target.transform, Sprites.Ring, new Color(1f, 0.8f, 0.2f, 0.95f), 1.2f, duration, 140f),
        StatusEffect.Stunned       => Fx.Aura(target.transform, Sprites.Star, new Color(1f, 1f, 0.6f, 0.95f), 0.45f, duration, 360f, 0.75f),
        StatusEffect.Shielded      => Fx.Aura(target.transform, Sprites.Glow, new Color(0.5f, 1f, 0.7f, 0.45f), 1.8f, duration),
        StatusEffect.ChaosContract => Fx.Aura(target.transform, Sprites.Ring, new Color(0.65f, 0.35f, 1f, 0.9f), 1.0f, duration, -200f),
        _                          => null,
    };

    public bool HasStatus(PlayerControl target, StatusEffect effect)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return false;
        return list.Exists(s => s.Effect == effect && Time.time < s.ExpiresAt);
    }

    public void RemoveStatus(PlayerControl target, StatusEffect effect)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return;
        foreach (var s in list)
            if (s.Effect == effect && s.Aura != null) Object.Destroy(s.Aura);
        list.RemoveAll(s => s.Effect == effect);
    }

    public void ClearAll()
    {
        foreach (var list in _statuses.Values)
            foreach (var s in list)
                if (s.Aura != null) Object.Destroy(s.Aura);
        _statuses.Clear();
    }

    public void Update()
    {
        float now = Time.time;
        foreach (var list in _statuses.Values)
        {
            foreach (var s in list)
                if (now >= s.ExpiresAt && s.Aura != null) Object.Destroy(s.Aura);
            list.RemoveAll(s => now >= s.ExpiresAt);
        }
    }

    private class ActiveStatus
    {
        public StatusEffect Effect;
        public float ExpiresAt;
        public GameObject? Aura;
    }
}

/// <summary>Among Us has no HP — "damage" lands as a stun + knockback (and dummy HP in practice mode).</summary>
[RegisterInIl2Cpp]
public class DamageSystem : MonoBehaviour
{
    public DamageSystem(IntPtr ptr) : base(ptr) { }

    public static DamageSystem? Instance { get; private set; }
    public void Awake() => Instance = this;

    public void ApplyDamage(PlayerControl target, Vector2 from, float amount, DamageType type, float stun)
    {
        if (!Players.IsAlive(target)) return;
        if (StatusManager.Instance != null && !StatusManager.Instance.ApplyStatus(target, StatusEffect.Stunned, stun))
            return;   // shield absorbed it
        Fx.Burst(target.transform.position, new Color(1f, 1f, 1f, 1f), 1f, 0.2f);
        Players.Knockback(target, from, type == DamageType.Melee ? 0.8f : 1.4f);
        if (target == PlayerControl.LocalPlayer) Fx.Shake(0.25f, 0.35f);
        Practice.PracticeModeManager.Instance?.RecordDummyHit(target, amount);
    }
}

/// <summary>Loki fades out: invisible to others, ghostly to himself.</summary>
[RegisterInIl2Cpp]
public class InvisibilityManager : MonoBehaviour
{
    public InvisibilityManager(IntPtr ptr) : base(ptr) { }

    public static InvisibilityManager? Instance { get; private set; }
    private readonly Dictionary<byte, float> _until = new();
    public void Awake() => Instance = this;

    public void ApplyInvisibility(PlayerControl target, float duration)
    {
        _until[target.PlayerId] = Time.time + duration;
        if (target == PlayerControl.LocalPlayer) target.cosmetics.SetPhantomRoleAlpha(0.3f);
        else target.Visible = false;
    }

    public bool IsInvisible(PlayerControl pc) => _until.ContainsKey(pc.PlayerId);

    private static void Restore(PlayerControl pc)
    {
        if (pc == PlayerControl.LocalPlayer) pc.cosmetics.SetPhantomRoleAlpha(1f);
        else if (Players.IsAlive(pc) && !pc.inVent) pc.Visible = true;
        Fx.Smoke(pc.transform.position, Fx.LokiDark, 8, 0.8f);
    }

    public void RestoreAll()
    {
        foreach (var id in _until.Keys)
        {
            var pc = Players.ById(id);
            if (pc != null) Restore(pc);
        }
        _until.Clear();
    }

    public void Update()
    {
        if (_until.Count == 0) return;
        var done = new List<byte>();
        foreach (var kv in _until)
            if (Time.time >= kv.Value) done.Add(kv.Key);
        foreach (var id in done)
        {
            _until.Remove(id);
            var pc = Players.ById(id);
            if (pc != null) Restore(pc);
        }
    }
}

/// <summary>Spawns decoy dead bodies that look real but can't be reported.</summary>
public static class FakeBodies
{
    public static void Spawn(PlayerControl lookAlike, Vector2 pos, float duration)
    {
        var gm = GameManager.Instance;
        if (gm == null || gm.deadBodyPrefab == null || gm.deadBodyPrefab.Length == 0) return;
        var body = Object.Instantiate(gm.deadBodyPrefab[0]);
        body.transform.position = new Vector3(pos.x, pos.y, pos.y / 1000f);
        body.ParentId = lookAlike.PlayerId;
        body.Reported = true;                       // the report button ignores it
        if (body.myCollider != null) body.myCollider.enabled = false;

        int colorId = lookAlike.Data != null ? lookAlike.Data.DefaultOutfit.ColorId : 0;
        if (body.bodyRenderers != null)
            foreach (var r in body.bodyRenderers)
                if (r != null) PlayerMaterial.SetColors(colorId, r);

        Object.Destroy(body.gameObject, duration);
    }
}

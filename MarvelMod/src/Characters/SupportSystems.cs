using System;
using System.Collections.Generic;
using MarvelMod.Assets;
using MarvelMod.VFX;
using Reactor.Utilities.Attributes;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Characters;

public enum StatusEffect
{
    Bound, Stunned, Shielded, ChaosContract, Invisible,
    // Thanos / finishers
    Frozen, Slowed, Haste, Puppet, Pulled, Prison, Transmuted, Chained, Blinded, Rooted,
}
public enum DamageType   { Melee, Magical, Projectile }

/// <summary>
/// Timed status effects with visible auras. Movement effects are enforced on the affected
/// player's controlling client by <see cref="Patches.MovementStatusPatch"/>.
/// </summary>
[RegisterInIl2Cpp]
public class StatusManager : MonoBehaviour
{
    public StatusManager(IntPtr ptr) : base(ptr) { }

    public static StatusManager? Instance { get; private set; }
    private readonly Dictionary<byte, List<ActiveStatus>> _statuses = new();
    private bool _blindApplied;

    public void Awake()
    {
        Instance = this;
        Sfx.Init(gameObject);
    }

    public static bool IsHostile(StatusEffect e) => e is not (StatusEffect.Shielded or StatusEffect.Haste or StatusEffect.Invisible);
    public static bool IsRooting(StatusEffect e) => e is StatusEffect.Bound or StatusEffect.Stunned or StatusEffect.Frozen
        or StatusEffect.Prison or StatusEffect.Transmuted or StatusEffect.Chained or StatusEffect.Rooted;

    /// <summary>Applies an effect (point = pull centre / puppet goal). Hostile effects are absorbed by Mystic Shield.</summary>
    public bool ApplyStatus(PlayerControl target, StatusEffect effect, float duration, Vector2 point = default)
    {
        if (!Players.IsAlive(target)) return false;
        if (IsHostile(effect) && ConsumeShield(target)) return false;

        if (duration <= 0f) duration = 30f;
        if (!_statuses.TryGetValue(target.PlayerId, out var list))
            _statuses[target.PlayerId] = list = new List<ActiveStatus>();

        // refresh instead of stacking
        var existing = list.Find(s => s.Effect == effect);
        if (existing != null)
        {
            existing.ExpiresAt = Time.time + duration;
            existing.Point = point;
            if (existing.Aura != null) Object.Destroy(existing.Aura);
            existing.Aura = MakeAura(target, effect, duration);
            return true;
        }

        list.Add(new ActiveStatus
        {
            Effect = effect,
            ExpiresAt = Time.time + duration,
            Point = point,
            Aura = MakeAura(target, effect, duration),
        });
        OnBegin(target, effect);

        if (target == PlayerControl.LocalPlayer)
        {
            var msg = effect switch
            {
                StatusEffect.Bound         => ("BOUND!", Fx.LokiGold),
                StatusEffect.Stunned       => ("STUNNED!", Fx.LokiLight),
                StatusEffect.ChaosContract => ("CHAOS CONTRACT: YOUR CONTROLS ARE CURSED!", Fx.ChaosPurple),
                StatusEffect.Frozen        => ("TIME FROZEN!", new Color(0.6f, 1f, 0.7f)),
                StatusEffect.Prison        => ("TRAPPED IN SPACE!", new Color(0.4f, 0.7f, 1f)),
                StatusEffect.Transmuted    => ("YOU ARE A CRATE.", new Color(1f, 0.4f, 0.4f)),
                StatusEffect.Chained       => ("SOUL CHAINED!", new Color(1f, 0.6f, 0.2f)),
                StatusEffect.Puppet        => ("SOMETHING CONTROLS YOUR BODY...", new Color(1f, 0.9f, 0.3f)),
                StatusEffect.Blinded       => ("YOUR MEMORY FADES...", new Color(1f, 0.9f, 0.3f)),
                StatusEffect.Pulled        => ("PULLED IN!", new Color(0.4f, 0.7f, 1f)),
                _                          => ((string?)null, Color.white),
            };
            if (msg.Item1 != null) Fx.Toast(msg.Item1, msg.Item2, 1.8f, 3.4f);
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
        Sfx.Play(SfxKind.Chime, 1.5f, target.transform.position);
        if (target == PlayerControl.LocalPlayer) Fx.Toast("SHIELD BLOCKED IT!", Fx.LokiLight, 1.5f, 3.5f);
        return true;
    }

    private static GameObject? MakeAura(PlayerControl target, StatusEffect effect, float duration) => effect switch
    {
        StatusEffect.Bound         => Fx.Aura(target.transform, Sprites.Ring, new Color(1f, 0.8f, 0.2f, 0.95f), 1.2f, duration, 140f),
        StatusEffect.Stunned       => Fx.Aura(target.transform, Sprites.Star, new Color(1f, 1f, 0.6f, 0.95f), 0.45f, duration, 360f, 0.75f),
        StatusEffect.Shielded      => Fx.Aura(target.transform, Sprites.Glow, new Color(0.5f, 1f, 0.7f, 0.45f), 1.8f, duration),
        StatusEffect.ChaosContract => Fx.Aura(target.transform, Sprites.Ring, new Color(0.65f, 0.35f, 1f, 0.9f), 1.0f, duration, -200f),
        StatusEffect.Frozen        => Fx.Aura(target.transform, Sprites.Glow, new Color(0.6f, 1f, 0.75f, 0.6f), 1.5f, duration, 0f, 0f, false),
        StatusEffect.Slowed        => Fx.Aura(target.transform, Sprites.Ring, new Color(0.3f, 0.9f, 0.5f, 0.5f), 1.1f, duration, 40f),
        StatusEffect.Haste         => Fx.Aura(target.transform, Sprites.Glow, new Color(0.7f, 0.35f, 1f, 0.5f), 1.6f, duration),
        StatusEffect.Puppet        => Fx.Aura(target.transform, Sprites.Ring, new Color(1f, 0.9f, 0.3f, 0.9f), 1.0f, duration, 90f, 0.9f),
        StatusEffect.Pulled        => Fx.Aura(target.transform, Sprites.Ring, new Color(0.3f, 0.6f, 1f, 0.8f), 0.9f, duration, -300f),
        StatusEffect.Prison        => Fx.Aura(target.transform, Sprites.Box, new Color(0.4f, 0.75f, 1f, 0.95f), 1.5f, duration, 25f, 0.2f),
        StatusEffect.Chained       => Fx.Aura(target.transform, Sprites.Ring, new Color(1f, 0.55f, 0.15f, 0.95f), 1.25f, duration, -120f),
        StatusEffect.Transmuted    => Fx.Aura(target.transform, Sprites.Icon("prop_crate", 300f) ?? Sprites.Box, Color.white, 1f, duration, 0f, 0.1f, false),
        _                          => null,
    };

    /// <summary>Effects that change how a player is drawn.</summary>
    private static void OnBegin(PlayerControl pc, StatusEffect effect)
    {
        if (effect == StatusEffect.Transmuted) SetHidden(pc, true);
    }

    private static void OnEnd(PlayerControl pc, StatusEffect effect)
    {
        if (effect == StatusEffect.Transmuted)
        {
            SetHidden(pc, false);
            Fx.Smoke(pc.transform.position, new Color(1f, 0.3f, 0.3f), 8, 0.7f);
        }
    }

    /// <summary>Hide a player's body (others) / ghost them (self).</summary>
    public static void SetHidden(PlayerControl pc, bool hidden)
    {
        if (pc == null || pc.cosmetics == null) return;
        if (pc == PlayerControl.LocalPlayer) pc.cosmetics.SetPhantomRoleAlpha(hidden ? 0.15f : 1f);
        else if (hidden) pc.Visible = false;
        else if (Players.IsAlive(pc) && !pc.inVent) pc.Visible = true;
    }

    public bool HasStatus(PlayerControl target, StatusEffect effect)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return false;
        return list.Exists(s => s.Effect == effect && Time.time < s.ExpiresAt);
    }

    public bool IsRooted(PlayerControl target)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return false;
        return list.Exists(s => IsRooting(s.Effect) && Time.time < s.ExpiresAt);
    }

    public Vector2? PointOf(PlayerControl target, StatusEffect effect)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return null;
        var s = list.Find(x => x.Effect == effect && Time.time < x.ExpiresAt);
        return s?.Point;
    }

    public void RemoveStatus(PlayerControl target, StatusEffect effect)
    {
        if (!_statuses.TryGetValue(target.PlayerId, out var list)) return;
        foreach (var s in list)
            if (s.Effect == effect)
            {
                if (s.Aura != null) Object.Destroy(s.Aura);
                OnEnd(target, effect);
            }
        list.RemoveAll(s => s.Effect == effect);
    }

    public void ClearAll()
    {
        foreach (var kv in _statuses)
        {
            var pc = Players.ById(kv.Key);
            foreach (var s in kv.Value)
            {
                if (s.Aura != null) Object.Destroy(s.Aura);
                if (pc != null) OnEnd(pc, s.Effect);
            }
        }
        _statuses.Clear();
        RestoreBlind();
    }

    public void Update()
    {
        float now = Time.time;
        foreach (var kv in _statuses)
        {
            PlayerControl? pc = null;
            foreach (var s in kv.Value)
            {
                if (now < s.ExpiresAt) continue;
                if (s.Aura != null) Object.Destroy(s.Aura);
                pc ??= Players.ById(kv.Key);
                if (pc != null) OnEnd(pc, s.Effect);
            }
            kv.Value.RemoveAll(s => now >= s.ExpiresAt);
        }
        UpdateBlind();
    }

    /// <summary>Memory Erasure: the blinded local player can't see anyone else.</summary>
    private void UpdateBlind()
    {
        var lp = PlayerControl.LocalPlayer;
        bool blind = lp != null && HasStatus(lp, StatusEffect.Blinded);
        if (blind)
        {
            foreach (var pc in Players.All())
                if (pc != lp && pc.Visible) pc.Visible = false;
            _blindApplied = true;
        }
        else if (_blindApplied) RestoreBlind();
    }

    private void RestoreBlind()
    {
        if (!_blindApplied) return;
        _blindApplied = false;
        foreach (var pc in Players.All())
            if (pc != PlayerControl.LocalPlayer && Players.IsAlive(pc) && !pc.inVent
                && !HasStatus(pc, StatusEffect.Transmuted)
                && (InvisibilityManager.Instance == null || !InvisibilityManager.Instance.IsInvisible(pc)))
                pc.Visible = true;
    }

    private class ActiveStatus
    {
        public StatusEffect Effect;
        public float ExpiresAt;
        public Vector2 Point;
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

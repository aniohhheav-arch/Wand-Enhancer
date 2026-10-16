using System;
using System.Collections.Generic;
using MarvelMod.Config;
using MarvelMod.VFX;
using Reactor.Utilities.Attributes;
using TMPro;
using UnityEngine;
using Object = UnityEngine.Object;
using Random = UnityEngine.Random;

namespace MarvelMod.Characters;

public enum IllusionBehavior { Idle, Wander, Follow, Task, Random }

/// <summary>Spawns and tracks illusions. Illusions are visual copies only — never real players.</summary>
[RegisterInIl2Cpp]
public class IllusionManager : MonoBehaviour
{
    public IllusionManager(IntPtr ptr) : base(ptr) { }

    public static IllusionManager? Instance { get; private set; }

    private readonly List<LokiIllusion> _active = new();

    public void Awake() => Instance = this;

    public LokiIllusion SpawnIllusion(PlayerControl template, Vector3 position)
    {
        var root = new GameObject($"LokiIllusion_{_active.Count}") { layer = template.gameObject.layer };
        root.transform.position = new Vector3(position.x, position.y, position.y / 1000f);
        var visual = CopyAppearance(template, root.transform);

        var ill = root.AddComponent<LokiIllusion>();
        ill.Init(template, visual, ModConfig.Data.IllusionLifetime);
        ill.SetBehavior(ModConfig.Data.IllusionBehavior switch
        {
            "follow" => IllusionBehavior.Follow,
            "task"   => IllusionBehavior.Task,
            "random" => IllusionBehavior.Random,
            "idle"   => IllusionBehavior.Idle,
            _        => IllusionBehavior.Wander,
        });
        _active.Add(ill);
        Fx.Smoke(position, Fx.LokiGreen, 6, 0.7f);
        return ill;
    }

    /// <summary>Copies the player's rendered cosmetics (body, hat, skin, visor, name) without any game scripts.</summary>
    private static Transform? CopyAppearance(PlayerControl template, Transform parent)
    {
        if (template.cosmetics == null) return null;
        var src = template.cosmetics.transform;

        // Instantiate under an inactive holder so none of the copied scripts ever run.
        var holder = new GameObject("IllusionHolder");
        holder.SetActive(false);
        var copy = Object.Instantiate(src.gameObject, holder.transform);
        UnityHelpers.StripScripts(copy);

        copy.transform.SetParent(parent, false);
        copy.transform.localPosition = src.localPosition;
        copy.transform.localScale = src.localScale;
        Object.Destroy(holder);

        int colorId = template.CurrentOutfit != null ? template.CurrentOutfit.ColorId : 0;
        foreach (var sr in copy.GetComponentsInChildren<SpriteRenderer>(true))
        {
            if (sr.sharedMaterial == null) continue;
            sr.material = new Material(sr.sharedMaterial);   // detach from the real player's material
            if (sr.material.HasProperty(PlayerMaterial.BodyColor))
                PlayerMaterial.SetColors(colorId, sr.material);
        }

        if (!ModConfig.Data.IllusionNameplates)
            foreach (var tmp in copy.GetComponentsInChildren<TMP_Text>(true))
                tmp.gameObject.SetActive(false);

        copy.SetActive(true);
        return copy.transform;
    }

    public void DespawnAll()
    {
        foreach (var ill in _active)
            if (ill != null) Destroy(ill.gameObject);
        _active.Clear();
    }

    public int ActiveCount => _active.Count;

    public void Update()
    {
        _active.RemoveAll(i => i == null);
    }
}

/// <summary>A single illusion: walks around, mirrors facing, pops when touched.</summary>
[RegisterInIl2Cpp]
public class LokiIllusion : MonoBehaviour
{
    public LokiIllusion(IntPtr ptr) : base(ptr) { }

    public float Lifetime { get; set; } = 30f;
    public IllusionBehavior Behavior { get; private set; } = IllusionBehavior.Wander;

    private const float TouchRadius = 0.45f;

    private Transform? _visual;
    private Vector3 _visualBase;
    private PlayerControl? _owner;
    private PlayerControl? _followTarget;
    private Vector2 _followOffset;
    private Vector2 _wanderTarget;
    private float _spawnTime;
    private float _behaviorTimer;
    private bool _popped;

    public void Init(PlayerControl template, Transform? visual, float lifetime)
    {
        _owner = template;
        _visual = visual;
        if (_visual != null) _visualBase = _visual.localPosition;
        Lifetime = lifetime;
        _spawnTime = Time.time;
        _wanderTarget = transform.position;
    }

    public void SetBehavior(IllusionBehavior b, PlayerControl? followTarget = null)
    {
        Behavior = b;
        _followTarget = followTarget;
        _followOffset = Random.insideUnitCircle.normalized * 0.8f;
        _behaviorTimer = 0f;
    }

    public void Update()
    {
        if (_popped) return;
        if (Time.time - _spawnTime > Lifetime || MeetingHud.Instance != null)
        {
            Pop();
            return;
        }

        float speed = ModConfig.Data.IllusionSpeed;
        Vector2 before = transform.position;
        switch (Behavior)
        {
            case IllusionBehavior.Wander:
            case IllusionBehavior.Task:   DoWander(Behavior == IllusionBehavior.Task ? speed * 0.6f : speed); break;
            case IllusionBehavior.Follow: DoFollow(speed); break;
            case IllusionBehavior.Random: DoRandom(speed); break;
        }
        Vector2 after = transform.position;
        Animate(after - before);
        CheckTouched();
    }

    private void MoveTowards(Vector2 target, float speed)
    {
        var p = Vector2.MoveTowards(transform.position, target, speed * Time.deltaTime);
        transform.position = new Vector3(p.x, p.y, p.y / 1000f);
    }

    private void DoWander(float speed)
    {
        _behaviorTimer -= Time.deltaTime;
        if (_behaviorTimer <= 0f || Vector2.Distance(transform.position, _wanderTarget) < 0.05f)
        {
            _wanderTarget = PickWanderPoint();
            _behaviorTimer = Random.Range(2f, 5f);
        }
        MoveTowards(_wanderTarget, speed);
    }

    private Vector2 PickWanderPoint()
    {
        Vector2 here = transform.position;
        for (int i = 0; i < 6; i++)
        {
            var p = here + Random.insideUnitCircle * 3f;
            if (!Players.WallBetween(here, p)) return p;
        }
        return here;   // boxed in: stand still for a bit
    }

    private void DoFollow(float speed)
    {
        if (_followTarget == null) { DoWander(speed); return; }
        var goal = Players.Pos(_followTarget) + _followOffset;
        if (Vector2.Distance(transform.position, goal) > 0.15f)
            MoveTowards(goal, Mathf.Max(speed, _followTarget.MyPhysics.Speed));
    }

    private void DoRandom(float speed)
    {
        _behaviorTimer -= Time.deltaTime;
        if (_behaviorTimer <= 0f)
        {
            Behavior = Random.value < 0.3f ? IllusionBehavior.Idle : IllusionBehavior.Wander;
            _behaviorTimer = Random.Range(3f, 8f);
        }
        if (Behavior == IllusionBehavior.Wander) DoWander(speed);
    }

    /// <summary>Face the walking direction and bob like a walking crewmate.</summary>
    private void Animate(Vector2 delta)
    {
        if (_visual == null) return;
        bool moving = delta.sqrMagnitude > 0.000001f;
        if (moving && Mathf.Abs(delta.x) > 0.0005f)
        {
            float sign = delta.x < 0 ? -1f : 1f;
            var s = _visual.localScale;
            _visual.localScale = new Vector3(Mathf.Abs(s.x) * sign, s.y, s.z);
            // keep the name text readable when flipped
            foreach (var tmp in _visual.GetComponentsInChildren<TMP_Text>())
            {
                var ts = tmp.transform.localScale;
                tmp.transform.localScale = new Vector3(Mathf.Abs(ts.x) * sign, ts.y, ts.z);
            }
        }
        float bob = moving ? Mathf.Abs(Mathf.Sin(Time.time * 14f)) * 0.06f : 0f;
        _visual.localPosition = _visualBase + new Vector3(0f, bob, 0f);
    }

    /// <summary>Illusions burst when a real player (other than Loki) walks into them.</summary>
    private void CheckTouched()
    {
        if (Time.time - _spawnTime < 1f) return;
        Vector2 here = transform.position;
        foreach (var pc in Players.All())
        {
            if (pc == _owner || pc == _followTarget || !Players.IsAlive(pc)) continue;
            if (Vector2.Distance(here, Players.Pos(pc)) < TouchRadius)
            {
                Pop();
                return;
            }
        }
    }

    public void Pop()
    {
        if (_popped) return;
        _popped = true;
        Fx.Smoke(transform.position, Fx.LokiGreen, 8, 0.8f);
        Fx.Sparkles(transform.position, Fx.LokiGold, 6, 0.8f, 0.5f);
        Destroy(gameObject);
    }
}

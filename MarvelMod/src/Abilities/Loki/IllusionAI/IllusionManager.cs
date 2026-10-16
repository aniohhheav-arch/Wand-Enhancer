using System;
using System.Collections.Generic;
using MarvelMod.Config;
using MarvelMod.VFX;
using Reactor.Utilities.Attributes;
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

    /// <summary>
    /// Builds a full player model using the game's PoolablePlayer (the one used in the intro and
    /// meeting screens), dressed in the template's current outfit and sized like a real player.
    /// </summary>
    private static PoolablePlayer? CopyAppearance(PlayerControl template, Transform parent)
    {
        var hud = HudManager.Instance;
        if (hud == null || hud.IntroPrefab == null || hud.IntroPrefab.PlayerPrefab == null) return null;
        var outfit = template.CurrentOutfit ?? template.Data?.DefaultOutfit;
        if (outfit == null) return null;

        var pp = Object.Instantiate(hud.IntroPrefab.PlayerPrefab, parent);
        pp.name = "IllusionModel";
        pp.transform.localPosition = Vector3.zero;
        pp.UpdateFromPlayerOutfit(outfit, PlayerMaterial.MaskType.None, false, false, null, false);
        pp.SetName(outfit.PlayerName);
        pp.ToggleName(ModConfig.Data.IllusionNameplates);
        SetLayer(pp.gameObject, template.gameObject.layer);

        // match a real player's on-screen height
        float want = BodyHeight(template.cosmetics);
        float have = BodyHeight(pp.cosmetics);
        if (want > 0.01f && have > 0.01f)
            pp.transform.localScale = pp.transform.localScale * (want / have);
        return pp;
    }

    private static float BodyHeight(CosmeticsLayer? cosmetics)
    {
        var body = cosmetics?.currentBodySprite?.BodySprite;
        if (body == null || body.sprite == null) return 0f;
        return body.sprite.bounds.size.y * Mathf.Abs(body.transform.lossyScale.y);
    }

    private static void SetLayer(GameObject go, int layer)
    {
        go.layer = layer;
        for (int i = 0; i < go.transform.childCount; i++)
            SetLayer(go.transform.GetChild(i).gameObject, layer);
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

    private PoolablePlayer? _model;
    private Vector3 _modelBase;
    private bool _flipped;
    private PlayerControl? _owner;
    private PlayerControl? _followTarget;
    private Vector2 _followOffset;
    private Vector2 _wanderTarget;
    private float _spawnTime;
    private float _behaviorTimer;
    private bool _popped;

    public void Init(PlayerControl template, PoolablePlayer? model, float lifetime)
    {
        _owner = template;
        _model = model;
        if (_model != null) _modelBase = _model.transform.localPosition;
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
        if (_model == null) return;
        bool moving = delta.sqrMagnitude > 0.000001f;
        if (moving && Mathf.Abs(delta.x) > 0.0005f)
        {
            bool flip = delta.x < 0;
            if (flip != _flipped)
            {
                _flipped = flip;
                _model.SetFlipX(flip);
            }
        }
        float bob = moving ? Mathf.Abs(Mathf.Sin(Time.time * 14f)) * 0.06f : 0f;
        float squash = moving ? 1f + Mathf.Sin(Time.time * 28f) * 0.03f : 1f;
        _model.transform.localPosition = _modelBase + new Vector3(0f, bob, 0f);
        transform.localScale = new Vector3(1f / squash, squash, 1f);
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

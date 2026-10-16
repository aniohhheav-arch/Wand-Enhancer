using Reactor.Utilities.Attributes;
using System;
using Random = UnityEngine.Random;
using System.Collections.Generic;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.Characters;

public enum IllusionBehavior { Idle, Wander, Follow, Task, Random }

/// <summary>Manages all active illusion instances. Never modifies real player state.</summary>
[RegisterInIl2Cpp]
public class IllusionManager : MonoBehaviour
{
    public IllusionManager(IntPtr ptr) : base(ptr) { }

    public static IllusionManager? Instance { get; private set; }

    private readonly List<LokiIllusion> _active = new();

    public void Awake() => Instance = this;

    public LokiIllusion SpawnIllusion(PlayerControl template, Vector3 position)
    {
        var go = new GameObject($"LokiIllusion_{_active.Count}");
        go.transform.position = position;
        var ill = go.AddComponent<LokiIllusion>();
        ill.Init(template, ModConfig.Data.IllusionLifetime);
        ill.SetBehavior(
            ModConfig.Data.IllusionBehavior switch
            {
                "wander"  => IllusionBehavior.Wander,
                "follow"  => IllusionBehavior.Follow,
                "task"    => IllusionBehavior.Task,
                "random"  => IllusionBehavior.Random,
                _         => IllusionBehavior.Idle,
            });
        _active.Add(ill);
        return ill;
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
        _active.RemoveAll(i => i == null || !i.gameObject.activeSelf);
    }
}

/// <summary>A single Loki illusion with its own AI profile.</summary>
[RegisterInIl2Cpp]
public class LokiIllusion : MonoBehaviour
{
    public LokiIllusion(IntPtr ptr) : base(ptr) { }

    public float Lifetime { get; set; } = 30f;
    public IllusionBehavior Behavior { get; private set; } = IllusionBehavior.Wander;
    
    // Never exposes a real PlayerControl reference that could be mistaken for a real player
    private string _templateName = "Loki";
    private float  _spawnTime;
    private PlayerControl? _followTarget;
    private Vector3 _wanderTarget;
    private float _behaviorTimer;

    public void Init(PlayerControl template, float lifetime)
    {
        _templateName = template.Data?.PlayerName ?? "Loki";
        Lifetime      = lifetime;
        _spawnTime    = Time.realtimeSinceStartup;
        _wanderTarget = transform.position;
    }

    public void SetBehavior(IllusionBehavior b, PlayerControl? followTarget = null)
    {
        Behavior       = b;
        _followTarget  = followTarget;
        _behaviorTimer = 0f;
    }

    public void Update()
    {
        if (Time.realtimeSinceStartup - _spawnTime > Lifetime)
        {
            Destroy(gameObject);
            return;
        }

        float speed = ModConfig.Data.IllusionSpeed;

        switch (Behavior)
        {
            case IllusionBehavior.Wander:
                DoWander(speed);
                break;
            case IllusionBehavior.Follow:
                DoFollow(speed);
                break;
            case IllusionBehavior.Task:
                DoTask(speed);
                break;
            case IllusionBehavior.Random:
                DoRandom(speed);
                break;
            // Idle: stay put
        }
    }

    private void DoWander(float speed)
    {
        _behaviorTimer -= Time.deltaTime;
        if (_behaviorTimer <= 0f)
        {
            _wanderTarget = transform.position + new Vector3(
                Random.Range(-4f, 4f), Random.Range(-4f, 4f), 0);
            _behaviorTimer = Random.Range(2f, 5f);
        }
        transform.position = Vector3.MoveTowards(transform.position, _wanderTarget, speed * Time.deltaTime);
    }

    private void DoFollow(float speed)
    {
        if (_followTarget == null) return;
        transform.position = Vector3.MoveTowards(
            transform.position, _followTarget.transform.position, speed * Time.deltaTime);
    }

    private void DoTask(float speed)
    {
        // Walk to a random map task point, pause, then pick another
        DoWander(speed * 0.6f);
    }

    private void DoRandom(float speed)
    {
        _behaviorTimer -= Time.deltaTime;
        if (_behaviorTimer <= 0f)
        {
            Behavior = (IllusionBehavior)Random.Range(0, 3); // Idle/Wander/Follow variants
            _behaviorTimer = Random.Range(3f, 8f);
        }
        DoWander(speed);
    }
}

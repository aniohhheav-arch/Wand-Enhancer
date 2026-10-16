using Reactor.Utilities.Attributes;
using System;
using System.Collections.Generic;
using MarvelMod.Characters;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.Practice;

/// <summary>
/// Sandbox manager for Practice Mode.
/// Spawns dummy crewmates, logs ability results, tracks dummy HP.
/// </summary>
[RegisterInIl2Cpp]
public class PracticeModeManager : MonoBehaviour
{
    public PracticeModeManager(IntPtr ptr) : base(ptr) { }

    public static PracticeModeManager? Instance { get; private set; }

    private readonly List<DummyCrewmate> _dummies    = new();
    private readonly List<string>        _testLog    = new();
    public  IReadOnlyList<string>        TestLogLines => _testLog;

    public void Awake() => Instance = this;

    // ── Dummy management ──────────────────────────────────────────────────────

    public void SpawnDummies(int count)
    {
        for (int i = 0; i < count; i++)
        {
            var go = new GameObject($"Dummy_{_dummies.Count}");
            go.transform.position = MapSpawnPoints.GetRandom(1)[0] + Vector3.right * i;
            var dummy = go.AddComponent<DummyCrewmate>();
            dummy.Init($"Dummy {_dummies.Count + 1}");
            _dummies.Add(dummy);
        }
        TestLog($"Spawned {count} dummies. Total: {_dummies.Count}");
    }

    public void ClearDummies()
    {
        foreach (var d in _dummies)
            if (d != null) Destroy(d.gameObject);
        _dummies.Clear();
        TestLog("Cleared all dummies.");
    }

    public void SetDummyCount(int count)
    {
        ClearDummies();
        SpawnDummies(count);
    }

    public List<DummyCrewmate> GetDummies() => _dummies;

    public void RecordDummyHit(PlayerControl target, float damage)
    {
        var dummy = _dummies.Find(d => d.name == target.name);
        if (dummy != null)
        {
            dummy.HP = Mathf.Max(0, dummy.HP - damage);
            TestLog($"[Damage] {target.Data?.PlayerName} took {damage} → HP: {dummy.HP}");
        }
    }

    // ── Test log ──────────────────────────────────────────────────────────────

    public void TestLog(string msg)
    {
        var entry = $"[{System.DateTime.Now:HH:mm:ss}] {msg}";
        _testLog.Add(entry);
        if (_testLog.Count > 200) _testLog.RemoveAt(0);
        BepInEx.Logging.Logger.CreateLogSource("MarvelMod.Practice").LogInfo(msg);
    }

    public void ResetLog() => _testLog.Clear();

    // ── Quick reset ───────────────────────────────────────────────────────────

    public void ResetSandbox()
    {
        ClearDummies();
        ResetLog();
        IllusionManager.Instance?.DespawnAll();
        LokiCharacter.Instance.ResetAllCooldowns();
        SpawnDummies(ModConfig.Data.PracticeDummies);
        TestLog("Sandbox reset.");
    }
}

[RegisterInIl2Cpp]
public class DummyCrewmate : MonoBehaviour
{
    public DummyCrewmate(IntPtr ptr) : base(ptr) { }

    public string DummyName { get; private set; } = "Dummy";
    public float  HP        { get; set; }         = 100f;
    public bool   Invincible{ get; set; }         = false;

    public void Init(string name)
    {
        DummyName = name;
        HP        = 100f;
    }
}

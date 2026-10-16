using System.Collections.Generic;
using MarvelMod.Abilities;
using MarvelMod.Abilities.Thanos;
using MarvelMod.Assets;
using MarvelMod.Config;
using MarvelMod.UI.HUD;
using MarvelMod.VFX;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.Characters;

public enum Stone { Power, Space, Reality, Soul, Time, Mind, Infinity }

public static class Stones
{
    public static readonly Stone[] Six = { Stone.Power, Stone.Space, Stone.Reality, Stone.Soul, Stone.Time, Stone.Mind };

    public static Color Color(Stone s) => s switch
    {
        Stone.Power   => new Color(0.65f, 0.3f, 1f, 1f),
        Stone.Space   => new Color(0.25f, 0.55f, 1f, 1f),
        Stone.Reality => new Color(1f, 0.15f, 0.2f, 1f),
        Stone.Soul    => new Color(1f, 0.55f, 0.1f, 1f),
        Stone.Time    => new Color(0.2f, 0.9f, 0.4f, 1f),
        Stone.Mind    => new Color(1f, 0.87f, 0.15f, 1f),
        _             => new Color(1f, 0.85f, 0.4f, 1f),
    };

    public static (SfxKind kind, float pitch) Sound(Stone s) => s switch
    {
        Stone.Power   => (SfxKind.Boom, 1f),
        Stone.Space   => (SfxKind.Whoosh, 1.2f),
        Stone.Reality => (SfxKind.Zap, 0.8f),
        Stone.Soul    => (SfxKind.Hum, 1.3f),
        Stone.Time    => (SfxKind.Chime, 1f),
        Stone.Mind    => (SfxKind.Chime, 1.5f),
        _             => (SfxKind.Swell, 1f),
    };
}

/// <summary>
/// Thanos: six Infinity Stone tabs plus the secret INFINITY tab (all six collected).
/// Freeplay/lobby: every stone. Matches: start with ThanosStartStones, one more every
/// StoneUnlockSeconds and one per kill. A gauntlet above the buttons shows collected stones.
/// </summary>
public class ThanosCharacter : CharacterDef
{
    public static readonly ThanosCharacter Instance = new();

    public override string Id => "thanos";
    public override string DisplayName => "Thanos";
    public override Color Color => new(0.6f, 0.4f, 0.85f, 1f);

    private readonly bool[] _owned = new bool[6];
    private float _nextUnlock;
    private readonly List<(float t, Vector2 p)> _history = new();
    private float _lastSample;

    private GameObject? _gauntlet;
    private readonly SpriteRenderer?[] _gems = new SpriteRenderer?[6];
    private readonly float[] _pulse = new float[6];

    private readonly List<AbilityTab> _tabs;
    public override IReadOnlyList<AbilityTab> Tabs => _tabs;
    public override IEnumerable<AbilityBase> Hidden => new AbilityBase[] { LokiCharacter.Instance.MapTeleport };

    private ThanosCharacter()
    {
        AbilityTab Tab(string name, Stone s, List<AbilityBase> list) =>
            new(name, $"stone_{s.ToString().ToLowerInvariant()}", list, _ => Has(s));

        _tabs = new List<AbilityTab>
        {
            Tab("POWER",   Stone.Power,   new() { new PowerBlast(), new CosmicBeam(), new Shockwave(), new PowerOverload(), new Planetbreaker() }),
            Tab("SPACE",   Stone.Space,   new() { new SpaceBlink(), new SpaceRift(), new GravityWell(), new SpatialPrison(), new CosmicExile() }),
            Tab("REALITY", Stone.Reality, new() { new RealityWarp(), new Transmutation(), new RealityShatter(), new FalseWorld(), new RealityCollapse() }),
            Tab("SOUL",    Stone.Soul,    new() { new SoulSight(), new SoulDrain(), new SpiritProjection(), new SoulChains(), new SoulWorld() }),
            Tab("TIME",    Stone.Time,    new() { new TimeFreeze(), new Rewind(), new TemporalEcho(), new AgeManipulation(), new EternalLoop() }),
            Tab("MIND",    Stone.Mind,    new() { new ThanosMindControl(), new PsychicPulse(), new MemoryErasure(), new IllusionArmy(), new AbsoluteDomination() }),
            new("INFINITY", "gauntlet", new() { new InfinitySurge(), new TheSnap(), new TitansJudgment() }, _ => HasAll),
        };
    }

    public bool Has(Stone s) => s == Stone.Infinity ? HasAll : _owned[(int)s];
    public bool HasAll
    {
        get
        {
            foreach (var o in _owned) if (!o) return false;
            return true;
        }
    }
    public int Count
    {
        get
        {
            int n = 0;
            foreach (var o in _owned) if (o) n++;
            return n;
        }
    }

    public override void OnLocalAssigned(PlayerControl lp)
    {
        for (int i = 0; i < 6; i++) _owned[i] = false;
        if (CharacterRegistry.IsFreeplay || CharacterRegistry.InLobby)
        {
            for (int i = 0; i < 6; i++) _owned[i] = true;
        }
        else
        {
            _owned[(int)Stone.Power] = true;
            int start = Mathf.Clamp(ModConfig.Data.ThanosStartStones, 1, 6);
            while (Count < start) _owned[Random.Range(0, 6)] = true;
        }
        _nextUnlock = Time.time + ModConfig.Data.StoneUnlockSeconds;
        _history.Clear();
    }

    public override void OnLocalUnassigned()
    {
        if (_gauntlet != null) Object.Destroy(_gauntlet);
        _gauntlet = null;
    }

    /// <summary>A random missing stone (kills, timer).</summary>
    public void GrantRandomStone()
    {
        var missing = new List<int>();
        for (int i = 0; i < 6; i++) if (!_owned[i]) missing.Add(i);
        if (missing.Count == 0) return;
        int idx = missing[Random.Range(0, missing.Count)];
        _owned[idx] = true;
        _pulse[idx] = 1f;
        var s = (Stone)idx;
        Fx.Toast($"{s.ToString().ToUpperInvariant()} STONE ACQUIRED", Stones.Color(s), 2.5f, 4.5f, 1.4f);
        Sfx.Play(SfxKind.Chime, 0.8f + idx * 0.1f);
        if (HasAll) Fx.Toast("ALL SIX STONES. THE INFINITY PAGE IS OPEN.", Stones.Color(Stone.Infinity), 3.5f, 4f, 0.8f);
    }

    /// <summary>Flash a stone on the gauntlet when one of its abilities is cast.</summary>
    public void Pulse(Stone s)
    {
        if (s == Stone.Infinity) for (int i = 0; i < 6; i++) _pulse[i] = 1f;
        else _pulse[(int)s] = 1f;
    }

    /// <summary>Where the local Thanos was `secondsAgo` seconds ago (Rewind / Temporal Echo).</summary>
    public Vector2 PositionAgo(float secondsAgo, Vector2 fallback)
    {
        float t = Time.time - secondsAgo;
        for (int i = 0; i < _history.Count; i++)
            if (_history[i].t >= t) return _history[i].p;
        return _history.Count > 0 ? _history[0].p : fallback;
    }

    public List<Vector2> PathSince(float secondsAgo)
    {
        float t = Time.time - secondsAgo;
        var path = new List<Vector2>();
        foreach (var h in _history) if (h.t >= t) path.Add(h.p);
        return path;
    }

    public override void LocalUpdate(PlayerControl lp, HudManager hud)
    {
        // position history (10 s)
        if (Time.time - _lastSample > 0.1f)
        {
            _lastSample = Time.time;
            _history.Add((Time.time, lp.transform.position));
            while (_history.Count > 0 && Time.time - _history[0].t > 10f) _history.RemoveAt(0);
        }

        // stones over time in real games
        if (!CharacterRegistry.IsFreeplay && !CharacterRegistry.InLobby && !HasAll && MeetingHud.Instance == null
            && Time.time >= _nextUnlock)
        {
            _nextUnlock = Time.time + Mathf.Max(5f, ModConfig.Data.StoneUnlockSeconds);
            GrantRandomStone();
        }

        UpdateGauntlet(hud);
    }

    // socket centres in the 1024 px gauntlet art (see tools/make_thanos_icons.py)
    private static readonly Vector2[] Sockets =
    {
        new(762.8f, 413f),   // power (thumb)
        new(694.4f, 234.4f), // space
        new(572.8f, 234.4f), // reality
        new(451.2f, 234.4f), // soul
        new(512f, 489f),     // time (palm)
        new(329.6f, 234.4f), // mind
    };

    private void UpdateGauntlet(HudManager hud)
    {
        var anchor = AbilityButtons.SwitchButton;
        bool show = anchor != null && AbilityButtons.Visible;
        if (!show)
        {
            if (_gauntlet != null) _gauntlet.SetActive(false);
            return;
        }

        float size = AbilityButtons.Spacing * 1.05f;
        if (_gauntlet == null)
        {
            _gauntlet = new GameObject("MarvelGauntlet") { layer = hud.gameObject.layer };
            _gauntlet.transform.SetParent(hud.transform, false);
            var sr = _gauntlet.AddComponent<SpriteRenderer>();
            sr.sprite = Sprites.Icon("gauntlet_empty", 256f);
            for (int i = 0; i < 6; i++)
            {
                var g = new GameObject($"Gem{i}") { layer = hud.gameObject.layer };
                g.transform.SetParent(_gauntlet.transform, false);
                var p = Sockets[i];
                g.transform.localPosition = new Vector3(p.x / 1024f - 0.5f, 0.5f - p.y / 1024f, -0.01f);
                var gem = g.AddComponent<SpriteRenderer>();
                gem.sprite = Sprites.Glow;
                gem.sortingOrder = 1;
                _gems[i] = gem;
            }
        }
        _gauntlet.SetActive(true);
        var ap = anchor!.position;
        _gauntlet.transform.position = new Vector3(ap.x, ap.y + AbilityButtons.Spacing * 1.95f, ap.z);
        _gauntlet.transform.localScale = Vector3.one * size / Mathf.Max(0.001f, hud.transform.lossyScale.x);

        for (int i = 0; i < 6; i++)
        {
            var gem = _gems[i];
            if (gem == null) continue;
            _pulse[i] = Mathf.Max(0f, _pulse[i] - Time.deltaTime * 1.5f);
            bool owned = _owned[i];
            var c = Stones.Color((Stone)i);
            float glow = owned ? 0.85f + 0.15f * Mathf.Sin(Time.time * 3f + i) : 0.25f;
            gem.color = owned ? new Color(c.r, c.g, c.b, glow) : new Color(0.2f, 0.2f, 0.2f, 0.6f);
            gem.transform.localScale = Vector3.one * (i == 4 ? 0.2f : 0.13f) * (1f + _pulse[i] * 1.2f);
        }
    }
}

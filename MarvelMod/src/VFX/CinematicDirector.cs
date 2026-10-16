using System;
using System.Collections;
using MarvelMod.Characters;
using MarvelMod.Config;
using Reactor.Utilities.Attributes;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.VFX;

/// <summary>An invisible object the game camera follows during a cinematic.</summary>
[RegisterInIl2Cpp]
public class CameraPivot : MonoBehaviour
{
    public CameraPivot(IntPtr ptr) : base(ptr) { }
}

/// <summary>
/// Drives a finisher on every client. The killer and victim get the full camera choreography
/// (pans, orbits, zooms, cuts, letterbox); everyone else just sees the in-world animation.
/// Time is scaled so the whole sequence fits the configured duration.
/// </summary>
public sealed class CinematicDirector
{
    /// <summary>True while the local player is inside a cinematic (HUD buttons hide).</summary>
    public static bool LocalActive { get; private set; }

    public PlayerControl Killer { get; }
    public PlayerControl Victim { get; }
    /// <summary>The local player is the killer or the victim.</summary>
    public bool Participant { get; }
    /// <summary>Multiply nominal timings by this (config duration / nominal duration).</summary>
    public float TimeScale { get; }
    /// <summary>Preview mode: plays everything but nobody dies.</summary>
    public bool Preview { get; }

    private CameraPivot? _pivot;
    private Camera? _cam;
    private float _baseSize;
    private GameObject? _letterbox;
    private bool _ended;

    public CinematicDirector(PlayerControl killer, PlayerControl victim, float nominalSeconds, float configuredSeconds, bool preview)
    {
        Killer = killer;
        Victim = victim;
        Preview = preview;
        var lp = PlayerControl.LocalPlayer;
        Participant = lp != null && (lp == killer || lp == victim);
        TimeScale = configuredSeconds > 0.5f ? configuredSeconds / nominalSeconds : 1f;
        float total = nominalSeconds * TimeScale;

        // both actors are held in place for the whole sequence
        StatusManager.Instance?.ApplyStatus(victim, StatusEffect.Rooted, total + 0.5f);
        StatusManager.Instance?.ApplyStatus(killer, StatusEffect.Rooted, total + 0.5f);

        if (!Participant) return;
        var hud = HudManager.Instance;
        _cam = Camera.main;
        if (hud == null || _cam == null || hud.PlayerCam == null) return;
        LocalActive = true;
        _baseSize = _cam.orthographicSize;
        _pivot = new GameObject("MarvelCamPivot").AddComponent<CameraPivot>();
        _pivot.transform.position = victim.transform.position;
        hud.PlayerCam.SetTarget(_pivot);
        _letterbox = Fx.Letterbox(total + 0.3f);
    }

    /// <summary>Scaled wait. Also aborts (returns false through Alive) if a meeting starts.</summary>
    public IEnumerator Wait(float nominal)
    {
        float t = nominal * TimeScale;
        for (float e = 0; e < t; e += Time.deltaTime)
        {
            if (!Alive) yield break;
            yield return null;
        }
    }

    /// <summary>The sequence may continue (nobody left, no meeting).</summary>
    public bool Alive => !_ended && MeetingHud.Instance == null
                         && Killer != null && Victim != null && Players.IsAlive(Killer)
                         && (Preview || Players.IsAlive(Victim));

    // ── camera (participants only) ───────────────────────────────────────────

    public void Cut(Vector2 pos)
    {
        if (_pivot == null) return;
        _pivot.transform.position = pos;
        HudManager.Instance?.PlayerCam?.SnapToTarget();
    }

    public void Pan(Vector2 to, float nominal) => Run(CoPan(to, nominal * TimeScale));
    public void Zoom(float factor, float nominal) => Run(CoZoom(factor, nominal * TimeScale));
    public void Orbit(Vector2 center, float radius, float degrees, float nominal) => Run(CoOrbit(center, radius, degrees, nominal * TimeScale));

    private void Run(IEnumerator e) { if (_pivot != null) Reactor.Utilities.Coroutines.Start(e); }

    private IEnumerator CoPan(Vector2 to, float dur)
    {
        if (_pivot == null) yield break;
        Vector2 from = _pivot.transform.position;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (_pivot == null) yield break;
            float k = Mathf.SmoothStep(0f, 1f, t / dur);
            _pivot.transform.position = Vector2.Lerp(from, to, k);
            yield return null;
        }
        if (_pivot != null) _pivot.transform.position = to;
    }

    private IEnumerator CoZoom(float factor, float dur)
    {
        if (_cam == null) yield break;
        float from = _cam.orthographicSize, to = _baseSize * factor;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (_cam == null || _ended) yield break;
            _cam.orthographicSize = Mathf.Lerp(from, to, Mathf.SmoothStep(0f, 1f, t / dur));
            yield return null;
        }
        if (_cam != null && !_ended) _cam.orthographicSize = to;
    }

    private IEnumerator CoOrbit(Vector2 center, float radius, float degrees, float dur)
    {
        if (_pivot == null) yield break;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (_pivot == null) yield break;
            float a = (t / dur) * degrees * Mathf.Deg2Rad - Mathf.PI / 2f;
            _pivot.transform.position = center + new Vector2(Mathf.Cos(a), Mathf.Sin(a)) * radius;
            yield return null;
        }
    }

    // ── victim reactions ─────────────────────────────────────────────────────

    public void React(PlayerControl pc, string text, Color? color = null) =>
        Fx.WorldText(pc.transform, text, color ?? Color.white, 1.1f * TimeScale, 3.2f);

    public void TurnAround(PlayerControl pc)
    {
        if (pc.cosmetics != null) pc.cosmetics.SetFlipX(!pc.cosmetics.FlipX);
    }

    /// <summary>Offsets / tilts the visible body (lifting, marionette poses). Reset with Pose(pc, 0, 0).</summary>
    public static void Pose(PlayerControl pc, float lift, float tilt)
    {
        if (pc == null || pc.cosmetics == null) return;
        var t = pc.cosmetics.transform;
        t.localPosition = new Vector3(t.localPosition.x, BaseY(pc) + lift, t.localPosition.z);
        t.localRotation = Quaternion.Euler(0, 0, tilt);
    }

    private static readonly System.Collections.Generic.Dictionary<byte, float> _baseY = new();
    private static float BaseY(PlayerControl pc)
    {
        if (!_baseY.TryGetValue(pc.PlayerId, out var y))
            _baseY[pc.PlayerId] = y = pc.cosmetics.transform.localPosition.y;
        return y;
    }

    public void Fade(PlayerControl pc, float alpha)
    {
        if (pc != null && pc.cosmetics != null) pc.cosmetics.SetPhantomRoleAlpha(alpha);
    }

    /// <summary>Kills the victim (unless previewing). Body removal follows config.</summary>
    public void Finish()
    {
        if (Preview || !Alive) return;
        Kills.Kill(Killer, Victim, leaveBody: false);
    }

    /// <summary>Always called (even on abort): restores camera, bodies and statuses.</summary>
    public void End()
    {
        if (_ended) return;
        _ended = true;
        foreach (var pc in new[] { Killer, Victim })
        {
            if (pc == null) continue;
            Pose(pc, 0f, 0f);
            if (Players.IsAlive(pc)) Fade(pc, 1f);
            StatusManager.Instance?.RemoveStatus(pc, StatusEffect.Rooted);
        }
        if (_cam != null) _cam.orthographicSize = _baseSize > 0.1f ? _baseSize : _cam.orthographicSize;
        var hud = HudManager.Instance;
        var lp = PlayerControl.LocalPlayer;
        if (_pivot != null)
        {
            if (hud != null && hud.PlayerCam != null && lp != null) hud.PlayerCam.SetTarget(lp);
            Object.Destroy(_pivot.gameObject);
            _pivot = null;
        }
        if (_letterbox != null) Object.Destroy(_letterbox);
        if (Participant) LocalActive = false;
    }

    /// <summary>Runs a finisher body with guaranteed End().</summary>
    public static void Play(CinematicDirector director, Func<CinematicDirector, IEnumerator> body)
        => Kills.Finisher(Wrap(director, body(director)));

    private static IEnumerator Wrap(CinematicDirector d, IEnumerator inner)
    {
        try
        {
            while (d.Alive)
            {
                bool next;
                try { next = inner.MoveNext(); }
                catch (Exception e)
                {
                    BepInEx.Logging.Logger.CreateLogSource("MarvelMod.Cinematic").LogWarning($"Finisher aborted: {e.Message}");
                    yield break;
                }
                if (!next) yield break;
                yield return inner.Current;
            }
        }
        finally
        {
            d.End();
        }
    }
}

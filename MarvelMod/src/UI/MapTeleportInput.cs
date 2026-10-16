using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.UI;

/// <summary>
/// While the map is open, left-click anywhere on it to teleport there (no cooldown).
/// Abilities with MapPoints &gt; 0 open the map in "pick" mode instead: each click picks a point.
/// </summary>
public static class MapTeleportInput
{
    private static int _need;
    private static readonly System.Collections.Generic.List<Vector2> _picked = new();
    private static System.Action<System.Collections.Generic.List<Vector2>>? _done;

    public static bool Picking => _need > 0;

    /// <summary>Open the map and collect `count` clicked points, then call done.</summary>
    public static void BeginPick(int count, System.Action<System.Collections.Generic.List<Vector2>> done)
    {
        var hud = HudManager.Instance;
        if (hud == null || ShipStatus.Instance == null) { Fx.Toast("NO MAP HERE", Fx.AlarmRed, 1.5f, 3.5f); return; }
        _need = count;
        _picked.Clear();
        _done = done;
        if (MapBehaviour.Instance == null || !MapBehaviour.Instance.IsOpen)
            hud.ToggleMapVisible(new MapOptions { Mode = MapOptions.Modes.Normal });
        Fx.Toast($"CLICK {count} SPOTS ON THE MAP", Fx.LokiGold, 2.5f, 3.5f);
    }

    public static void Update()
    {
        var map = MapBehaviour.Instance;
        if (Picking && (map == null || !map.IsOpen)) { _need = 0; _done = null; return; }   // map closed: cancel
        if (map == null || !map.IsOpen || !Input.GetMouseButtonDown(0)) return;

        var lp = PlayerControl.LocalPlayer;
        var ship = ShipStatus.Instance;
        var cam = Camera.main;
        if (CharacterRegistry.Local == null || ship == null || cam == null || map.HerePoint == null) return;
        if (!Players.IsAlive(lp) || lp.inVent || MeetingHud.Instance != null) return;

        // The game draws "you are here" at playerPos / MapScale (x mirrored on flipped maps); invert that.
        var mapRoot = map.HerePoint.transform.parent;
        Vector2 local = mapRoot.InverseTransformPoint(cam.ScreenToWorldPoint(Input.mousePosition));
        float flip = Mathf.Sign(ship.transform.localScale.x);
        var dest = new Vector2(local.x * flip, local.y) * ship.MapScale;
        if (dest.magnitude > 80f) return;   // clicked outside the map image

        if (Picking)
        {
            _picked.Add(dest);
            Sfx.Play(SfxKind.Chime, 1f + _picked.Count * 0.2f);
            if (_picked.Count < _need)
            {
                Fx.Toast($"POINT {_picked.Count} SET - CLICK {_need - _picked.Count} MORE", Fx.LokiGold, 1.5f, 3.2f);
                return;
            }
            var done = _done;
            var pts = new System.Collections.Generic.List<Vector2>(_picked);
            _need = 0; _done = null;
            map.Close();
            done?.Invoke(pts);
            return;
        }

        // the map stays open so you can keep hopping around
        var teleport = LokiCharacter.Instance.MapTeleport;
        teleport.PendingDestination = dest;
        teleport.TryActivate(lp, null);
    }
}

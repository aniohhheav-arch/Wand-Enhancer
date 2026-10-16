using MarvelMod.Characters;
using UnityEngine;

namespace MarvelMod.UI;

/// <summary>While the map is open, left-click anywhere on it to teleport there (no cooldown).</summary>
public static class MapTeleportInput
{
    public static void Update()
    {
        var map = MapBehaviour.Instance;
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

        map.Close();
        var teleport = LokiCharacter.Instance.MapTeleport;
        teleport.PendingDestination = dest;
        teleport.TryActivate(lp, null);
    }
}

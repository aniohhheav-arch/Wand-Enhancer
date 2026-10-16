using MarvelMod.UI.Console;
using MarvelMod.UI.Index;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.UI;

/// <summary>Mouse wheel: scroll down to zoom out, scroll up to zoom back in (never closer than normal).</summary>
public static class ZoomInput
{
    private const float MaxZoomOut = 3.5f;   // times the normal view
    private static float _normal;

    public static void Update(HudManager hud)
    {
        var cam = Camera.main;
        if (cam == null) return;
        if (_normal <= 0.1f) _normal = cam.orthographicSize;
        if (CinematicDirector.LocalActive) return;   // finishers drive the camera themselves

        // Meetings / menus / typing: snap back to normal and ignore the wheel
        bool blocked = MeetingHud.Instance != null || ExileController.Instance != null || Minigame.Instance != null
                       || DevConsole.IsOpen || AbilityIndex.IsOpen
                       || (hud.Chat != null && hud.Chat.IsOpenOrOpening)
                       || (MapBehaviour.Instance != null && MapBehaviour.Instance.IsOpen);
        if (blocked)
        {
            if (MeetingHud.Instance != null && cam.orthographicSize > _normal) cam.orthographicSize = _normal;
            return;
        }

        float scroll = Input.mouseScrollDelta.y;
        if (Mathf.Abs(scroll) < 0.01f) return;
        float size = cam.orthographicSize * (scroll < 0f ? 1.15f : 1f / 1.15f);
        cam.orthographicSize = Mathf.Clamp(size, _normal, _normal * MaxZoomOut);
    }
}

using System;
using AmongUs.GameOptions;
using HarmonyLib;
using UnityEngine;
using Object = UnityEngine.Object;

namespace MarvelMod.UI;

/// <summary>
/// Opens the game's own Shapeshifter player-picker and reports which player was chosen,
/// instead of letting the game perform a real shapeshift.
/// </summary>
public static class TargetMenu
{
    private static ShapeshifterMinigame? _menu;
    private static Action<PlayerControl>? _onPick;

    public static bool IsOpen => _menu != null;

    public static bool Open(Action<PlayerControl> onPick)
    {
        if (Minigame.Instance != null || Camera.main == null || RoleManager.Instance == null) return false;
        var role = RoleManager.Instance.GetRole(RoleTypes.Shapeshifter)?.TryCast<ShapeshifterRole>();
        if (role == null || role.ShapeshifterMenu == null) return false;

        _menu = Object.Instantiate(role.ShapeshifterMenu, Camera.main.transform, false);
        _menu.transform.localPosition = new Vector3(0f, 0f, -50f);
        _onPick = onPick;
        _menu.Begin(null);
        return true;
    }

    /// <summary>Returns true if this was our menu (the vanilla shapeshift is then skipped).</summary>
    internal static bool TryHandle(ShapeshifterMinigame menu, PlayerControl target)
    {
        if (_menu == null || _onPick == null || menu.Pointer != _menu.Pointer) return false;
        var pick = _onPick;
        _onPick = null;
        _menu = null;
        menu.Close();
        if (target != null) pick(target);
        return true;
    }
}

[HarmonyPatch(typeof(ShapeshifterMinigame), nameof(ShapeshifterMinigame.Shapeshift))]
public static class ShapeshifterMenuPatch
{
    public static bool Prefix(ShapeshifterMinigame __instance, PlayerControl target) =>
        !TargetMenu.TryHandle(__instance, target);
}

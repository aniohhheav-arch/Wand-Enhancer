using HarmonyLib;
using MarvelMod.Characters;
using MarvelMod.UI;
using MarvelMod.UI.HUD;
using UnityEngine;

namespace MarvelMod.Patches;

/// <summary>Drives the ability buttons every frame the HUD is alive.</summary>
[HarmonyPatch(typeof(HudManager), nameof(HudManager.Update))]
public static class HudUpdatePatch
{
    public static void Postfix(HudManager __instance)
    {
        AbilityButtons.Update(__instance);
        MapTeleportInput.Update();
    }
}

/// <summary>Rooted (Bound / Stunned) and inverted (Chaos Contract) movement, enforced by the owning client.</summary>
[HarmonyPatch(typeof(PlayerPhysics), nameof(PlayerPhysics.FixedUpdate))]
public static class MovementStatusPatch
{
    public static void Postfix(PlayerPhysics __instance)
    {
        var status = StatusManager.Instance;
        if (status == null) return;
        var pc = __instance.myPlayer;
        if (pc == null || __instance.body == null || !Players.Controls(pc)) return;

        if (status.HasStatus(pc, StatusEffect.Bound) || status.HasStatus(pc, StatusEffect.Stunned))
            __instance.body.velocity = Vector2.zero;
        else if (status.HasStatus(pc, StatusEffect.ChaosContract))
            __instance.body.velocity = -__instance.body.velocity;
    }
}

/// <summary>Meetings wipe all Loki trickery so nobody is stuck disguised/rooted/invisible at the table.</summary>
[HarmonyPatch(typeof(MeetingHud), nameof(MeetingHud.Start))]
public static class MeetingCleanupPatch
{
    public static void Postfix()
    {
        IllusionManager.Instance?.DespawnAll();
        ShapeshiftManager.Instance?.RestoreAll();
        StatusManager.Instance?.ClearAll();
        InvisibilityManager.Instance?.RestoreAll();
        VFX.Fx.ClearStains();
    }
}

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
        ZoomInput.Update(__instance);
        Abilities.Loki.Possessions.Tick();
        Dimensions.Tick();
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

        var body = __instance.body;
        if (status.IsRooted(pc))
        {
            body.velocity = Vector2.zero;
            return;
        }

        Vector2 pos = pc.transform.position;
        float speed = __instance.Speed;
        var controller = status.SourceOf(pc, StatusEffect.Possessed);
        if (controller != null)   // Mind Control: mirror the controller's movement, keeping the same offset
        {
            var offset = status.PointOf(pc, StatusEffect.Possessed) ?? Vector2.zero;
            var goal = (Vector2)controller.transform.position + offset;
            var to = goal - pos;
            body.velocity = to.magnitude > 0.1f ? to.normalized * Mathf.Min(speed * 1.2f, to.magnitude * 8f) : Vector2.zero;
            return;
        }

        var puppetGoal = status.PointOf(pc, StatusEffect.Puppet);
        if (puppetGoal.HasValue)   // Mind Control: the body walks on its own
        {
            var to = puppetGoal.Value - pos;
            body.velocity = to.magnitude > 0.2f ? to.normalized * speed : Vector2.zero;
            return;
        }

        var v = body.velocity;
        if (status.HasStatus(pc, StatusEffect.ChaosContract)) v = -v;
        if (status.HasStatus(pc, StatusEffect.Slowed)) v *= 0.45f;
        if (status.HasStatus(pc, StatusEffect.Haste)) v *= 1.6f;
        var pull = status.PointOf(pc, StatusEffect.Pulled);
        if (pull.HasValue)   // Gravity Well
        {
            var to = pull.Value - pos;
            if (to.magnitude > 0.3f) v += to.normalized * speed * 0.9f;
        }
        body.velocity = v;
    }
}

/// <summary>Records real kills (for Soul Verdict) on every client.</summary>
[HarmonyPatch(typeof(PlayerControl), nameof(PlayerControl.MurderPlayer))]
public static class KillLogPatch
{
    public static void Postfix(PlayerControl __instance, PlayerControl target)
    {
        if (__instance != null && target != null && target.Data != null && target.Data.IsDead)
            Abilities.Meeting.KillLog.Record(__instance);
    }
}

/// <summary>Meetings wipe all Loki trickery so nobody is stuck disguised/rooted/invisible at the table.</summary>
[HarmonyPatch(typeof(MeetingHud), nameof(MeetingHud.Start))]
public static class MeetingCleanupPatch
{
    public static void Postfix()
    {
        Abilities.Meeting.MeetingAbility.NewMeeting();
        UI.MeetingPanel.Clear();
        Abilities.Loki.Possessions.EndAll();
        IllusionManager.Instance?.DespawnAll();
        ShapeshiftManager.Instance?.RestoreAll();
        StatusManager.Instance?.ClearAll();
        InvisibilityManager.Instance?.RestoreAll();
    }
}

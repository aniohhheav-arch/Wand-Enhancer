using System;
using System.Collections;
using MarvelMod.VFX;
using Reactor.Utilities;
using UnityEngine;

namespace MarvelMod.Characters;

/// <summary>
/// Real kills for the character abilities. Called on every client (like the game's own
/// murder RPC), so the death, body and kill animation happen everywhere.
/// </summary>
public static class Kills
{
    private static readonly BepInEx.Logging.ManualLogSource Log = BepInEx.Logging.Logger.CreateLogSource("MarvelMod.Kills");

    public static bool CanKill(PlayerControl? killer, PlayerControl? target) =>
        Players.IsAlive(killer) && Players.IsAlive(target) && killer != target
        && MeetingHud.Instance == null && LobbyBehaviour.Instance == null;

    /// <summary>
    /// Kills target. Mystic Shield absorbs it (unless checkShield is false because the caller
    /// already checked). leaveBody=false removes the corpse (finishers that "dissolve" the victim).
    /// </summary>
    public static bool Kill(PlayerControl killer, PlayerControl target, bool leaveBody = true, bool checkShield = true)
    {
        if (!CanKill(killer, target)) return false;
        if (checkShield && StatusManager.Instance != null && StatusManager.Instance.ConsumeShield(target)) return false;
        try
        {
            killer.MurderPlayer(target, MurderResultFlags.Succeeded | MurderResultFlags.DecisionByHost);
        }
        catch (Exception e)
        {
            Log.LogWarning($"MurderPlayer failed, using fallback death: {e.Message}");
            if (Players.IsAlive(target)) target.Die(DeathReason.Kill, false);
        }
        if (Players.IsAlive(target))
        {
            // the game refuses some kills (e.g. impostor on impostor): force the death and leave a real body
            Log.LogInfo($"Kill of {target.Data?.PlayerName} was refused by the game; forcing it");
            target.Die(DeathReason.Kill, false);
            if (leaveBody) SpawnBody(target);
        }
        if (!leaveBody && !Config.ModConfig.Data.FinisherLeavesBody) RemoveBody(target.PlayerId);
        // Thanos collects a stone for every kill he makes
        if (killer == PlayerControl.LocalPlayer && CharacterRegistry.Local == ThanosCharacter.Instance)
            ThanosCharacter.Instance.GrantRandomStone();
        return true;
    }

    private static void SpawnBody(PlayerControl victim)
    {
        var gm = GameManager.Instance;
        if (gm == null || gm.deadBodyPrefab == null || gm.deadBodyPrefab.Length == 0) return;
        var body = UnityEngine.Object.Instantiate(gm.deadBodyPrefab[0]);
        Vector2 p = victim.transform.position;
        body.transform.position = new Vector3(p.x, p.y, p.y / 1000f);
        body.ParentId = victim.PlayerId;
        int colorId = victim.Data != null ? victim.Data.DefaultOutfit.ColorId : 0;
        if (body.bodyRenderers != null)
            foreach (var r in body.bodyRenderers)
                if (r != null) PlayerMaterial.SetColors(colorId, r);
    }

    private static void RemoveBody(byte playerId)
    {
        foreach (var body in UnityEngine.Object.FindObjectsOfType<DeadBody>())
            if (body != null && body.ParentId == playerId) UnityEngine.Object.Destroy(body.gameObject);
    }

    /// <summary>Runs a finisher sequence on every client (wrapped so a missing player can't throw).</summary>
    public static void Finisher(IEnumerator sequence) => Coroutines.Start(Safe(sequence));

    /// <summary>
    /// Runs a sequence that may yield nested IEnumerators (sub-steps) itself, so it doesn't depend on
    /// the IL2CPP coroutine bridge supporting nesting. On error everything is disposed (finally blocks run).
    /// </summary>
    private static IEnumerator Safe(IEnumerator inner)
    {
        var stack = new System.Collections.Generic.Stack<IEnumerator>();
        stack.Push(inner);
        while (stack.Count > 0)
        {
            var top = stack.Peek();
            bool next;
            try
            {
                next = top.MoveNext();
            }
            catch (Exception e)
            {
                Log.LogWarning($"Finisher aborted: {e}");
                while (stack.Count > 0) (stack.Pop() as IDisposable)?.Dispose();
                yield break;
            }
            if (!next)
            {
                (stack.Pop() as IDisposable)?.Dispose();
                continue;
            }
            if (top.Current is IEnumerator sub)
            {
                stack.Push(sub);
                continue;
            }
            yield return top.Current;
        }
    }

    public static IEnumerator Wait(float seconds)
    {
        for (float t = 0; t < seconds; t += Time.deltaTime) yield return null;
    }
}

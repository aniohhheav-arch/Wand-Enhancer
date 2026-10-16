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

    /// <summary>Kills target with gore. Mystic Shield absorbs it. Returns true if the target died.</summary>
    public static bool Kill(PlayerControl killer, PlayerControl target, float gore = 1.5f, Vector2? sprayDir = null)
    {
        if (!CanKill(killer, target)) return false;
        if (StatusManager.Instance != null && StatusManager.Instance.ConsumeShield(target)) return false;

        Fx.BloodSplatter(target.transform.position, gore, sprayDir);
        Fx.Shake(0.35f, 0.6f);
        try
        {
            killer.MurderPlayer(target, MurderResultFlags.Succeeded | MurderResultFlags.DecisionByHost);
        }
        catch (Exception e)
        {
            Log.LogWarning($"MurderPlayer failed, using fallback death: {e.Message}");
            if (Players.IsAlive(target)) target.Die(DeathReason.Kill, false);
        }
        return true;
    }

    /// <summary>Runs a finisher sequence on every client (wrapped so a missing player can't throw).</summary>
    public static void Finisher(IEnumerator sequence) => Coroutines.Start(Safe(sequence));

    private static IEnumerator Safe(IEnumerator inner)
    {
        while (true)
        {
            object? current;
            try
            {
                if (!inner.MoveNext()) yield break;
                current = inner.Current;
            }
            catch (Exception e)
            {
                Log.LogWarning($"Finisher aborted: {e.Message}");
                yield break;
            }
            yield return current;
        }
    }

    public static IEnumerator Wait(float seconds)
    {
        for (float t = 0; t < seconds; t += Time.deltaTime) yield return null;
    }
}

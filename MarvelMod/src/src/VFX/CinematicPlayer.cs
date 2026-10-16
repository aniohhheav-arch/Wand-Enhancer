using System;
using System.Collections;
using MarvelMod.Config;
using UnityEngine;

namespace MarvelMod.VFX;

public enum CinematicType
{
    HallOfLoki, TheOtherYou, MasterOfMisdirection, AsgardianWrath, TheGrandDeception
}

public static class CinematicPlayer
{
    public static void PlayUltimate(CinematicType type, PlayerControl caster, Action onComplete)
    {
        var cfg = ModConfig.GetAbility(TypeToId(type));
        if (ModConfig.Data.CinematicSkip)
        {
            onComplete();
            return;
        }
        var host = UnityEngine.Object.FindObjectOfType<MonoBehaviour>();
        host?.StartCoroutine(RunCinematic(type, caster, cfg.CinematicDur, onComplete));
    }

    private static IEnumerator RunCinematic(CinematicType type, PlayerControl caster, float dur, Action onComplete)
    {
        // Freeze local input briefly
        caster.moveable = false;

        // Play entry VFX
        VfxPlayer.PlayLocal(VfxType.LokiUltimate, caster.transform.position);
        ScreenShake.Trigger(0.5f, 0.8f);

        // Play type-specific intro
        switch (type)
        {
            case CinematicType.HallOfLoki:
                ScreenTint.Apply(new Color(0.1f, 0.8f, 0.2f, 0.35f), dur);
                break;
            case CinematicType.AsgardianWrath:
                ScreenTint.Apply(new Color(0.2f, 1f, 0.3f, 0.4f), dur);
                ScreenShake.Trigger(0.8f, dur);
                break;
            case CinematicType.TheGrandDeception:
                ScreenTint.Apply(new Color(0f, 0.6f, 0f, 0.5f), dur);
                break;
        }

        yield return new WaitForSeconds(Mathf.Min(dur, 1.5f));

        // Run ability logic
        onComplete();

        yield return new WaitForSeconds(0.5f);
        caster.moveable = true;
    }

    private static string TypeToId(CinematicType type) => type switch
    {
        CinematicType.HallOfLoki           => "hall_of_loki",
        CinematicType.TheOtherYou          => "the_other_you",
        CinematicType.MasterOfMisdirection => "master_of_misdirection",
        CinematicType.AsgardianWrath       => "asgardian_wrath",
        CinematicType.TheGrandDeception    => "the_grand_deception",
        _                                  => "hall_of_loki",
    };
}

using System;
using System.Collections;
using MarvelMod.Config;
using Reactor.Utilities;
using UnityEngine;

namespace MarvelMod.VFX;

public enum CinematicType
{
    HallOfLoki, TheOtherYou, MasterOfMisdirection, AsgardianWrath, TheGrandDeception
}

/// <summary>Short wind-up for ultimates: title, tint, shake, then the effect fires.</summary>
public static class CinematicPlayer
{
    public static void PlayUltimate(CinematicType type, PlayerControl caster, Action onComplete)
    {
        var cfg = ModConfig.GetAbility(TypeToId(type));
        if (ModConfig.Data.CinematicSkip || cfg.CinematicDur <= 0f)
        {
            onComplete();
            return;
        }
        Coroutines.Start(RunCinematic(type, caster, cfg.CinematicDur, onComplete));
    }

    private static IEnumerator RunCinematic(CinematicType type, PlayerControl caster, float dur, Action onComplete)
    {
        bool mine = caster.AmOwner;
        if (mine) caster.moveable = false;

        Fx.Toast(Title(type), Fx.LokiGold, dur + 0.8f, 6f, 1.8f);
        Fx.Shake(dur, 0.25f);
        Fx.ScreenTint(type == CinematicType.AsgardianWrath
            ? new Color(0.2f, 1f, 0.3f, 0.35f)
            : new Color(0.1f, 0.75f, 0.25f, 0.3f), dur + 0.4f);

        // Charging swirl around the caster
        float spark = 0f;
        for (float t = 0; t < dur; t += Time.deltaTime)
        {
            if (caster == null) yield break;
            spark += Time.deltaTime;
            if (spark > 0.25f)
            {
                spark = 0f;
                Fx.Sparkles(caster.transform.position, Fx.LokiGold, 4, 1.4f, 0.5f);
            }
            yield return null;
        }

        if (caster == null) yield break;
        VfxPlayer.PlayLocal(VfxType.LokiUltimate, caster.transform.position);
        try { onComplete(); }
        finally { if (mine && caster != null) caster.moveable = true; }
    }

    private static string Title(CinematicType type) => type switch
    {
        CinematicType.HallOfLoki           => "HALL OF LOKI",
        CinematicType.TheOtherYou          => "THE OTHER YOU",
        CinematicType.MasterOfMisdirection => "MASTER OF MISDIRECTION",
        CinematicType.AsgardianWrath       => "ASGARDIAN WRATH",
        CinematicType.TheGrandDeception    => "THE GRAND DECEPTION",
        _                                  => "LOKI",
    };

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

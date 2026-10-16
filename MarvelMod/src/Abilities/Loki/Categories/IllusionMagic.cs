using System.Collections;
using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── A: Illusion Magic ─────────────────────────────────────────────────────────

public class MirrorImage : AbilityBase
{
    public override string Id       => "mirror_image";
    public override string Name     => "Mirror Image";
    public override string Category => "Illusion Magic";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        var illusion = IllusionManager.Instance.SpawnIllusion(caster, caster.transform.position);
        illusion.SetBehavior(IllusionBehavior.Follow, caster);
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, caster.transform.position);
    }
}

public class PhantomSquad : AbilityBase
{
    public override string Id       => "phantom_squad";
    public override string Name     => "Phantom Squad";
    public override string Category => "Illusion Magic";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        int count = (int)Mathf.Min(Cfg.MaxTargets, 4);
        for (int i = 0; i < count; i++)
        {
            var offset = new Vector3(UnityEngine.Random.Range(-2f, 2f), UnityEngine.Random.Range(-2f, 2f), 0);
            var illusion = IllusionManager.Instance.SpawnIllusion(caster, caster.transform.position + offset);
            illusion.SetBehavior(IllusionBehavior.Wander);
        }
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, caster.transform.position);
    }
}

public class FalseEmergency : AbilityBase
{
    public override string Id       => "false_emergency";
    public override string Name     => "False Emergency";
    public override string Category => "Illusion Magic";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        // Displays a simulated sabotage notification to nearby targets.
        // Purely visual — does NOT trigger real sabotage or alter game state.
        VfxPlayer.PlayLocal(VfxType.LokiMagic, caster?.transform.position ?? Vector3.zero);
        HudMessageBroadcaster.ShowFakeAlert(target, "⚠ Reactor meltdown!", Cfg.Duration);
    }
}

public class IllusionTrap : AbilityBase
{
    public override string Id       => "illusion_trap";
    public override string Name     => "Illusion Trap";
    public override string Category => "Illusion Magic";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (target == null) return;
        ScreenDistortion.Apply(target, DistortionType.Environmental, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
    }
}

public class HallOfLoki : AbilityBase
{
    public override string Id       => "hall_of_loki";
    public override string Name     => "Hall of Loki";
    public override string Category => "Illusion Magic";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        // Cinematic ultimate: fills map with Loki illusions
        CinematicPlayer.PlayUltimate(CinematicType.HallOfLoki, caster, () =>
        {
            int count = Cfg.MaxTargets;
            var spawnPoints = MapSpawnPoints.GetRandom(count);
            for (int i = 0; i < spawnPoints.Count; i++)
            {
                var ill = IllusionManager.Instance.SpawnIllusion(caster, spawnPoints[i]);
                ill.SetBehavior(IllusionBehavior.Random);
                ill.Lifetime = Cfg.Duration;
            }
        });
    }
}

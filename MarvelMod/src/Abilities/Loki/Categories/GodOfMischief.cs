using MarvelMod.Characters;
using MarvelMod.Config;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── E: God of Mischief ────────────────────────────────────────────────────────

public class FakeBody : AbilityBase
{
    public override string Id       => "fake_body";
    public override string Name     => "Fake Body";
    public override string Category => "God of Mischief";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        var ill = IllusionManager.Instance.SpawnIllusion(caster, caster.transform.position);
        ill.SetBehavior(IllusionBehavior.Idle);
        ill.Lifetime = Cfg.Duration;
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, caster.transform.position);
    }
}

public class MindWhisper : AbilityBase
{
    public override string Id       => "mind_whisper";
    public override string Name     => "Mind Whisper";
    public override string Category => "God of Mischief";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (target == null) return;
        // Simulated message — does NOT interact with real Among Us chat or RPC
        string msg = ModConfig.Data.DebugMode ? "[Loki whisper] Watch your back." : "[Loki whisper] You are not safe here.";
        HudMessageBroadcaster.ShowFakeAlert(target, msg, 4f);
        VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
    }
}

public class EnchantedWitness : AbilityBase
{
    public override string Id       => "enchanted_witness";
    public override string Name     => "Enchanted Witness";
    public override string Category => "God of Mischief";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        // Show target an illusion of a player at a random location
        var fakePos = MapSpawnPoints.GetRandom(1)[0];
        var nearbyPlayers = TargetFinder.GetPlayersInRadius(fakePos, 30f, 1);
        var witnessed = nearbyPlayers.Count > 0 ? nearbyPlayers[0] : caster;
        ScreenDistortion.ShowGhostPlayer(target, witnessed, fakePos, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
    }
}

public class ChaosContract : AbilityBase
{
    public override string Id       => "chaos_contract";
    public override string Name     => "Chaos Contract";
    public override string Category => "God of Mischief";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (target == null) return;
        StatusManager.Instance.ApplyStatus(target, StatusEffect.ChaosContract, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
    }
}

public class TheGrandDeception : AbilityBase
{
    public override string Id       => "the_grand_deception";
    public override string Name     => "The Grand Deception";
    public override string Category => "God of Mischief";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        CinematicPlayer.PlayUltimate(CinematicType.TheGrandDeception, caster, () =>
        {
            // Turn map green tint
            VfxPlayer.PlayGlobal(VfxType.LokiMapGreen, Cfg.Duration);
            // Spawn fake crewmates
            int count = Cfg.MaxTargets;
            var positions = MapSpawnPoints.GetRandom(count);
            for (int i = 0; i < count; i++)
            {
                var ill = IllusionManager.Instance.SpawnIllusion(caster, positions[i]);
                ill.SetBehavior(IllusionBehavior.Random);
                ill.Lifetime = Cfg.Duration;
            }
            // Simulate fake kill animation for nearby players (visual only — no real kill)
            var nearby = TargetFinder.GetPlayersInRadius(caster.transform.position, Cfg.Range, 3);
            foreach (var p in nearby)
                VfxPlayer.PlayLocal(VfxType.FakeKillFlash, p.transform.position);
        });
    }
}

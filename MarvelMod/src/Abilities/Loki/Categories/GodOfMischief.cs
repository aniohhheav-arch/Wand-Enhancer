using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── E: God of Mischief ────────────────────────────────────────────────────────

/// <summary>Drop a fake dead body that looks like you (can't be reported).</summary>
public class FakeBody : AbilityBase
{
    public override string Id       => "fake_body";
    public override string Name     => "Fake Body";
    public override string Category => "God of Mischief";

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiSpawn, ctx.CasterPos);
        FakeBodies.Spawn(ctx.Caster, ctx.CasterPos, ctx.Cfg.Duration);
    }
}

/// <summary>A creepy message appears on the target's screen.</summary>
public class MindWhisper : AbilityBase
{
    private static readonly string[] Lines =
    {
        "You are not safe here.",
        "I saw who vented...",
        "Trust no one. Especially green.",
        "Your crewmate is lying to you.",
        "Behind you.",
        "Kneel.",
    };

    public override string Id       => "mind_whisper";
    public override string Name     => "Mind Whisper";
    public override string Category => "God of Mischief";
    public override string Label    => "WHISPER";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        string line = Lines[ctx.Rng.Next(Lines.Length)];
        VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
        if (!ctx.LocalIs(target)) return;
        Fx.Toast($"<i>\"{line}\"</i>", Fx.LokiLight, ctx.Cfg.Duration, 4f, 1.2f);
        Fx.ScreenTint(new Color(0.1f, 0.6f, 0.2f, 0.2f), 1.2f);
    }
}

/// <summary>The target sees a ghost of another player somewhere it isn't.</summary>
public class EnchantedWitness : AbilityBase
{
    public override string Id       => "enchanted_witness";
    public override string Name     => "Enchanted Witness";
    public override string Category => "God of Mischief";
    public override string Label    => "FALSE SIGHT";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        VfxPlayer.PlayLocal(VfxType.LokiMagic, target.transform.position);
        if (!ctx.LocalIs(target)) return;

        // pick someone other than the victim to "witness"
        PlayerControl witnessed = ctx.Caster;
        foreach (var pc in Players.All())
            if (pc != target && Players.IsAlive(pc) && ctx.Rng.Next(3) == 0) { witnessed = pc; break; }

        var spot = MapSpawnPoints.NearbyOpenPoint(Players.Pos(target), ctx.Rng, 1.5f, 3f);
        var ill = IllusionManager.Instance!.SpawnIllusion(witnessed, spot);
        ill.SetBehavior(IllusionBehavior.Wander);
        ill.Lifetime = ctx.Cfg.Duration;
    }
}

/// <summary>The target's movement controls are inverted.</summary>
public class ChaosContract : AbilityBase
{
    public override string Id       => "chaos_contract";
    public override string Name     => "Chaos Contract";
    public override string Category => "God of Mischief";
    public override string Label    => "CHAOS";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Fx.Projectile(ctx.CasterPos, Players.Pos(target), Fx.ChaosPurple, 12f, () =>
        {
            Fx.Sparkles(target.transform.position, Fx.ChaosPurple, 12, 1.2f);
            StatusManager.Instance?.ApplyStatus(target, StatusEffect.ChaosContract, ctx.Cfg.Duration);
        });
    }
}

/// <summary>Ultimate: green world, fake crewmates everywhere, nearby players see a fake kill.</summary>
public class TheGrandDeception : AbilityBase
{
    public override string Id       => "the_grand_deception";
    public override string Name     => "The Grand Deception";
    public override string Category => "God of Mischief";
    public override string Label    => "DECEPTION";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        CinematicPlayer.PlayUltimate(CinematicType.TheGrandDeception, ctx.Caster, () =>
        {
            VfxPlayer.PlayGlobal(VfxType.LokiMapGreen, ctx.Cfg.Duration);

            // fake crewmates wearing other players' looks
            var templates = new System.Collections.Generic.List<PlayerControl>();
            foreach (var pc in Players.All())
                if (Players.IsAlive(pc)) templates.Add(pc);
            var spots = MapSpawnPoints.GetRandom(ctx.Cfg.MaxTargets, ctx.Rng, ctx.CasterPos);
            foreach (var spot in spots)
            {
                var look = templates.Count > 0 ? templates[ctx.Rng.Next(templates.Count)] : ctx.Caster;
                var ill = IllusionManager.Instance!.SpawnIllusion(look, spot);
                ill.SetBehavior(IllusionBehavior.Random);
                ill.Lifetime = ctx.Cfg.Duration;
            }

            // players close to Loki see a fake kill flash (visual only)
            var me = PlayerControl.LocalPlayer;
            if (me != null && me != ctx.Caster && Players.IsAlive(me)
                && Vector2.Distance(Players.Pos(me), ctx.Caster.transform.position) <= ctx.Cfg.Range)
            {
                VfxPlayer.PlayLocal(VfxType.FakeKillFlash, me.transform.position);
                var victim = templates.Find(p => p != me && p != ctx.Caster) ?? ctx.Caster;
                FakeBodies.Spawn(victim, Players.Pos(me) + new Vector2(0.7f, -0.2f), 3f);
            }
        });
    }
}

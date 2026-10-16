using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── D: Sorcery & Combat ───────────────────────────────────────────────────────

public class GreenBolt : AbilityBase
{
    public override string Id       => "green_bolt";
    public override string Name     => "Green Bolt";
    public override string Category => "Sorcery & Combat";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        var dir = target != null
            ? ((Vector2)(target.transform.position - caster.transform.position)).normalized
            : Vector2.right;
        ProjectileManager.Instance.FireProjectile(
            caster, dir, Cfg.Range, Cfg.Damage, ProjectileType.GreenBolt);
        VfxPlayer.PlayLocal(VfxType.LokiCast, caster.transform.position);
    }
}

public class BindingRibbons : AbilityBase
{
    public override string Id       => "binding_ribbons";
    public override string Name     => "Binding Ribbons";
    public override string Category => "Sorcery & Combat";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (target == null) return;
        StatusManager.Instance.ApplyStatus(target, StatusEffect.Bound, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiRibbons, target.transform.position);
    }
}

public class MysticShield : AbilityBase
{
    public override string Id       => "mystic_shield";
    public override string Name     => "Mystic Shield";
    public override string Category => "Sorcery & Combat";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        StatusManager.Instance.ApplyStatus(caster, StatusEffect.Shielded, 0 /* until hit */);
        VfxPlayer.PlayLocal(VfxType.LokiShield, caster.transform.position);
    }
}

public class EnchantedDagger : AbilityBase
{
    public override string Id       => "enchanted_dagger";
    public override string Name     => "Enchanted Dagger";
    public override string Category => "Sorcery & Combat";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        float dist = Vector2.Distance(caster.transform.position, target.transform.position);
        if (dist > Cfg.Range) { VfxPlayer.PlayLocal(VfxType.LokiMiss, caster.transform.position); return; }
        DamageSystem.Instance.ApplyDamage(target, Cfg.Damage, DamageType.Melee);
        VfxPlayer.PlayLocal(VfxType.LokiDagger, target.transform.position);
    }
}

public class AsgardianWrath : AbilityBase
{
    public override string Id       => "asgardian_wrath";
    public override string Name     => "Asgardian Wrath";
    public override string Category => "Sorcery & Combat";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        CinematicPlayer.PlayUltimate(CinematicType.AsgardianWrath, caster, () =>
        {
            // Summon spectral daggers storm in radius
            var nearby = TargetFinder.GetPlayersInRadius(caster.transform.position, Cfg.Range, Cfg.MaxTargets);
            foreach (var p in nearby)
            {
                DamageSystem.Instance.ApplyDamage(p, Cfg.Damage, DamageType.Magical);
                StatusManager.Instance.ApplyStatus(p, StatusEffect.Stunned, 2f);
                VfxPlayer.PlayLocal(VfxType.LokiDaggerStorm, p.transform.position);
            }
            VfxPlayer.PlayLocal(VfxType.LokiUltimate, caster.transform.position);
        });
    }
}

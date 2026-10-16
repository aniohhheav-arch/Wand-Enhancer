using MarvelMod.Assets;
using MarvelMod.Characters;
using MarvelMod.UI.HUD;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── D: Sorcery & Combat ───────────────────────────────────────────────────────
// Among Us has no HP: hits stun and knock back instead (and are absorbed by Mystic Shield).

/// <summary>Magic bolt: auto-aims at the closest player, otherwise flies where you're moving.</summary>
public class GreenBolt : AbilityBase
{
    public override string Id       => "green_bolt";
    public override string Name     => "Green Bolt";
    public override string Category => "Sorcery & Combat";
    public override string Label    => "BOLT";
    public override TargetMode Targeting => TargetMode.Optional;

    protected override void Prepare(PlayerControl caster, ref PlayerControl? target, ref Vector2 point)
    {
        Vector2 origin = caster.transform.position;
        var dir = target != null ? (Players.Pos(target) - origin).normalized : AbilityButtons.LastMoveDirection;
        var end = Players.SafeDestination(origin, dir, Cfg.Range);

        // First living player along the bolt's path is hit.
        target = null;
        float best = Vector2.Distance(origin, end);
        foreach (var pc in Players.All())
        {
            if (pc == caster || !Players.IsAlive(pc) || pc.inVent) continue;
            var v = Players.Pos(pc) - origin;
            float along = Vector2.Dot(v, dir);
            if (along <= 0f || along > best) continue;
            if ((v - dir * along).magnitude > 0.5f) continue;
            target = pc;
            best = along;
        }
        point = target != null ? Players.Pos(target) : end;
    }

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        var origin = ctx.CasterPos;
        VfxPlayer.PlayLocal(VfxType.LokiCast, origin);
        Fx.Projectile(origin, ctx.Point, Fx.LokiGreen, 12f, () =>
        {
            if (target != null)
                DamageSystem.Instance?.ApplyDamage(target, origin, ctx.Cfg.Damage, DamageType.Projectile, ctx.Cfg.Duration);
        });
    }
}

/// <summary>Golden ribbons root the target in place.</summary>
public class BindingRibbons : AbilityBase
{
    public override string Id       => "binding_ribbons";
    public override string Name     => "Binding Ribbons";
    public override string Category => "Sorcery & Combat";
    public override string Label    => "BIND";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Fx.Projectile(ctx.CasterPos, Players.Pos(target), Fx.LokiGold, 14f, () =>
        {
            VfxPlayer.PlayLocal(VfxType.LokiRibbons, target.transform.position);
            StatusManager.Instance?.ApplyStatus(target, StatusEffect.Bound, ctx.Cfg.Duration);
        });
    }
}

/// <summary>Bubble that blocks the next bind / stun / curse.</summary>
public class MysticShield : AbilityBase
{
    public override string Id       => "mystic_shield";
    public override string Name     => "Mystic Shield";
    public override string Category => "Sorcery & Combat";
    public override string Label    => "SHIELD";

    protected override void OnCast(CastContext ctx)
    {
        VfxPlayer.PlayLocal(VfxType.LokiShield, ctx.CasterPos);
        StatusManager.Instance?.ApplyStatus(ctx.Caster, StatusEffect.Shielded, ctx.Cfg.Duration);
    }
}

/// <summary>Close-range stab: long stun + knockback.</summary>
public class EnchantedDagger : AbilityBase
{
    public override string Id       => "enchanted_dagger";
    public override string Name     => "Enchanted Dagger";
    public override string Category => "Sorcery & Combat";
    public override string Label    => "DAGGER";
    public override TargetMode Targeting => TargetMode.Required;

    protected override void OnCast(CastContext ctx)
    {
        var target = ctx.Target;
        if (target == null) return;
        Vector2 tp = target.transform.position;
        // slash arc between caster and target
        var mid = Vector2.Lerp(ctx.CasterPos, tp, 0.6f);
        var slash = Fx.MakeSprite(Sprites.Ring, mid, new Color(0.7f, 1f, 0.75f, 1f), 0.5f);
        Fx.Animate(slash, 0.25f, k =>
        {
            slash.transform.localScale = new Vector3(1.4f, 0.35f, 1f) * (0.5f + k);
            slash.transform.rotation = Quaternion.Euler(0, 0, -60f + 120f * k);
            slash.color = new Color(0.7f, 1f, 0.75f, 1f - k);
        });
        VfxPlayer.PlayLocal(VfxType.LokiDagger, tp);
        DamageSystem.Instance?.ApplyDamage(target, ctx.CasterPos, ctx.Cfg.Damage, DamageType.Melee, ctx.Cfg.Duration);
    }
}

/// <summary>Ultimate: daggers rain on everyone around Loki.</summary>
public class AsgardianWrath : AbilityBase
{
    public override string Id       => "asgardian_wrath";
    public override string Name     => "Asgardian Wrath";
    public override string Category => "Sorcery & Combat";
    public override string Label    => "WRATH";
    public override bool IsUltimate => true;

    protected override void OnCast(CastContext ctx)
    {
        CinematicPlayer.PlayUltimate(CinematicType.AsgardianWrath, ctx.Caster, () =>
        {
            Vector2 center = ctx.Caster.transform.position;
            Fx.Burst(center, Fx.LokiGreen, ctx.Cfg.Range * 2f, 0.6f);
            // ring of daggers flying outward
            for (int i = 0; i < 12; i++)
            {
                float a = i * Mathf.PI / 6f;
                var dir = new Vector2(Mathf.Cos(a), Mathf.Sin(a));
                Fx.Projectile(center, center + dir * ctx.Cfg.Range, Fx.LokiLight, 16f, null);
            }
            foreach (var p in Players.InRadius(center, ctx.Cfg.Range, ctx.Cfg.MaxTargets, ctx.Caster))
            {
                VfxPlayer.PlayLocal(VfxType.LokiDaggerStorm, p.transform.position);
                DamageSystem.Instance?.ApplyDamage(p, center, ctx.Cfg.Damage, DamageType.Magical, ctx.Cfg.Duration);
            }
        });
    }
}

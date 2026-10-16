using MarvelMod.Characters;
using MarvelMod.VFX;
using UnityEngine;

namespace MarvelMod.Abilities.Loki;

// ── C: Trickster Mobility ─────────────────────────────────────────────────────

public class MysticBlink : AbilityBase
{
    public override string Id       => "mystic_blink";
    public override string Name     => "Mystic Blink";
    public override string Category => "Trickster Mobility";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        var dir = new Vector2(Input.GetAxisRaw("Horizontal"), Input.GetAxisRaw("Vertical")).normalized;
        if (dir == Vector2.zero) dir = Vector2.right;
        var dest = (Vector2)caster.transform.position + dir * Cfg.Range;
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, caster.transform.position);
        caster.NetTransform.RpcSnapTo(dest);
        VfxPlayer.PlayLocal(VfxType.LokiSmoke, dest);
    }
}

public class SwapTrick : AbilityBase
{
    public override string Id       => "swap_trick";
    public override string Name     => "Swap Trick";
    public override string Category => "Trickster Mobility";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null || target == null) return;
        var casterPos = caster.transform.position;
        var targetPos = target.transform.position;
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, casterPos);
        VfxPlayer.PlayLocal(VfxType.LokiTeleport, targetPos);
        caster.NetTransform.RpcSnapTo(targetPos);
        target.NetTransform.RpcSnapTo(casterPos);
    }
}

public class DecoyEscape : AbilityBase
{
    public override string Id       => "decoy_escape";
    public override string Name     => "Decoy Escape";
    public override string Category => "Trickster Mobility";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        // Leave a moving fake Loki behind
        var ill = IllusionManager.Instance.SpawnIllusion(caster, caster.transform.position);
        ill.SetBehavior(IllusionBehavior.Wander);
        ill.Lifetime = Cfg.Duration;
        // Teleport Loki away
        var dest = MapSpawnPoints.GetRandom(1)[0];
        VfxPlayer.PlayLocal(VfxType.LokiSmoke, caster.transform.position);
        caster.NetTransform.RpcSnapTo(dest);
    }
}

public class ShadowStep : AbilityBase
{
    public override string Id       => "shadow_step";
    public override string Name     => "Shadow Step";
    public override string Category => "Trickster Mobility";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        InvisibilityManager.Instance.ApplyInvisibility(caster, Cfg.Duration);
        VfxPlayer.PlayLocal(VfxType.LokiSmoke, caster.transform.position);
    }
}

public class MasterOfMisdirection : AbilityBase
{
    public override string Id       => "master_of_misdirection";
    public override string Name     => "Master of Misdirection";
    public override string Category => "Trickster Mobility";

    protected override void OnActivate(PlayerControl? caster, PlayerControl? target)
    {
        if (caster == null) return;
        CinematicPlayer.PlayUltimate(CinematicType.MasterOfMisdirection, caster, () =>
        {
            int stops = 5;
            var positions = MapSpawnPoints.GetRandom(stops);
            for (int i = 0; i < stops; i++)
            {
                var pos = positions[i];
                // Spawn illusion at each stop
                var ill = IllusionManager.Instance.SpawnIllusion(caster, pos);
                ill.SetBehavior(IllusionBehavior.Idle);
                ill.Lifetime = Cfg.Duration - i * 1.5f;
            }
            // Caster teleports to last stop
            VfxPlayer.PlayLocal(VfxType.LokiTeleport, caster.transform.position);
            caster.NetTransform.RpcSnapTo(positions[stops - 1]);
        });
    }
}

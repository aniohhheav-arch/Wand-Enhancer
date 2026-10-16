using MarvelMod.Characters;
using Reactor.Networking.Attributes;
using Reactor.Networking.Rpc;
using UnityEngine;

namespace MarvelMod.Networking;

public enum RpcIds : uint
{
    Cast = 0,
}

public static class LokiRpc
{
    public const byte NoTarget = byte.MaxValue;

    /// <summary>
    /// Runs an ability on every client (and locally first), so all players see the same effects.
    /// Movement is applied only by whichever client owns the moved player.
    /// </summary>
    [MethodRpc((uint)RpcIds.Cast, LocalHandling = RpcLocalHandling.Before)]
    public static void RpcCast(PlayerControl caster, string abilityId, byte targetId, float x, float y, int seed)
    {
        var ability = CharacterRegistry.Ability(abilityId);
        if (ability == null || caster == null) return;
        var target = targetId == NoTarget ? null : Players.ById(targetId);
        // everyone sees a quick cast flash on the caster
        if (ability.Category != "Hidden" && !ability.Silent)
        {
            var c = ability.CastColor;
            VFX.Fx.Aura(caster.transform, Assets.Sprites.Glow, new Color(c.r, c.g, c.b, 0.6f), 1.5f, 0.45f, pulse: false);
            VFX.Fx.Sparkles(caster.transform.position, VFX.Fx.LokiGold, 5, 0.9f, 0.4f);
        }
        ability.Execute(new Abilities.CastContext(caster, target, new Vector2(x, y), seed, ability.Cfg));
    }
}

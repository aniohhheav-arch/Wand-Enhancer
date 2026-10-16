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
    public static void RpcCast(PlayerControl caster, string abilityId, byte targetId, float x, float y, float x2, float y2, int seed)
    {
        var ability = CharacterRegistry.Ability(abilityId);
        if (ability == null || caster == null) return;
        var target = targetId == NoTarget ? null : Players.ById(targetId);
        // everyone sees a quick cast flash on the caster
        if (ability.Category != "Hidden" && !ability.Silent && ability.Category != "Meeting")
        {
            VFX.Fx.Motif(ability.Category, caster.transform, ability.CastColor);
            VFX.SoundBook.PlayActivation(ability, caster.transform.position);
        }
        ability.Execute(new Abilities.CastContext(caster, target, new Vector2(x, y), seed, ability.Cfg) { Point2 = new Vector2(x2, y2) });
    }
}

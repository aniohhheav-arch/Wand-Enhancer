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
        var ability = LokiCharacter.Instance.GetById(abilityId);
        if (ability == null || caster == null) return;
        var target = targetId == NoTarget ? null : Players.ById(targetId);
        ability.Execute(new Abilities.CastContext(caster, target, new Vector2(x, y), seed, ability.Cfg));
    }
}

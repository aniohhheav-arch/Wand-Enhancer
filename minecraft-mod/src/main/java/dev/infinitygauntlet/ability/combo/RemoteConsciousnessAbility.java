package dev.infinitygauntlet.ability.combo;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.nexus.ControlProfiles;
import dev.infinitygauntlet.nexus.PossessionManager;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.EnumSet;

/**
 * Mind + Space: opens a miniature rupture over the aimed creature and threads a neural link from it to the caster's
 * Consciousness Nexus, where it can be operated remotely.
 */
public final class RemoteConsciousnessAbility extends Ability {
    public RemoteConsciousnessAbility() {
        super("remote_consciousness", InfinityStone.MIND, 100, EnumSet.of(InfinityStone.MIND, InfinityStone.SPACE));
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Entity target = ctx.pickEntity(48, ControlProfiles::isCompatible);
        if (!(target instanceof Mob mob)) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        return PossessionManager.link(ctx.player(), mob);
    }
}

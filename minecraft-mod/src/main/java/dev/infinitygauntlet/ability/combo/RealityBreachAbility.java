package dev.infinitygauntlet.ability.combo;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.breach.BreachManager;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

/**
 * Mind + Space: tears reality open on a creature. Walk into the tear to reach the white observation chamber,
 * where the creature can be examined and manipulated. Casting again seals the breach.
 */
public final class RealityBreachAbility extends Ability {
    public RealityBreachAbility() {
        super("reality_breach", InfinityStone.SPACE, 400, EnumSet.of(InfinityStone.MIND, InfinityStone.SPACE));
    }

    @Override
    public boolean bypassesCooldown(AbilityContext ctx) {
        return BreachManager.hasBreach(ctx.player());
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        if (BreachManager.hasBreach(ctx.player())) {
            return BreachManager.open(ctx.player(), null);
        }
        LivingEntity target = ctx.pickLiving(40);
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        return BreachManager.open(ctx.player(), target);
    }
}

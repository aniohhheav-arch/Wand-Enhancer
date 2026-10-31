package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;

/**
 * Astral Projection: the consciousness steps out of the body as a golden, intangible projection that can fly and pass
 * through walls but touch nothing. The body stays behind; return by flying back into it, pressing the disconnect key,
 * or when time runs out. Striking the body snaps you back.
 */
public final class AstralProjectionAbility extends Ability {
    public AstralProjectionAbility() {
        super("astral_projection", InfinityStone.MIND, 600);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        if (MindManager.projecting(ctx.player()) || ctx.player().isSpectator()) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.astral.already"));
        }
        PowerFx.pose(ctx.player(), PowerPose.TEMPLE, 10);
        MindManager.project(ctx.player());
        return CastResult.success(Component.translatable("message.infinitygauntlet.astral.out",
                dev.infinitygauntlet.config.ServerConfig.ASTRAL_SECONDS.get()));
    }
}

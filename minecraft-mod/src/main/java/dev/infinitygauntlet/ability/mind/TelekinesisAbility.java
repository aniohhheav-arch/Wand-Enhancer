package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Telekinesis: seize a creature, an item or a block torn out of the ground, and hold it in the air wherever you look;
 * it follows your gaze. Cast again to hurl it: creatures crash into whatever they hit, blocks explode on impact.
 * Sneak-cast to set it down gently.
 */
public final class TelekinesisAbility extends Ability {
    public TelekinesisAbility() {
        super("telekinesis", InfinityStone.MIND, 60);
    }

    @Override
    public boolean bypassesCooldown(AbilityContext ctx) {
        return MindManager.holding(ctx.player());
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        if (MindManager.holding(ctx.player())) {
            MindManager.release(ctx.player(), ctx.sneaking());
            PowerFx.pose(ctx.player(), PowerPose.PUSH, 10);
            PowerFx.anim(ctx, "mind_push");
            return CastResult.SUCCESS;
        }
        Entity target = ctx.pickEntity(24, e -> e instanceof ItemEntity
                || e instanceof LivingEntity living && living.isAlive() && TargetRules.canTarget(ctx.player(), living) && living.getBbHeight() < 4);
        if (target == null) {
            // no creature in the way: tear the aimed block out of the ground instead
            var hit = ctx.pickBlock(24);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                target = MindManager.liftBlock(ctx.player(), hit.getBlockPos());
            }
        }
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        MindManager.grab(ctx.player(), target);
        PowerFx.pose(ctx.player(), PowerPose.HOLD, 20);
        PowerFx.anim(ctx, "mind_focus");
        return CastResult.utility(Component.translatable("message.infinitygauntlet.telekinesis.holding", target.getDisplayName()));
    }
}

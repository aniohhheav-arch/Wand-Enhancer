package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Mind Control: a hostile creature's eyes turn gold and it fights for you, following you when there is nothing to
 * fight. While you control any, casting at another creature orders them all to attack it.
 */
public final class MindControlAbility extends Ability {
    public MindControlAbility() {
        super("mind_control", InfinityStone.MIND, 400);
    }

    @Override
    public boolean bypassesCooldown(AbilityContext ctx) {
        return MindManager.controlledBy(ctx.player()) > 0;
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = ctx.pickLiving(24);
        if (target == null) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.no_target"));
        }
        int controlled = MindManager.controlledBy(ctx.player());
        boolean onCooldown = ctx.player().getData(dev.infinitygauntlet.registry.ModAttachments.POWER_DATA).cooldownEnd(id()) > ctx.level().getGameTime();
        boolean seizable = target instanceof Mob mob && target instanceof Enemy && !(target instanceof WitherBoss) && !(target instanceof EnderDragon)
                && !MindManager.isControlled(mob);
        if (seizable && !onCooldown && controlled < ServerConfig.MIND_CONTROL_MAX.get()) {
            MindManager.control(ctx.player(), (Mob) target);
            PowerFx.pose(ctx.player(), PowerPose.TEMPLE, 24);
            PowerFx.anim(ctx, "mind_focus");
            return CastResult.success(Component.translatable("message.infinitygauntlet.mind_control.seized", target.getDisplayName()));
        }
        if (controlled > 0 && ctx.player().getUUID().equals(MindManager.controllerOf(target))) {
            return CastResult.fail(Component.translatable("message.infinitygauntlet.mind_control.already"));
        }
        if (controlled > 0) {
            int n = MindManager.command(ctx.player(), target);
            PowerFx.pose(ctx.player(), PowerPose.THRUST, 10);
            return CastResult.utility(Component.translatable("message.infinitygauntlet.mind_control.command", n, target.getDisplayName()));
        }
        return CastResult.fail(Component.translatable("message.infinitygauntlet.mind_control.resists", target.getDisplayName()));
    }
}

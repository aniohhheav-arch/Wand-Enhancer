package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** One wound-up, heavy blow (scaled per Stand) on the creature you are looking at. Free if nothing is in reach. */
public final class PrecisionStrikeAbility extends StandAbility {
    private final float damageMultiplier;

    public PrecisionStrikeAbility(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        double reach = MultiverseConfig.SERVER.standReach.get();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        AABB sweep = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(ctx.level(), player, eye, end, sweep,
                entity -> entity instanceof LivingEntity && AbilityTargeting.isHostileTarget(player, entity));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
            return false;
        }
        stand.startHeavy(target, damageMultiplier);
        return true;
    }
}

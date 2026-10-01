package com.infinitemultiverse.core.ability;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/** Shared friend-or-foe rules so every offensive ability respects PvP settings, teams and pets. */
public final class AbilityTargeting {
    private AbilityTargeting() {
    }

    public static boolean isHostileTarget(ServerPlayer owner, Entity entity) {
        if (entity == owner || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player other) {
            return !other.isCreative() && owner.canHarmPlayer(other);
        }
        if (entity instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        return !entity.isAlliedTo(owner);
    }

    public static List<LivingEntity> hostilesInRadius(ServerPlayer owner, double radius) {
        double radiusSqr = radius * radius;
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radius),
                entity -> entity.distanceToSqr(owner) <= radiusSqr && isHostileTarget(owner, entity));
    }
}

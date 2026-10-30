package dev.infinitygauntlet.ability;

import dev.infinitygauntlet.config.ServerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/** Who offensive abilities may hit, and who counts as an ally for group effects. */
public final class TargetRules {
    public static boolean canTarget(Player caster, Entity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other) {
            return ServerConfig.AFFECT_PLAYERS.get() && caster.canHarmPlayer(other) && !other.isCreative();
        }
        return !isAlly(caster, target);
    }

    public static boolean isAlly(Player caster, Entity entity) {
        if (entity == caster) {
            return true;
        }
        if (entity instanceof OwnableEntity ownable && caster.getUUID().equals(ownable.getOwnerUUID())) {
            return true;
        }
        if (entity instanceof Player other) {
            return caster.getTeam() == null ? !ServerConfig.AFFECT_PLAYERS.get() : caster.isAlliedTo(other);
        }
        return caster.isAlliedTo(entity);
    }

    public static boolean isHostileTo(Player caster, LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.monster.Enemy && canTarget(caster, entity);
    }

    private TargetRules() {
    }
}

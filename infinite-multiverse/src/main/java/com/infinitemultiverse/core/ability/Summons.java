package com.infinitemultiverse.core.ability;

import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Temporary allied creatures (shadow beasts, released spirits, living constructs). They never harm their creator. */
public final class Summons {
    public static final String CREATOR_TAG = "infinitemultiverse_creator";

    private Summons() {
    }

    @Nullable
    public static <T extends Mob> T spawnAlly(ServerPlayer owner, EntityType<T> type, Vec3 at, int lifetime, int fadeColor) {
        ServerLevel level = owner.serverLevel();
        T mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        mob.getPersistentData().putUUID(CREATOR_TAG, owner.getUUID());
        mob.setPersistenceRequired();
        if (mob instanceof TamableAnimal tamable) {
            tamable.tame(owner);
        }
        LivingEntity target = AbilityTargeting.hostilesInRadius(owner, 20.0).stream()
                .filter(e -> !isAllyOf(e, owner))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(owner))).orElse(null);
        if (target != null) {
            mob.setTarget(target);
        }
        level.addFreshEntity(mob);
        StandScheduler.later(lifetime, () -> {
            if (mob.isAlive()) {
                MultiverseVfx.broadcast(level, VfxIds.STAND_DISMISS, mob.position(), Vec3.ZERO, fadeColor);
                mob.discard();
            }
        });
        return mob;
    }

    public static boolean isAllyOf(Entity entity, ServerPlayer owner) {
        return entity.getPersistentData().hasUUID(CREATOR_TAG) && owner.getUUID().equals(entity.getPersistentData().getUUID(CREATOR_TAG));
    }

    /** True if {@code attacker} was summoned by {@code target}. */
    public static boolean isCreatorOf(Entity target, @Nullable Entity attacker) {
        return attacker != null && attacker.getPersistentData().hasUUID(CREATOR_TAG)
                && target.getUUID().equals(attacker.getPersistentData().getUUID(CREATOR_TAG));
    }
}

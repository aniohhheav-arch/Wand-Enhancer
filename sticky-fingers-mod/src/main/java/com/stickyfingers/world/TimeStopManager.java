package com.stickyfingers.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Freezes entities during Star Platinum's Time Stop.
 * Stores position + velocity and re-applies the freeze each tick.
 */
public final class TimeStopManager {
    private record FrozenState(Vec3 pos, float yRot, float xRot) {}

    // per-level frozen entities: entityId → snapshot
    private static final Map<Integer, FrozenState> FROZEN = new HashMap<>();
    // entity IDs queued to receive delayed damage when time resumes
    private static final List<int[]> QUEUED_HITS = new ArrayList<>(); // [entityId, damage*100]

    public static boolean isFrozen(int entityId) {
        return FROZEN.containsKey(entityId);
    }

    /** Freeze a living entity in place. */
    public static void freeze(LivingEntity entity) {
        if (FROZEN.containsKey(entity.getId())) return;
        FROZEN.put(entity.getId(), new FrozenState(entity.position(), entity.getYRot(), entity.getXRot()));
    }

    /** Called every server tick: holds frozen entities in place. */
    public static void tickFreeze(ServerLevel level) {
        Iterator<Map.Entry<Integer, FrozenState>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) { it.remove(); continue; }
            if (entity instanceof LivingEntity le) {
                FrozenState s = entry.getValue();
                le.setPos(s.pos().x, s.pos().y, s.pos().z);
                le.setDeltaMovement(Vec3.ZERO);
                le.setYRot(s.yRot());
                le.setXRot(s.xRot());
                le.hurtTime = 0;
                if (le instanceof Mob mob) mob.setNoAi(true);
            }
        }
    }

    /** Unfreeze all entities in a radius of the caster and restore their AI. */
    public static void releaseAll(ServerLevel level) {
        for (var id : FROZEN.keySet()) {
            var entity = level.getEntity(id);
            if (entity instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }
        FROZEN.clear();
        // resolve queued hits
        for (int[] hit : QUEUED_HITS) {
            var entity = level.getEntity(hit[0]);
            if (entity instanceof LivingEntity le && le.isAlive()) {
                le.hurt(level.damageSources().generic(), hit[1] / 100f);
            }
        }
        QUEUED_HITS.clear();
    }

    /** Queue damage to be applied when time resumes. */
    public static void queueHit(int entityId, float damage) {
        QUEUED_HITS.add(new int[]{entityId, (int)(damage * 100)});
    }

    /** Freeze all living entities within range of a point (except the caster). */
    public static void freezeArea(ServerLevel level, Vec3 center, double radius, int excludeId) {
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> e.getId() != excludeId && e.isAlive() && !e.isSpectator())) {
            if (le.distanceToSqr(center.x, center.y, center.z) <= radius * radius) {
                freeze(le);
            }
        }
    }

    public static int frozenCount() { return FROZEN.size(); }

    private TimeStopManager() {}
}

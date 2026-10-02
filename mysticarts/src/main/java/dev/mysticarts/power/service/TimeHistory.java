package dev.mysticarts.power.service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Rolling per-entity record of where things were, for rewinds and time loops. */
public final class TimeHistory {
    public record Snapshot(ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch, float health, Vec3 motion) {}

    private static final int KEEP = 240;
    private static final Map<UUID, ArrayDeque<Snapshot>> HISTORY = new HashMap<>();

    private TimeHistory() {}

    public static void record(Entity e) {
        ArrayDeque<Snapshot> d = HISTORY.computeIfAbsent(e.getUUID(), k -> new ArrayDeque<>());
        d.addLast(new Snapshot(e.level().dimension(), e.position(), e.getYRot(), e.getXRot(), e instanceof LivingEntity l ? l.getHealth() : 0, e.getDeltaMovement()));
        while (d.size() > KEEP) d.pollFirst();
    }

    /** Where the entity was {@code ticksAgo} ticks ago (or the oldest record). */
    @Nullable
    public static Snapshot ago(Entity e, int ticksAgo) {
        ArrayDeque<Snapshot> d = HISTORY.get(e.getUUID());
        if (d == null || d.isEmpty()) return null;
        int idx = Math.max(0, d.size() - 1 - ticksAgo);
        int i = 0;
        for (Snapshot s : d) if (i++ == idx) return s;
        return d.peekFirst();
    }

    public static void forget(UUID id) {
        HISTORY.remove(id);
    }

    public static void clear() {
        HISTORY.clear();
    }
}

package dev.finalframe.sheriff;

import dev.finalframe.FFConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers each shooter's most recent near miss: a shot that passed close by a living thing without touching it and
 * struck something beyond. Kept per side so the client can show readiness without asking the server.
 */
public final class ShotMemory {
    public record Miss(int targetId, Vec3 impact, long time) {
    }

    private static final Map<UUID, Miss> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Miss> CLIENT = new ConcurrentHashMap<>();

    private ShotMemory() {
    }

    private static Map<UUID, Miss> side(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    public static void record(Level level, Player shooter, Vec3 from, Vec3 stop, @Nullable Entity hit) {
        Map<UUID, Miss> map = side(level);
        map.remove(shooter.getUUID());
        if (hit != null) {
            return;
        }
        Vec3 seg = stop.subtract(from);
        double len = seg.length();
        if (len < 1.0) {
            return;
        }
        Vec3 dir = seg.scale(1 / len);
        double maxMiss = FFConfig.get(FFConfig.MISS_DISTANCE);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(maxMiss),
            e -> e != shooter && e.isAlive() && !e.isSpectator())) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.6, 0);
            double along = c.subtract(from).dot(dir);
            // The bullet has to go past them and land somewhere behind.
            if (along < 1.0 || along > len - 0.3) {
                continue;
            }
            double d = c.distanceTo(from.add(dir.scale(along)));
            if (d < maxMiss && d < bestDist) {
                best = e;
                bestDist = d;
            }
        }
        if (best != null) {
            map.put(shooter.getUUID(), new Miss(best.getId(), stop, level.getGameTime()));
        }
    }

    /** The near miss on {@code target} if it happened recently enough to matter. */
    @Nullable
    public static Miss recent(Player shooter, LivingEntity target) {
        Miss m = side(shooter.level()).get(shooter.getUUID());
        if (m == null || m.targetId() != target.getId()) {
            return null;
        }
        long age = shooter.level().getGameTime() - m.time();
        return age >= 0 && age <= FFConfig.get(FFConfig.MISS_WINDOW_TICKS) ? m : null;
    }

    public static void forget(Player shooter) {
        side(shooter.level()).remove(shooter.getUUID());
    }
}

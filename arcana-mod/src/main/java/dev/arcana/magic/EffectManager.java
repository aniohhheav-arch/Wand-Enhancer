package dev.arcana.magic;

import dev.arcana.ArcanaConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Lightweight server-side scheduler for spell effects. Spells are expressed as tick functions instead of
 * entities, which keeps entity counts low and makes every effect server-authoritative.
 */
public final class EffectManager {
    @FunctionalInterface
    public interface TickFn {
        /** @return false to end the effect early. */
        boolean tick(int age);
    }

    private static final class Active {
        final int max;
        final TickFn fn;
        final Runnable end;
        final boolean mustFinish;
        int age;

        Active(int max, TickFn fn, Runnable end, boolean mustFinish) {
            this.max = max;
            this.fn = fn;
            this.end = end;
            this.mustFinish = mustFinish;
        }
    }

    private static final Map<ResourceKey<Level>, List<Active>> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<Active>> PENDING = new HashMap<>();

    /** Entities currently frozen/summoned by a running effect; used to clean up after crashes. */
    public static final Set<UUID> FROZEN = new HashSet<>();
    public static final Set<UUID> SUMMONS = new HashSet<>();

    /** Number of effect callbacks that threw (exposed for game tests). */
    public static int errors;

    public static void run(ServerLevel level, int maxAge, TickFn fn, Runnable end) {
        add(level, new Active(maxAge, fn, end, false));
    }

    /** Like {@link #run} but {@code end} is also executed when the server stops (for effects that must restore state). */
    public static void runSafe(ServerLevel level, int maxAge, TickFn fn, Runnable end) {
        add(level, new Active(maxAge, fn, end, true));
    }

    public static void later(ServerLevel level, int delay, Runnable action) {
        add(level, new Active(Math.max(1, delay), a -> true, action, false));
    }

    private static void add(ServerLevel level, Active a) {
        List<Active> list = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        List<Active> pending = PENDING.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        if (list.size() + pending.size() >= ArcanaConfig.MAX_EFFECTS.get() && !a.mustFinish) return;
        pending.add(a);
    }

    public static void tick(ServerLevel level) {
        List<Active> pending = PENDING.get(level.dimension());
        List<Active> list = ACTIVE.get(level.dimension());
        if (list == null) return;
        if (pending != null && !pending.isEmpty()) {
            list.addAll(pending);
            pending.clear();
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            Active a = list.get(i);
            boolean alive;
            try {
                alive = a.fn.tick(a.age);
            } catch (RuntimeException ex) {
                errors++;
                dev.arcana.ArcanaMod.LOGGER.error("Spell effect failed", ex);
                alive = false;
            }
            a.age++;
            if (!alive || a.age >= a.max) {
                list.remove(i);
                finish(a);
            }
        }
    }

    private static void finish(Active a) {
        try {
            if (a.end != null) a.end.run();
        } catch (RuntimeException ex) {
            errors++;
            dev.arcana.ArcanaMod.LOGGER.error("Spell effect cleanup failed", ex);
        }
    }

    /** Called when the server stops: finish effects that hold temporary world/entity state. */
    public static void shutdown() {
        for (List<Active> list : ACTIVE.values()) for (Active a : list) if (a.mustFinish) finish(a);
        for (List<Active> list : PENDING.values()) for (Active a : list) if (a.mustFinish) finish(a);
        ACTIVE.clear();
        PENDING.clear();
        FROZEN.clear();
        SUMMONS.clear();
    }

    private EffectManager() {}
}

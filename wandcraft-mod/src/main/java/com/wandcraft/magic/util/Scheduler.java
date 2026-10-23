package com.wandcraft.magic.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.server.level.ServerLevel;

/** Tiny delayed-task queue so spells can sequence effects (eruptions, waves, echoes) without extra entities. */
public final class Scheduler {
    private record Task(ServerLevel level, long due, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> INCOMING = new ArrayList<>();

    private Scheduler() {}

    public static void after(ServerLevel level, int ticks, Runnable r) {
        INCOMING.add(new Task(level, level.getGameTime() + Math.max(0, ticks), r));
    }

    /** Runs {@code step} with index 0..count-1, once every {@code interval} ticks starting after {@code delay}. */
    public static void repeat(ServerLevel level, int delay, int interval, int count, IntConsumer step) {
        for (int i = 0; i < count; i++) {
            final int idx = i;
            after(level, delay + i * interval, () -> step.accept(idx));
        }
    }

    public static void tick(ServerLevel level) {
        if (!INCOMING.isEmpty()) {
            TASKS.addAll(INCOMING);
            INCOMING.clear();
        }
        long now = level.getGameTime();
        List<Runnable> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.level == level && t.due <= now) {
                due.add(t.run);
                it.remove();
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().warn("Spell task failed", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        INCOMING.clear();
    }
}

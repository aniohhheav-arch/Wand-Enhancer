package com.infinitemultiverse.stand;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny server-thread scheduler for timed Stand effects (fields, delayed expiry, repeating damage).
 * Tasks are transient: they end with the server, and every task must leave the world consistent if dropped.
 */
public final class StandScheduler {
    @FunctionalInterface
    public interface Task {
        /** Called every {@code period} ticks with the run index (0-based). Return false to stop early. */
        boolean run(int index);
    }

    private static final class Entry {
        final Task task;
        final int period;
        final int runs;
        int countdown;
        int index;

        Entry(Task task, int delay, int period, int runs) {
            this.task = task;
            this.countdown = delay;
            this.period = Math.max(1, period);
            this.runs = runs;
        }
    }

    private static final List<Entry> TASKS = new ArrayList<>();
    private static final List<Entry> PENDING = new ArrayList<>();

    private StandScheduler() {
    }

    /** Runs {@code task} {@code runs} times, first after {@code delay} ticks, then every {@code period} ticks. */
    public static void repeat(int delay, int period, int runs, Task task) {
        PENDING.add(new Entry(task, delay, period, runs));
    }

    public static void later(int delay, Runnable action) {
        repeat(delay, 1, 1, index -> {
            action.run();
            return false;
        });
    }

    static void tick() {
        TASKS.addAll(PENDING);
        PENDING.clear();
        Iterator<Entry> it = TASKS.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (--entry.countdown > 0) {
                continue;
            }
            boolean keepGoing = entry.task.run(entry.index++);
            if (!keepGoing || entry.index >= entry.runs) {
                it.remove();
            } else {
                entry.countdown = entry.period;
            }
        }
    }

    static void clear() {
        TASKS.clear();
        PENDING.clear();
    }
}

package dev.riftverse.multiverse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Tiny server-thread task scheduler used to choreograph cinematics with world changes. */
public final class Scheduler {
    private record Task(long runAt, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private Scheduler() {}

    public static void later(int ticks, Runnable action) {
        TASKS.add(new Task(now + Math.max(0, ticks), action));
    }

    public static void tick(MinecraftServer server) {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.runAt <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) t.action.run();
    }

    public static int pending() {
        return TASKS.size();
    }

    public static void clear() {
        TASKS.clear();
    }
}

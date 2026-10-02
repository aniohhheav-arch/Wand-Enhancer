package dev.mysticarts.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * Delayed server actions (time loops, delayed returns, staged effects). Tasks flagged {@code mustRun} are executed
 * immediately if the server stops first, so displaced entities are never lost.
 */
public final class Scheduler {
    private record Task(long due, Consumer<MinecraftServer> action, boolean mustRun) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private Scheduler() {}

    public static void schedule(int delay, Consumer<MinecraftServer> action) {
        TASKS.add(new Task(now + Math.max(1, delay), action, false));
    }

    public static void scheduleSafe(int delay, Consumer<MinecraftServer> action) {
        TASKS.add(new Task(now + Math.max(1, delay), action, true));
    }

    public static void tick(MinecraftServer server) {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.due <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) t.action.accept(server);
    }

    public static void stop(MinecraftServer server) {
        List<Task> pending = new ArrayList<>(TASKS);
        TASKS.clear();
        for (Task t : pending) if (t.mustRun) t.action.accept(server);
    }
}

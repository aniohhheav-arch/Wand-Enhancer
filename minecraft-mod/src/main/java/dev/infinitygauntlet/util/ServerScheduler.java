package dev.infinitygauntlet.util;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Runs delayed server-side steps of multi-stage abilities (charge-ups, staged cinematic sequences). */
@EventBusSubscriber(modid = "infinitygauntlet")
public final class ServerScheduler {
    private record Task(long runAt, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick;

    public static void schedule(int delayTicks, Runnable action) {
        TASKS.add(new Task(tick + Math.max(1, delayTicks), action));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        tick++;
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.runAt <= tick) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            t.action.run();
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    private ServerScheduler() {
    }
}

package dev.telephoneorder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Tiny server-tick scheduler used by bursts, echoes and cinematic delivery sequences. */
public final class Sched {
    private record Task(long due, Runnable run) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> INCOMING = new ArrayList<>();
    private static long tick;

    private Sched() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Sched::onTick);
    }

    public static void later(int ticks, Runnable r) {
        INCOMING.add(new Task(tick + Math.max(0, ticks), r));
    }

    public static long now() { return tick; }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (!INCOMING.isEmpty()) { TASKS.addAll(INCOMING); INCOMING.clear(); }
        for (int i = 0; i < TASKS.size(); i++) {
            Task t = TASKS.get(i);
            if (t.due() <= tick) {
                TASKS.remove(i--);
                try { t.run().run(); } catch (Throwable e) { TelephoneOrder.LOGGER.error("Scheduled task failed", e); }
            }
        }
        if (!INCOMING.isEmpty()) { TASKS.addAll(INCOMING); INCOMING.clear(); }
    }

    public static void clear() { TASKS.clear(); INCOMING.clear(); }
}

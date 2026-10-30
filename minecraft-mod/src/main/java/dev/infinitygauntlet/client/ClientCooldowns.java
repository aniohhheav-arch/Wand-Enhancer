package dev.infinitygauntlet.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Client mirror of the player's ability cooldowns, counted down locally between server syncs. */
public final class ClientCooldowns {
    private record Entry(int total, int remaining) {
    }

    private static final Map<String, Entry> COOLDOWNS = new HashMap<>();

    public static void sync(Map<String, Integer> remaining) {
        Map<String, Entry> old = new HashMap<>(COOLDOWNS);
        COOLDOWNS.clear();
        remaining.forEach((id, ticks) -> {
            Entry prev = old.get(id);
            int total = prev != null && prev.total >= ticks ? prev.total : ticks;
            COOLDOWNS.put(id, new Entry(total, ticks));
        });
    }

    public static void tick() {
        COOLDOWNS.replaceAll((id, e) -> new Entry(e.total, e.remaining - 1));
        COOLDOWNS.values().removeIf(e -> e.remaining <= 0);
    }

    public static int remaining(ResourceLocation id) {
        Entry e = COOLDOWNS.get(id.toString());
        return e == null ? 0 : e.remaining;
    }

    /** 0 = ready, 1 = just used. */
    public static float fraction(ResourceLocation id) {
        Entry e = COOLDOWNS.get(id.toString());
        return e == null || e.total <= 0 ? 0 : (float) e.remaining / e.total;
    }

    public static void clear() {
        COOLDOWNS.clear();
    }

    private ClientCooldowns() {
    }
}

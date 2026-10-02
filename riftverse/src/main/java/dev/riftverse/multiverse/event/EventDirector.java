package dev.riftverse.multiverse.event;

import dev.riftverse.multiverse.RealityState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * The event director: a world-wide intensity level, per-event duration overrides, event chains and the natural-event
 * switch. Everything is stored in {@link RealityState} so it survives restarts.
 */
public final class EventDirector {
    public enum Intensity {
        MINOR(0.5f), MODERATE(1f), MAJOR(1.5f), SEVERE(2f), CATASTROPHIC(3f), MULTIVERSAL(4f);

        public final float scale;

        Intensity(float scale) {
            this.scale = scale;
        }
    }

    public static final int MAX_CHAIN = 3;
    private static MinecraftServer server;

    private EventDirector() {}

    public static void bind(MinecraftServer s) {
        server = s;
    }

    private static CompoundTag tag() {
        return server == null ? new CompoundTag() : RealityState.get(server).director;
    }

    private static void touch() {
        if (server != null) RealityState.get(server).touch();
    }

    public static Intensity intensity() {
        CompoundTag t = tag();
        return t.contains("intensity") ? Intensity.values()[Math.floorMod(t.getInt("intensity"), Intensity.values().length)] : Intensity.MODERATE;
    }

    public static void setIntensity(Intensity i) {
        tag().putInt("intensity", i.ordinal());
        touch();
    }

    public static float scale() {
        return intensity().scale;
    }

    /** Duration override in seconds, or -1. */
    public static int durationOverride(EventType t) {
        CompoundTag d = tag().getCompound("durations");
        return d.contains(t.id) ? d.getInt(t.id) : -1;
    }

    public static void setDuration(EventType t, int seconds) {
        CompoundTag root = tag();
        CompoundTag d = root.getCompound("durations");
        if (seconds < 0) d.remove(t.id);
        else d.putInt(t.id, seconds);
        root.put("durations", d);
        touch();
    }

    public static int durationFor(EventType t) {
        int o = durationOverride(t);
        int base = o > 0 ? o * 20 : t.durationTicks;
        return o > 0 ? base : (int) (base * (0.75f + 0.25f * scale()));
    }

    public static boolean chains() {
        return !tag().contains("chains") || tag().getBoolean("chains");
    }

    public static void setChains(boolean on) {
        tag().putBoolean("chains", on);
        touch();
    }

    public static boolean naturalEnabled() {
        return !tag().contains("natural") || tag().getBoolean("natural");
    }

    public static void setNatural(boolean on) {
        tag().putBoolean("natural", on);
        touch();
    }

    public static void reset() {
        CompoundTag t = tag();
        for (String k : new ArrayList<>(t.getAllKeys())) t.remove(k);
        touch();
    }

    /** After an event ends naturally, it may set off a related one (same category). Chains never repeat a type and stop at three links. */
    static void onEnded(ActiveEvent e, boolean forced) {
        if (forced || !chains() || e.type.classic()) return;
        int depth = e.data.getInt("chain");
        if (depth >= MAX_CHAIN) return;
        RandomSource r = e.level.random;
        if (r.nextFloat() > 0.25f + 0.1f * scale()) return;
        String seen = e.data.getString("chainSeen") + "," + e.type.id;
        List<EventType> options = new ArrayList<>();
        for (EventType t : EventType.values()) {
            if (!t.classic() && t.category() == e.type.category() && !seen.contains("," + t.id)) options.add(t);
        }
        if (options.isEmpty()) return;
        EventType next = options.get(r.nextInt(options.size()));
        var out = EventManager.start(next, e.level, e.center, null, e.natural);
        if (out.ok()) {
            for (ActiveEvent a : EventManager.active()) {
                if (a.type == next && a.age == 0) {
                    a.data.putInt("chain", depth + 1);
                    a.data.putString("chainSeen", seen);
                }
            }
        }
    }
}

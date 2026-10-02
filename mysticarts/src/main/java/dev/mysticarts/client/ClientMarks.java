package dev.mysticarts.client;

import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.service.MarkKind;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.entity.Entity;

/** Client mirror of the server's status marks: drives tick suppression for frozen entities and status visuals. */
public final class ClientMarks {
    private static final Map<Integer, EnumMap<MarkKind, int[]>> MARKS = new HashMap<>();

    private ClientMarks() {}

    public static void update(Payloads.Marks p) {
        MARKS.clear();
        for (Payloads.Marks.Entry e : p.entries()) {
            MARKS.computeIfAbsent(e.entity(), k -> new EnumMap<>(MarkKind.class)).put(MarkKind.byId(e.kind()), new int[] {e.ticks(), e.param()});
        }
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, EnumMap<MarkKind, int[]>>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            EnumMap<MarkKind, int[]> m = it.next().getValue();
            m.values().removeIf(v -> --v[0] <= 0);
            if (m.isEmpty()) it.remove();
        }
    }

    public static boolean has(Entity e, MarkKind kind) {
        EnumMap<MarkKind, int[]> m = MARKS.get(e.getId());
        return m != null && m.containsKey(kind);
    }

    public static int param(Entity e, MarkKind kind) {
        EnumMap<MarkKind, int[]> m = MARKS.get(e.getId());
        int[] v = m == null ? null : m.get(kind);
        return v == null ? 0 : v[1];
    }

    public static int ticks(Entity e, MarkKind kind) {
        EnumMap<MarkKind, int[]> m = MARKS.get(e.getId());
        int[] v = m == null ? null : m.get(kind);
        return v == null ? 0 : v[0];
    }

    /** Whether the client should skip this entity's tick (frozen, or between slowed ticks). */
    public static boolean suppress(Entity e, long gameTime) {
        EnumMap<MarkKind, int[]> m = MARKS.get(e.getId());
        if (m == null) return false;
        if (m.containsKey(MarkKind.FROZEN)) return true;
        int[] slow = m.get(MarkKind.SLOWED);
        return slow != null && slow[1] > 1 && gameTime % slow[1] != 0;
    }

    public static Map<Integer, EnumMap<MarkKind, int[]>> all() {
        return MARKS;
    }

    public static void clear() {
        MARKS.clear();
    }
}

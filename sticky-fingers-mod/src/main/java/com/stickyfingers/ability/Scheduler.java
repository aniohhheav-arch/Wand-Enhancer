package com.stickyfingers.ability;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Runs multi-tick scripted ability sequences (barrages, combos, Zipper World) on the server thread.
 * Sequences are bound to their player and are cancelled cleanly when the player leaves or dies.
 */
public final class Scheduler {
    public interface Sequence {
        /** @return false when the sequence has finished. */
        boolean tick(ServerPlayer player, int t);

        /** Called when the sequence is cut short; must leave the world clean. */
        default void cancel(ServerPlayer player) {}

        /** Sequences with a key replace each other instead of stacking. */
        default String key() { return null; }
    }

    private static final class Running {
        final Sequence seq;
        int t;
        Running(Sequence seq) { this.seq = seq; }
    }

    private static final Map<UUID, List<Running>> RUNNING = new HashMap<>();

    public static void run(ServerPlayer player, Sequence seq) {
        List<Running> list = RUNNING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        if (seq.key() != null) {
            list.removeIf(r -> {
                if (seq.key().equals(r.seq.key())) { r.seq.cancel(player); return true; }
                return false;
            });
        }
        list.add(new Running(seq));
    }

    public static boolean isRunning(ServerPlayer player, String key) {
        List<Running> list = RUNNING.get(player.getUUID());
        return list != null && list.stream().anyMatch(r -> key.equals(r.seq.key()));
    }

    public static void tick(ServerPlayer player) {
        List<Running> list = RUNNING.get(player.getUUID());
        if (list == null || list.isEmpty()) return;
        for (Running r : new ArrayList<>(list)) {
            boolean keep;
            try {
                keep = r.seq.tick(player, r.t++);
            } catch (RuntimeException e) {
                com.stickyfingers.StickyFingersMod.LOGGER.error("Sticky Fingers sequence failed", e);
                r.seq.cancel(player);
                keep = false;
            }
            if (!keep) list.remove(r);
        }
    }

    public static void cancelAll(ServerPlayer player) {
        List<Running> list = RUNNING.remove(player.getUUID());
        if (list != null) list.forEach(r -> r.seq.cancel(player));
    }

    private Scheduler() {}
}

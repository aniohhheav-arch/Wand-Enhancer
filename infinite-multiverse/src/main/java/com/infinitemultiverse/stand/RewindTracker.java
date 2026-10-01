package com.infinitemultiverse.stand;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Rolling ~5 second history of Killer Queen users, sampled twice a second. Only tracks players who own Killer Queen. */
public final class RewindTracker {
    public record Snapshot(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 position, float health) {
    }

    private static final int INTERVAL = 10;
    private static final int CAPACITY = 10;
    private static final Map<UUID, Deque<Snapshot>> HISTORY = new HashMap<>();

    private RewindTracker() {
    }

    static void tick(MinecraftServer server, int tickCount) {
        if (tickCount % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StandType type = StandManager.standTypeOf(player);
            if (type == null || !type.tracksRewind()) {
                HISTORY.remove(player.getUUID());
                continue;
            }
            Deque<Snapshot> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
            history.addLast(new Snapshot(player.level().dimension(), player.position(), player.getHealth()));
            while (history.size() > CAPACITY) {
                history.removeFirst();
            }
        }
    }

    /** The oldest snapshot in the player's current dimension, or null. */
    @Nullable
    public static Snapshot oldest(ServerPlayer player) {
        Deque<Snapshot> history = HISTORY.get(player.getUUID());
        if (history == null) {
            return null;
        }
        for (Snapshot snapshot : history) {
            if (snapshot.dimension() == player.level().dimension() && snapshot.position().distanceToSqr(player.position()) > 1.0) {
                return snapshot;
            }
        }
        return null;
    }

    public static void clear(ServerPlayer player) {
        HISTORY.remove(player.getUUID());
    }

    static void reset() {
        HISTORY.clear();
    }
}

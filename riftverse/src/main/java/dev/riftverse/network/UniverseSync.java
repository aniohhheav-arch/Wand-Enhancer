package dev.riftverse.network;

import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps each client's notion of "which reality am I in" up to date. */
public final class UniverseSync {
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final long NONE = Long.MIN_VALUE;
    private static final long NEXUS = Long.MIN_VALUE + 1;

    private UniverseSync() {}

    public static void send(ServerPlayer player) {
        long key;
        Payloads.UniverseSync payload;
        if (player.level().dimension() == RvWorldgen.EXPANSE) {
            UniverseSpec spec = UniverseRegistry.specAt(player.getBlockX(), player.getBlockZ());
            UniverseRegistry.get(player.server).remember(spec);
            key = spec.id.pack();
            payload = new Payloads.UniverseSync(1, spec.save());
        } else if (player.level().dimension() == RvWorldgen.NEXUS) {
            key = NEXUS;
            payload = new Payloads.UniverseSync(2, new CompoundTag());
        } else {
            key = NONE;
            payload = new Payloads.UniverseSync(0, new CompoundTag());
        }
        LAST.put(player.getUUID(), key);
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Cheap periodic check for players who crossed into another slot by unusual means. */
    public static void check(ServerPlayer player) {
        Long last = LAST.get(player.getUUID());
        long now;
        if (player.level().dimension() == RvWorldgen.EXPANSE) now = UniverseId.ofBlock(player.getBlockX(), player.getBlockZ()).pack();
        else if (player.level().dimension() == RvWorldgen.NEXUS) now = NEXUS;
        else now = NONE;
        if (last == null || last != now) send(player);
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }
}

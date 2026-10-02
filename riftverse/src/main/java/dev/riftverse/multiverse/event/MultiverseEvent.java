package dev.riftverse.multiverse.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Behaviour of one event type. Implementations live in {@link EventHandlers}. */
public interface MultiverseEvent {
    /** Extra conditions for natural occurrence (dimension rules are checked by the manager). */
    default boolean canOccurNaturally(ServerLevel level, BlockPos pos) {
        return true;
    }

    /** Sets the event up: spawns, rifts, focus point. Returning a non-null string aborts with that reason. */
    String start(ActiveEvent e);

    void tick(ActiveEvent e);

    /** Cleans up. {@code forced} is true when stopped by command, stabilization or erasure. */
    void end(ActiveEvent e, boolean forced);
}

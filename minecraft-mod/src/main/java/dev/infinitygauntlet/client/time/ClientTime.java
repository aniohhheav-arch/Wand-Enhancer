package dev.infinitygauntlet.client.time;

import dev.infinitygauntlet.InfinityGauntlet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client mirror of server time dilation. Client-side entity ticks (projectile flight prediction, animations,
 * interpolation) are skipped at the same rate as on the server, so a frozen arrow does not fly on and snap back.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class ClientTime {
    private static final class State {
        float scale;
        float acc;
        long heard;
        boolean visible = true;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    /**
     * The server re-sends every slowed entity's scale once a second. An entry not heard from for this long is stale
     * (its "back to normal" update went to a world the player was not in, e.g. while in the observation chamber)
     * and is dropped, so a creature can never stay frozen on this client after the server released it.
     */
    private static final int STALE_TICKS = 60;
    private static long clock;

    public static void set(int entityId, float scale, boolean visible) {
        if (scale >= 1f) {
            STATES.remove(entityId);
            return;
        }
        State state = STATES.computeIfAbsent(entityId, id -> new State());
        state.scale = scale;
        state.heard = clock;
        state.visible = visible;
        ClientHistory.keepAlive(60);
    }

    public static float scale(Entity e) {
        State s = STATES.get(e.getId());
        return s == null ? 1f : s.scale;
    }

    /** Entities whose time is slowed by a visible (Time Stone) source, for the overlay. */
    public static void forEach(BiConsumer<Integer, Float> action) {
        STATES.forEach((id, s) -> {
            if (s.visible) {
                action.accept(id, s.scale);
            }
        });
    }

    public static boolean isEmpty() {
        return STATES.isEmpty();
    }

    public static void clear() {
        STATES.clear();
    }

    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        clock++;
        if (!STATES.isEmpty()) {
            STATES.values().removeIf(s -> clock - s.heard > STALE_TICKS);
        }
    }

    @SubscribeEvent
    static void onLevelChange(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        STATES.clear(); // a new world: entity ids no longer refer to the same creatures
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (STATES.isEmpty() || !e.level().isClientSide || e == Minecraft.getInstance().player) {
            return;
        }
        State s = STATES.get(e.getId());
        if (s == null) {
            return;
        }
        s.acc += s.scale;
        if (s.acc >= 1f) {
            s.acc -= 1f;
        } else {
            event.setCanceled(true);
        }
    }

    private ClientTime() {
    }
}

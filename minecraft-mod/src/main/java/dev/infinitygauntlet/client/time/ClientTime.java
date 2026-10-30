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
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    public static void set(int entityId, float scale) {
        if (scale >= 1f) {
            STATES.remove(entityId);
            return;
        }
        STATES.computeIfAbsent(entityId, id -> new State()).scale = scale;
        ClientHistory.keepAlive(60);
    }

    public static float scale(Entity e) {
        State s = STATES.get(e.getId());
        return s == null ? 1f : s.scale;
    }

    public static void forEach(BiConsumer<Integer, Float> action) {
        STATES.forEach((id, s) -> action.accept(id, s.scale));
    }

    public static boolean isEmpty() {
        return STATES.isEmpty();
    }

    public static void clear() {
        STATES.clear();
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

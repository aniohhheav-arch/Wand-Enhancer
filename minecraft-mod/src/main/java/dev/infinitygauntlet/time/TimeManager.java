package dev.infinitygauntlet.time;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.network.TimeScalePayload;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Real time dilation. Every entity under a Time Stone effect has a time scale in [0, 1]; on the server (and, through
 * {@link TimeScalePayload}, on clients) its ticks are skipped so it runs at exactly that fraction of normal speed.
 * A frozen mob keeps its momentum, AI state and pose and simply continues once the scale returns to 1; an arrow
 * hangs in mid-air. Players move on their own client, so they are slowed through movement attributes instead.
 * <p>
 * Effects are sources with an expiry; fields re-apply a short source every tick, so stepping out of a field restores
 * normal time within two ticks. The effective scale is the minimum of all live sources.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class TimeManager {
    private static final ResourceLocation MODIFIER = InfinityGauntlet.id("time_dilation");
    private static final int RESYNC_INTERVAL = 20;

    private static final class Entry {
        final Entity entity;
        final List<float[]> sources = new ArrayList<>(2);
        float effective = 1f;
        float sent = 1f;
        float acc;

        Entry(Entity entity) {
            this.entity = entity;
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static long now;

    /** Applies a time scale to {@code entity} for {@code ticks} ticks (merged with other sources: the slowest wins). */
    public static void apply(Entity entity, float scale, int ticks) {
        if (entity.level().isClientSide || ticks <= 0) {
            return;
        }
        Entry entry = ENTRIES.get(entity.getId());
        if (entry == null || entry.entity != entity) {
            entry = new Entry(entity);
            ENTRIES.put(entity.getId(), entry);
        }
        float s = Math.max(0f, Math.min(1f, scale));
        long expires = now + ticks;
        for (float[] src : entry.sources) {
            if (src[0] == s) {
                src[1] = Math.max(src[1], expires);
                refresh(entry);
                return;
            }
        }
        entry.sources.add(new float[]{s, expires});
        refresh(entry);
    }

    public static float scale(Entity entity) {
        Entry entry = ENTRIES.get(entity.getId());
        return entry == null || entry.entity != entity ? 1f : entry.effective;
    }

    public static boolean isFrozen(Entity entity) {
        return scale(entity) <= 0f;
    }

    /** Removes every time effect from {@code entity} immediately. */
    public static void clear(Entity entity) {
        Entry entry = ENTRIES.get(entity.getId());
        if (entry != null && entry.entity == entity) {
            entry.sources.clear();
            refresh(entry);
        }
    }

    private static void refresh(Entry entry) {
        float eff = 1f;
        for (float[] src : entry.sources) {
            eff = Math.min(eff, src[0]);
        }
        if (eff != entry.effective) {
            entry.effective = eff;
            if (entry.entity instanceof Player player) {
                applyPlayerModifiers(player, eff);
            }
        }
    }

    private static void applyPlayerModifiers(Player player, float scale) {
        setModifier(player, Attributes.MOVEMENT_SPEED, scale - 1.0);
        setModifier(player, Attributes.JUMP_STRENGTH, scale <= 0.05f ? -1.0 : (scale - 1.0) * 0.5);
        setModifier(player, Attributes.ATTACK_SPEED, scale - 1.0);
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MODIFIER);
        if (amount < -1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof Player || ENTRIES.isEmpty()) {
            return;
        }
        Entry entry = ENTRIES.get(entity.getId());
        if (entry == null || entry.entity != entity || entry.effective >= 1f) {
            return;
        }
        entry.acc += entry.effective;
        if (entry.acc >= 1f) {
            entry.acc -= 1f;
        } else {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        now++;
        if (ENTRIES.isEmpty()) {
            return;
        }
        boolean resync = now % RESYNC_INTERVAL == 0;
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            Entity entity = entry.entity;
            if (entity.isRemoved()) {
                it.remove();
                continue;
            }
            entry.sources.removeIf(src -> src[1] <= now);
            refresh(entry);
            if (entry.effective != entry.sent || resync && entry.effective < 1f) {
                entry.sent = entry.effective;
                if (entity.level() instanceof ServerLevel level) {
                    ModNetwork.sendNear(level, entity.position(), 160, new TimeScalePayload(entity.getId(), entry.effective));
                }
            }
            if (entry.sources.isEmpty()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        ENTRIES.clear();
    }

    public static long now() {
        return now;
    }

    private TimeManager() {
    }
}

package dev.infinitygauntlet.time;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Server-side movement history used by Rewind, Temporal Echo and Temporal Loop. Only position, rotation and velocity
 * are recorded (never health, inventories or world state). To stay cheap it only records living entities and
 * projectiles within range of a player carrying a gauntlet with the Time Stone.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class EntityHistory {
    public record Frame(Vec3 pos, float yRot, float xRot, float headRot, Vec3 velocity) {
    }

    private static final double RANGE = 64;
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static long tick;

    private static final class Track {
        final Entity entity;
        final ArrayDeque<Frame> frames = new ArrayDeque<>();
        long lastSeen;

        Track(Entity entity) {
            this.entity = entity;
        }
    }

    /** Recorded frames, newest first. */
    public static List<Frame> history(Entity entity) {
        Track track = TRACKS.get(entity.getId());
        if (track == null || track.entity != entity) {
            return List.of();
        }
        List<Frame> list = new ArrayList<>(track.frames);
        java.util.Collections.reverse(list);
        return list;
    }

    public static Frame snapshot(Entity e) {
        return new Frame(e.position(), e.getYRot(), e.getXRot(), e.getYHeadRot(), e.getDeltaMovement());
    }

    public static boolean carriesTimeStone(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof InfinityGauntletItem) {
                GauntletState state = InfinityGauntletItem.state(stack);
                if (state.has(InfinityStone.TIME)) {
                    return true;
                }
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof InfinityGauntletItem && InfinityGauntletItem.state(off).has(InfinityStone.TIME);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        int capacity = ServerConfig.SPEC.isLoaded() ? ServerConfig.REWIND_HISTORY_TICKS.get() : 100;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !carriesTimeStone(player)) {
                    continue;
                }
                for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(RANGE),
                        e -> e instanceof LivingEntity || e instanceof Projectile)) {
                    record(e, capacity);
                }
                record(player, capacity);
            }
        }
        Iterator<Track> it = TRACKS.values().iterator();
        while (it.hasNext()) {
            Track track = it.next();
            if (track.entity.isRemoved() || tick - track.lastSeen > 40) {
                it.remove();
            }
        }
    }

    private static void record(Entity e, int capacity) {
        Track track = TRACKS.get(e.getId());
        if (track == null || track.entity != e) {
            track = new Track(e);
            TRACKS.put(e.getId(), track);
        }
        if (track.lastSeen == tick) {
            return;
        }
        track.lastSeen = tick;
        // a frozen entity has no new history; don't fill the buffer with copies of its frozen pose
        if (TimeManager.isFrozen(e) || RewindController.isRewinding(e)) {
            return;
        }
        track.frames.addLast(snapshot(e));
        while (track.frames.size() > capacity) {
            track.frames.removeFirst();
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TRACKS.clear();
    }

    private EntityHistory() {
    }
}

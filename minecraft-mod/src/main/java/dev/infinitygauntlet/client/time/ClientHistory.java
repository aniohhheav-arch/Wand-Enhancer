package dev.infinitygauntlet.client.time;

import dev.infinitygauntlet.client.HeldGauntlet;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Short client-side pose history of nearby living entities, used to draw afterimages. It only records while a time
 * effect is visible or the local player holds a gauntlet with the Time Stone.
 */
public final class ClientHistory {
    public record Frame(double x, double y, double z, float bodyRot, float headRot, float xRot, float limbPos, float limbSpeed, float age) {
    }

    private static final int CAPACITY = 40;
    private static final double RANGE = 48;
    private static final Map<Integer, ArrayDeque<Frame>> FRAMES = new HashMap<>();
    private static long tick;
    private static long activeUntil;

    public static void keepAlive(int ticks) {
        activeUntil = Math.max(activeUntil, tick + ticks);
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (tick < activeUntil) {
            return true;
        }
        if (mc.player == null) {
            return false;
        }
        ItemStack stack = HeldGauntlet.find(mc.player);
        return !stack.isEmpty() && InfinityGauntletItem.state(stack).has(InfinityStone.TIME);
    }

    public static void tick() {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !active()) {
            FRAMES.clear();
            return;
        }
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e.position().distanceToSqr(cam) > RANGE * RANGE) {
                continue;
            }
            ArrayDeque<Frame> q = FRAMES.computeIfAbsent(e.getId(), id -> new ArrayDeque<>());
            q.addFirst(new Frame(e.getX(), e.getY(), e.getZ(), living.yBodyRot, living.yHeadRot, e.getXRot(), living.walkAnimation.position(),
                    living.walkAnimation.speed(), e.tickCount));
            while (q.size() > CAPACITY) {
                q.removeLast();
            }
        }
        if (tick % 40 == 0) {
            Iterator<Integer> it = FRAMES.keySet().iterator();
            while (it.hasNext()) {
                Entity e = mc.level.getEntity(it.next());
                if (e == null || e.isRemoved()) {
                    it.remove();
                }
            }
        }
    }

    /** Frame {@code ticksAgo} ticks back (0 = latest), or null if not recorded. */
    public static Frame get(int entityId, int ticksAgo) {
        ArrayDeque<Frame> q = FRAMES.get(entityId);
        if (q == null || q.size() <= ticksAgo) {
            return null;
        }
        int i = 0;
        for (Frame f : q) {
            if (i++ == ticksAgo) {
                return f;
            }
        }
        return null;
    }

    public static void clear() {
        FRAMES.clear();
        activeUntil = 0;
    }

    private ClientHistory() {
    }
}

package dev.infinitygauntlet.time;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plays recorded movement backwards. The entity's own ticks are suspended while it is dragged back through its
 * frames (so its AI cannot fight the rewind), and the velocity it had at the final frame is restored at the end.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class RewindController {
    private static final int FRAMES_PER_TICK = 2;

    private static final class Rewind {
        final Entity entity;
        final List<EntityHistory.Frame> frames;
        int index;

        Rewind(Entity entity, List<EntityHistory.Frame> frames) {
            this.entity = entity;
            this.frames = frames;
        }
    }

    private static final Map<Integer, Rewind> ACTIVE = new HashMap<>();

    /** Starts rewinding {@code entity} through at most {@code maxFrames} recorded frames. Returns the rewind length in ticks. */
    public static int start(Entity entity, int maxFrames) {
        List<EntityHistory.Frame> history = EntityHistory.history(entity);
        if (history.size() < 4) {
            return 0;
        }
        List<EntityHistory.Frame> frames = new ArrayList<>(history.subList(0, Math.min(maxFrames, history.size())));
        ACTIVE.put(entity.getId(), new Rewind(entity, frames));
        int ticks = (frames.size() + FRAMES_PER_TICK - 1) / FRAMES_PER_TICK;
        TimeManager.apply(entity, 0f, ticks + 1);
        if (entity.level() instanceof ServerLevel level) {
            Vfx.send(level, VfxType.AFTERIMAGES, entity.position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 1f,
                    ticks + 10, entity.getId());
        }
        return ticks;
    }

    public static boolean isRewinding(Entity entity) {
        Rewind r = ACTIVE.get(entity.getId());
        return r != null && r.entity == entity;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Rewind> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Rewind r = it.next();
            Entity e = r.entity;
            if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            r.index = Math.min(r.frames.size() - 1, r.index + FRAMES_PER_TICK);
            EntityHistory.Frame f = r.frames.get(r.index);
            Vec3 before = e.position();
            if (e instanceof ServerPlayer player) {
                player.connection.teleport(f.pos().x, f.pos().y, f.pos().z, f.yRot(), f.xRot());
            } else {
                e.moveTo(f.pos().x, f.pos().y, f.pos().z, f.yRot(), f.xRot());
                e.setYHeadRot(f.headRot());
                if (e instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
            e.fallDistance = 0;
            if (r.index % 6 == 0) {
                Vfx.send(level, VfxType.LINK, before.add(0, e.getBbHeight() / 2, 0), f.pos().add(0, e.getBbHeight() / 2, 0),
                        InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 0.15f, 10);
            }
            if (r.index >= r.frames.size() - 1) {
                e.setDeltaMovement(f.velocity());
                e.hurtMarked = true;
                TimeManager.clear(e);
                Vfx.shockwave(level, e.position().add(0, e.getBbHeight() / 2, 0), new Vec3(0, 1, 0), InfinityStone.TIME,
                        Math.max(1.5f, e.getBbHeight() * 1.5f), 12);
                Vfx.sound(level, e.position(), ModSounds.TIME_RESUME, 0.8f, 1.3f);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private RewindController() {
    }
}

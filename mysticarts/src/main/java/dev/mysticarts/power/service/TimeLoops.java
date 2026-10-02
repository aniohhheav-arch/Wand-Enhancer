package dev.mysticarts.power.service;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.registry.MaSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Time loops: an entity's movement is recorded for a few seconds, then it is snapped back and forced to relive that
 * exact path, several times over. Its AI keeps trying, but every tick it is put back where it was.
 */
public final class TimeLoops {
    private static final class Loop {
        final ResourceKey<Level> dim;
        final int entity;
        final int length;
        int loopsLeft;
        final List<Vec3> path = new ArrayList<>();
        final List<float[]> rot = new ArrayList<>();
        int index = -1;

        Loop(ResourceKey<Level> dim, int entity, int length, int loops) {
            this.dim = dim;
            this.entity = entity;
            this.length = length;
            this.loopsLeft = loops;
        }
    }

    private static final List<Loop> LOOPS = new ArrayList<>();

    private TimeLoops() {}

    public static void start(Entity e, int length, int loops) {
        LOOPS.removeIf(l -> l.entity == e.getId() && l.dim == e.level().dimension());
        LOOPS.add(new Loop(e.level().dimension(), e.getId(), length, loops));
        EntityMarks.add(e, MarkKind.LOOPING, length * (loops + 1) + 5, length);
    }

    public static void tick(ServerLevel level) {
        Iterator<Loop> it = LOOPS.iterator();
        while (it.hasNext()) {
            Loop l = it.next();
            if (l.dim != level.dimension()) continue;
            Entity e = level.getEntity(l.entity);
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (l.index < 0) {
                l.path.add(e.position());
                l.rot.add(new float[] {e.getYRot(), e.getXRot()});
                if (l.path.size() >= l.length) {
                    l.index = 0;
                    loopFlash(level, e, l);
                }
                continue;
            }
            Vec3 p = l.path.get(l.index);
            float[] r = l.rot.get(l.index);
            e.teleportTo(p.x, p.y, p.z);
            e.setYRot(r[0]);
            e.setXRot(r[1]);
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob m) m.getNavigation().stop();
            if (++l.index >= l.path.size()) {
                if (--l.loopsLeft <= 0) {
                    EntityMarks.remove(e, MarkKind.LOOPING);
                    Fx.send(level, FxKind.TIME_RESUME, e.position().add(0, 1, 0), 0x22E06A, 2f, 0);
                    it.remove();
                } else {
                    l.index = 0;
                    loopFlash(level, e, l);
                }
            }
        }
    }

    private static void loopFlash(ServerLevel level, Entity e, Loop l) {
        Vec3 start = l.path.get(0);
        Fx.send(level, FxKind.CLOCKS, start.add(0, e.getBbHeight() * 0.5, 0), new Vec3(0, 1, 0), 0x22E06A, 1.4f, 16, -1);
        level.playSound(null, start.x, start.y, start.z, MaSounds.TIME_LOOP.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1f);
    }

    public static void clear() {
        LOOPS.clear();
    }
}

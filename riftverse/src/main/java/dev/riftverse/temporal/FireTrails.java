package dev.riftverse.temporal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Burning tyre tracks: flame points that keep burning on the ground for a while after the car has passed. */
public final class FireTrails {
    private record Point(ServerLevel level, Vec3 pos, int[] life) {}

    private static final List<Point> POINTS = new ArrayList<>();

    private FireTrails() {}

    public static void add(ServerLevel level, Vec3 pos, int ticks) {
        if (POINTS.size() > 600) POINTS.remove(0);
        POINTS.add(new Point(level, pos, new int[] {ticks}));
    }

    public static void tick() {
        for (Iterator<Point> it = POINTS.iterator(); it.hasNext(); ) {
            Point p = it.next();
            if (--p.life[0] <= 0) {
                it.remove();
                continue;
            }
            if (p.life[0] % 2 == 0) {
                p.level.sendParticles(ParticleTypes.FLAME, p.pos.x, p.pos.y + 0.05, p.pos.z, 2, 0.12, 0.02, 0.12, 0.005);
                if (p.life[0] % 6 == 0) p.level.sendParticles(ParticleTypes.SMOKE, p.pos.x, p.pos.y + 0.2, p.pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    public static void clear() {
        POINTS.clear();
    }
}

package dev.telephoneorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Layered, timeline-driven explosion VFX: white-hot flash, directional fireball, sparks, flying debris from the block underneath,
 * a ground-hugging shockwave, a rising smoke column and (for the big ones) a rolling toroidal mushroom cloud.
 * Everything is vanilla particles sent with a velocity (count 0), so one packet = one properly moving particle.
 */
public final class Blast {
    private static final Random R = new Random();

    private Blast() { }

    private static Vec3 unit() {
        double x = R.nextGaussian(), y = R.nextGaussian(), z = R.nextGaussian();
        double l = Math.sqrt(x * x + y * y + z * z);
        return l < 1e-4 ? new Vec3(0, 1, 0) : new Vec3(x / l, y / l, z / l);
    }

    /** `n` particles flying outward in a sphere (up-biased by `up`). */
    public static void burst(ServerLevel l, ParticleOptions o, Vec3 at, int n, double speed, double up) {
        n = Fx.scaled(n);
        for (int i = 0; i < n; i++) {
            Vec3 v = unit();
            l.sendParticles(o, at.x, at.y, at.z, 0, v.x, v.y + up, v.z, speed * (0.35 + R.nextDouble() * 0.65));
        }
    }

    /** A cone/fountain of particles around +Y. */
    public static void fountain(ServerLevel l, ParticleOptions o, Vec3 at, int n, double speed, double spread) {
        n = Fx.scaled(n);
        for (int i = 0; i < n; i++) {
            l.sendParticles(o, at.x, at.y, at.z, 0, (R.nextDouble() - 0.5) * spread, 1.0, (R.nextDouble() - 0.5) * spread, speed * (0.5 + R.nextDouble() * 0.5));
        }
    }

    private static BlockState groundAt(ServerLevel l, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i < 4; i++) {
            BlockState s = l.getBlockState(p.below(i));
            if (!s.isAir()) return s;
        }
        return Blocks.DIRT.defaultBlockState();
    }

    /** Expanding dusty ring along the ground, with kicked-up debris. */
    public static void shockwave(ServerLevel l, Vec3 c, double maxR) {
        BlockState ground = groundAt(l, c);
        int steps = Math.max(6, (int) (maxR * 0.9));
        for (int t = 0; t <= steps; t++) {
            int tt = t;
            Sched.later(t, () -> {
                double f = tt / (double) steps;
                double r = Math.max(0.5, maxR * (1 - (1 - f) * (1 - f)));
                int n = Fx.scaled(10 + (int) (r * 5));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * (i + R.nextDouble() * 0.5) / n;
                    double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                    l.sendParticles(Fx.dust(0xD9CDB8, 1.6f), x, c.y + 0.15, z, 0, Math.cos(a) * 0.1, 0.04, Math.sin(a) * 0.1, 1);
                    if (i % 3 == 0) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, c.y + 0.2, z, 0, Math.cos(a) * 0.1, 0.45, Math.sin(a) * 0.1, 0.5);
                    if (i % 5 == 0) l.sendParticles(ParticleTypes.POOF, x, c.y + 0.3, z, 0, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1, 0.6);
                }
            });
        }
    }

    /** One-shot explosion. `size` is roughly the radius in blocks (1 = grenade, 5 = meteor, 8+ = huge). */
    public static void explosion(ServerLevel l, Vec3 at, float size) {
        Vec3 core = at.add(0, Math.min(1.5, size * 0.25), 0);
        l.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), core.x, core.y, core.z, 1, 0, 0, 0, 0);
        l.sendParticles(size >= 3 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, core.x, core.y, core.z, 1, 0, 0, 0, 0);
        burst(l, ParticleTypes.FLAME, core, (int) (16 + size * 7), 0.16 + size * 0.035, 0.2);
        burst(l, Fx.dust(0xFFB040, 1.8f), core, (int) (14 + size * 5), 0.22 + size * 0.03, 0.15);
        burst(l, ParticleTypes.ELECTRIC_SPARK, core, (int) (10 + size * 4), 0.55 + size * 0.06, 0.25);
        burst(l, ParticleTypes.LAVA, core, (int) (4 + size * 2), 0.3, 0.5);
        burst(l, ParticleTypes.LARGE_SMOKE, core, (int) (8 + size * 3), 0.07 + size * 0.02, 0.15);
        BlockState g = groundAt(l, at);
        burst(l, new BlockParticleOption(ParticleTypes.BLOCK, g), at.add(0, 0.3, 0), (int) (14 + size * 6), 0.4 + size * 0.05, 0.9);
        shockwave(l, at, size * 2.4);
        // delayed secondary fire and a smoke column that rises and thins out
        int rise = (int) (14 + size * 5);
        for (int k = 2; k < rise; k += 2) {
            int kk = k;
            Sched.later(k, () -> {
                Vec3 p = at.add(0, kk * 0.18 * (0.6 + size * 0.15), 0);
                for (int i = 0; i < Fx.scaled(2 + (int) (size * 0.6)); i++) {
                    l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x + (R.nextDouble() - 0.5) * size * 0.35, p.y, p.z + (R.nextDouble() - 0.5) * size * 0.35,
                            0, 0, 1, 0, 0.04 + R.nextDouble() * 0.05);
                }
                if (kk < rise * 0.5) burst(l, ParticleTypes.FLAME, p, 3, 0.06, 0.5);
                if (kk % 4 == 0) burst(l, ParticleTypes.SMOKE, p, 3, 0.08, 0.3);
            });
        }
    }

    /** A rolling mushroom cloud: fireball, swirling stem, toroidal cap and a spreading skirt. `s` = scale (1 = ~12 blocks tall). */
    public static void mushroom(ServerLevel l, Vec3 c, float s) {
        // fireball
        for (int k = 0; k < 18; k++) {
            int kk = k;
            Sched.later(k, () -> {
                double r = (2.0 + kk * 0.55) * s;
                Vec3 mid = c.add(0, r * 0.7, 0);
                int n = Fx.scaled(26);
                for (int i = 0; i < n; i++) {
                    Vec3 u = unit();
                    Vec3 pt = mid.add(u.scale(r));
                    int col = R.nextBoolean() ? 0xFFD060 : 0xFF7A20;
                    l.sendParticles(Fx.dust(col, 3.2f), pt.x, pt.y, pt.z, 0, u.x * 0.05, 0.1 + u.y * 0.04, u.z * 0.05, 1);
                    if (i % 4 == 0) l.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 0, u.x, u.y + 0.3, u.z, 0.08 * s);
                }
                if (kk % 4 == 0) l.sendParticles(ParticleTypes.EXPLOSION, mid.x + (R.nextDouble() - 0.5) * r, mid.y + (R.nextDouble() - 0.5) * r, mid.z + (R.nextDouble() - 0.5) * r, 1, 0, 0, 0, 0);
            });
        }
        // stem + cap
        int life = 130;
        for (int k = 0; k < life; k++) {
            int kk = k;
            Sched.later(4 + k, () -> {
                double f = kk / (double) life;
                double topH = (3 + 26 * (1 - Math.pow(1 - f, 2.2))) * s;       // fast then slow rise
                double age = f;
                // stem: swirling column from the ground to the cap
                for (double y = 0.5; y < topH; y += 2.4 * s) {
                    double a = kk * 0.45 + y * 0.35, rad = (1.6 + 0.7 * Math.sin(y * 0.8)) * s * (1.0 - 0.25 * (y / topH));
                    int col = y < topH * 0.4 ? 0xFF8A30 : 0x6B5C50;
                    if (age > 0.5) col = 0x4B4540;
                    l.sendParticles(Fx.dust(col, 3.0f), c.x + Math.cos(a) * rad, c.y + y, c.z + Math.sin(a) * rad, 0, Math.cos(a) * 0.02, 0.1, Math.sin(a) * 0.02, 1);
                }
                // cap: smoke torus that rolls (outer surface moves down, inner surface moves up)
                double capR = (4.5 + 11 * Math.min(1, f * 1.6)) * s;
                double minor = (2.2 + 3.0 * Math.min(1, f * 1.6)) * s;
                Vec3 centre = c.add(0, topH, 0);
                int n = Fx.scaled(34);
                for (int i = 0; i < n; i++) {
                    double phi = Math.PI * 2 * i / n + kk * 0.03, th = R.nextDouble() * Math.PI * 2;
                    double rr = capR + Math.cos(th) * minor;
                    Vec3 pt = centre.add(Math.cos(phi) * rr, Math.sin(th) * minor * 0.85, Math.sin(phi) * rr);
                    double rad = Math.cos(phi), rz = Math.sin(phi);
                    double vr = -Math.sin(th) * 0.10, vy = Math.cos(th) * 0.10;
                    int col = f < 0.35 ? (R.nextInt(3) == 0 ? 0xFF9A3A : 0x8A7664) : 0x5A524C;
                    l.sendParticles(Fx.dust(col, 3.4f), pt.x, pt.y, pt.z, 0, rad * vr, vy, rz * vr, 1);
                    if (i % 6 == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, pt.x, pt.y, pt.z, 0, rad * vr * 0.6, vy * 0.6, rz * vr * 0.6, 1);
                }
                // skirt: dusty ring creeping outward near the ground
                if (kk % 3 == 0) {
                    double r = Math.min(14 * s, 3 + kk * 0.18 * s);
                    int m = Fx.scaled(20);
                    for (int i = 0; i < m; i++) {
                        double a = Math.PI * 2 * i / m;
                        l.sendParticles(Fx.dust(0x8C7F70, 2.6f), c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r, 0, Math.cos(a) * 0.04, 0.05, Math.sin(a) * 0.04, 1);
                    }
                }
            });
        }
        shockwave(l, c, 14 * s);
    }

    /** A glowing impact where a vertical beam meets the ground: rings, sparks and heat shimmer. Call every tick or two. */
    public static void beamImpact(ServerLevel l, Vec3 c, double radius, int tick, int rgb) {
        double r = radius * (0.7 + 0.3 * Math.sin(tick * 0.6));
        Fx.ring(l, Fx.dust(rgb, 2.2f), c, r, 28, 0.12);
        Fx.ring(l, Fx.dust(0xFFFFFF, 1.6f), c, r * 0.55, 18, 0.2);
        fountain(l, ParticleTypes.ELECTRIC_SPARK, c.add(0, 0.2, 0), 8, 0.9, 1.4);
        fountain(l, ParticleTypes.FLAME, c.add(0, 0.2, 0), 4, 0.35, 1.0);
        burst(l, new BlockParticleOption(ParticleTypes.BLOCK, groundAt(l, c)), c.add(0, 0.3, 0), 6, 0.5, 1.0);
        if (tick % 6 == 0) burst(l, ParticleTypes.LARGE_SMOKE, c.add(0, 0.5, 0), 4, 0.12, 0.3);
    }

    /** Streaking, spiralling fire trail for something falling or flying at `p` (meteors, warheads). */
    public static void trail(ServerLevel l, Vec3 p, Vec3 away, double size, int tick) {
        for (int i = 0; i < Fx.scaled(5); i++) {
            double a = tick * 0.9 + i * 1.2;
            Vec3 o = new Vec3(Math.cos(a) * size * 0.45, (R.nextDouble() - 0.5) * size * 0.3, Math.sin(a) * size * 0.45);
            l.sendParticles(ParticleTypes.FLAME, p.x + o.x, p.y + o.y, p.z + o.z, 0, away.x, away.y, away.z, 0.12);
        }
        l.sendParticles(Fx.dust(0xFFC060, (float) (1.8 + size * 0.5)), p.x, p.y, p.z, 0, away.x, away.y, away.z, 0.25);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, away.x, away.y, away.z, 0.18);
        if (tick % 2 == 0) l.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 0, away.x, away.y, away.z, 0.1);
    }
}

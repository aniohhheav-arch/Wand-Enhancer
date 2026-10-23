package com.wandcraft.magic.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-side smoke, tendril and stream particles shared by the Dark Clone and its possession. */
public final class ShadowFx {
    public static final int DEEP = 0x140A22, VIOLET = 0x7A2BD6, BRIGHT = 0xB06BFF, ASH = 0x5A5762;

    private ShadowFx() {}

    public static ParticleOptions smoke(float size) { return Fx.wisp(DEEP, size); }

    public static void puff(ServerLevel l, Vec3 at, int n, double spread) {
        Fx.burst(l, smoke(1.3f), at, n, spread, 0.03);
        Fx.burst(l, Fx.glow(VIOLET, 0.8f), at, Math.max(2, n / 2), spread, 0.04);
        Fx.burst(l, Fx.spark(BRIGHT, 0.6f), at, Math.max(2, n / 3), spread, 0.08);
    }

    /** Wavering black tendril between two points. */
    public static void tendril(ServerLevel l, Vec3 a, Vec3 b, double phase) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 n = d.scale(1 / len);
        Vec3 side = n.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(n).normalize();
        int steps = Math.max(6, (int) (len * 4));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, w = Math.sin(t * Math.PI);
            Vec3 p = a.add(d.scale(t)).add(side.scale(Math.sin(t * 9 + phase) * 0.2 * w)).add(up.scale(Math.cos(t * 7 + phase) * 0.2 * w));
            l.sendParticles(smoke(0.7f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 3 == 0) l.sendParticles(Fx.glow(BRIGHT, 0.45f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Smoke pouring from {@code a} into {@code b}; density is the particle count. */
    public static void stream(ServerLevel l, Vec3 a, Vec3 b, int count, double jitter) {
        Vec3 d = b.subtract(a);
        for (int i = 0; i < count; i++) {
            double t = l.random.nextDouble();
            Vec3 p = a.add(d.scale(t));
            double w = Math.sin(t * Math.PI) * jitter;
            l.sendParticles(smoke(0.9f), p.x + (l.random.nextDouble() - .5) * w, p.y + (l.random.nextDouble() - .5) * w, p.z + (l.random.nextDouble() - .5) * w,
                    1, d.x * 0.02, d.y * 0.02, d.z * 0.02, 0.15);
            if (i % 4 == 0) l.sendParticles(Fx.glow(VIOLET, 0.5f), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    /** Particles falling inward toward a centre. */
    public static void implode(ServerLevel l, Vec3 c, double radius, int n) {
        for (int i = 0; i < Fx.scaled(n); i++) {
            double u = l.random.nextDouble() * 2 - 1, a = l.random.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
            Vec3 dir = new Vec3(q * Math.cos(a), u, q * Math.sin(a));
            Vec3 p = c.add(dir.scale(radius));
            Vec3 v = dir.scale(-radius * 0.12);
            l.sendParticles(i % 3 == 0 ? Fx.glow(VIOLET, 0.6f) : smoke(0.9f), p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
        }
    }

    public static ParticleOptions ash(float size) { return Fx.wisp(ASH, size); }
}

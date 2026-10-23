package com.wandcraft.magic.util;

import com.wandcraft.Config;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Particle and sound helpers. Everything is scaled by the configured effect intensity. */
public final class Fx {
    private Fx() {}

    public static int scaled(int count) { return Math.max(1, (int) Math.round(count * Config.FX_INTENSITY.get())); }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), size);
    }

    public static void burst(ServerLevel l, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        l.sendParticles(p, at.x, at.y, at.z, scaled(count), spread, spread, spread, speed);
    }

    public static void burst(ServerLevel l, ParticleOptions p, Vec3 at, int count, double sx, double sy, double sz, double speed) {
        l.sendParticles(p, at.x, at.y, at.z, scaled(count), sx, sy, sz, speed);
    }

    /** Horizontal ring of particles. */
    public static void ring(ServerLevel l, ParticleOptions p, Vec3 c, double radius, int points) {
        int n = scaled(points);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            l.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Expanding ring that travels outward from the centre over a few ticks. */
    public static void shockwave(ServerLevel l, ParticleOptions p, Vec3 c, double maxRadius, int steps) {
        for (int i = 1; i <= steps; i++) {
            final double r = maxRadius * i / steps;
            Scheduler.after(l, i, () -> ring(l, p, c, r, (int) (8 + r * 8)));
        }
    }

    public static void line(ServerLevel l, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 n = d.scale(1 / len);
        double s = step / Config.FX_INTENSITY.get();
        for (double t = 0; t <= len; t += s) {
            Vec3 q = a.add(n.scale(t));
            l.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** A glowing geometric rune circle: outer ring plus an inscribed star, tinted with {@code rgb}. */
    public static void rune(ServerLevel l, Vec3 c, double radius, int rgb, int points, double rotation) {
        DustParticleOptions d = dust(rgb, 1.1f);
        ring(l, d, c, radius, points);
        int sides = 5;
        for (int i = 0; i < sides; i++) {
            double a1 = rotation + Math.PI * 2 * i * 2 / sides;
            double a2 = rotation + Math.PI * 2 * (i + 1) * 2 / sides;
            Vec3 p1 = c.add(Math.cos(a1) * radius * 0.92, 0, Math.sin(a1) * radius * 0.92);
            Vec3 p2 = c.add(Math.cos(a2) * radius * 0.92, 0, Math.sin(a2) * radius * 0.92);
            line(l, d, p1, p2, 0.4);
        }
    }

    public static void sound(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Client-side single particle, thinned by the client intensity setting. */
    public static void c(Level l, ParticleOptions p, double x, double y, double z, double dx, double dy, double dz) {
        if (l.random.nextFloat() <= Config.CLIENT_FX_INTENSITY.get()) l.addParticle(p, x, y, z, dx, dy, dz);
    }

    /** Random points on a sphere surface (client side), for orbs and domes. */
    public static void cSphere(Level l, ParticleOptions p, Vec3 c, double r, int n) {
        for (int i = 0; i < n; i++) {
            double u = l.random.nextDouble() * 2 - 1, a = l.random.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
            c(l, p, c.x + q * Math.cos(a) * r, c.y + u * r, c.z + q * Math.sin(a) * r, 0, 0, 0);
        }
    }
}

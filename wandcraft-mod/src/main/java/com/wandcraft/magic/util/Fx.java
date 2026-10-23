package com.wandcraft.magic.util;

import com.wandcraft.Config;
import com.wandcraft.ModParticles;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Particle and sound helpers. Everything is scaled by the configured effect intensity. */
public final class Fx {
    private Fx() {}

    public static int scaled(int count) { return Math.max(1, (int) Math.round(count * Config.FX_INTENSITY.get())); }

    private static ColorParticleOption tint(net.minecraft.core.particles.ParticleType<ColorParticleOption> t, int rgb, float size, float unit) {
        int a = Math.max(1, Math.min(255, Math.round(size * unit)));
        return ColorParticleOption.create(t, (a << 24) | (rgb & 0xFFFFFF));
    }

    /** Soft glowing orb tinted {@code rgb}; {@code size} is roughly 0.5 (tiny) to 2 (big). */
    public static ParticleOptions dust(int rgb, float size) { return tint(ModParticles.GLOW.get(), rgb, size, 100); }
    public static ParticleOptions glow(int rgb, float size) { return dust(rgb, size); }
    /** Four-pointed flare that spins and shrinks. */
    public static ParticleOptions spark(int rgb, float size) { return tint(ModParticles.SPARK.get(), rgb, size, 100); }
    /** Drifting smoke-like curl. */
    public static ParticleOptions wisp(int rgb, float size) { return tint(ModParticles.WISP.get(), rgb, size, 100); }
    /** Flat rune circle lying on the ground; {@code radius} in blocks. */
    public static ParticleOptions runeDecal(int rgb, float radius) { return tint(ModParticles.RUNE.get(), rgb, radius, 20); }

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

    /** A glowing rune circle on the ground: a flat spinning decal plus a ring of flares. */
    public static void rune(ServerLevel l, Vec3 c, double radius, int rgb, int points, double rotation) {
        l.sendParticles(runeDecal(rgb, (float) radius), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        ParticleOptions sp = spark(rgb, 0.7f);
        int n = Math.max(4, scaled(points) / 2);
        for (int i = 0; i < n; i++) {
            double a = rotation + Math.PI * 2 * i / n;
            l.sendParticles(sp, c.x + Math.cos(a) * radius, c.y + 0.05, c.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0.01);
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

package com.stickyfingers.ability;

import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.network.ScreenFxPayload;
import com.stickyfingers.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helpers that fan out sounds, particles and screen effects to clients. */
public final class Fx {
    public static void screen(ServerPlayer player, ScreenFx fx, float intensity, int duration) {
        PacketDistributor.sendToPlayer(player, new ScreenFxPayload(fx.ordinal(), intensity, duration));
    }

    /** Camera shake for everyone near a point, falling off with distance. */
    public static void shake(ServerLevel level, Vec3 at, double radius, float intensity, int duration) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d < radius) screen(p, ScreenFx.SHAKE, (float) (intensity * (1 - d / radius)), duration);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void particles(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Line of sparks + teeth between two points, used for zipper chains and dashes. */
    public static void seam(ServerLevel level, Vec3 from, Vec3 to, int steps) {
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ModParticles.SEAM_GLOW.get(), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 3 == 0) level.sendParticles(ModParticles.ZIPPER_SPARK.get(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
        }
    }

    public static void afterimage(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = (int) Math.max(4, from.distanceTo(to) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ModParticles.AFTERIMAGE.get(), p.x, p.y + 1.0, p.z, 1, 0.1, 0.4, 0.1, 0.0);
        }
    }

    private Fx() {}
}

package dev.mysticarts.fx;

import dev.mysticarts.network.Payloads;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helpers that broadcast effects and sounds to everyone who can see them. */
public final class Fx {
    public static final double RANGE = 96;

    private Fx() {}

    public static void send(ServerLevel level, int kind, Vec3 pos, Vec3 dir, int color, float a, float b, int entity) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, kind == FxKind.WAVE || kind == FxKind.SNAP ? 256 : RANGE,
                new Payloads.Fx(kind, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, color, a, b, entity));
    }

    public static void send(ServerLevel level, int kind, Vec3 pos, int color, float a, float b) {
        send(level, kind, pos, Vec3.ZERO, color, a, b, -1);
    }

    public static void to(ServerPlayer player, int kind, Vec3 pos, int color, float a, float b) {
        PacketDistributor.sendToPlayer(player, new Payloads.Fx(kind, pos.x, pos.y, pos.z, 0, 0, 0, color, a, b, player.getId()));
    }

    public static void burst(ServerLevel level, Vec3 pos, int color, float size, float count) {
        send(level, FxKind.BURST, pos, Vec3.ZERO, color, size, count, -1);
    }

    public static void ring(ServerLevel level, Vec3 pos, Vec3 normal, int color, float radius, int ticks) {
        send(level, FxKind.RING, pos, normal, color, radius, ticks, -1);
    }

    public static void screen(ServerLevel level, Vec3 pos, double range, float shake, float flash, int color) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, range,
                new Payloads.Fx(FxKind.SCREEN, pos.x, pos.y, pos.z, 0, 0, 0, color, shake, flash, -1));
    }

    public static void teleport(ServerLevel level, Vec3 from, Vec3 to, int color) {
        send(level, FxKind.TELEPORT, from, to.subtract(from), color, 0.6f, 0, -1);
        burst(level, to, color, 1f, 1f);
    }

    public static void particles(ServerLevel level, ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    public static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch * (0.95f + at.level().random.nextFloat() * 0.1f));
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch * (0.95f + level.random.nextFloat() * 0.1f));
    }
}

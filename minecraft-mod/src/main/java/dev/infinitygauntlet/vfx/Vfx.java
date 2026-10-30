package dev.infinitygauntlet.vfx;

import dev.infinitygauntlet.network.CameraPayload;
import dev.infinitygauntlet.network.TransitionPayload;
import dev.infinitygauntlet.network.VfxPayload;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import dev.infinitygauntlet.network.ModNetwork;

/** Server-side entry point for requesting cinematic effects. Everything here is purely visual/audio. */
public final class Vfx {
    private static final double BROADCAST_RADIUS = 192;

    public static void send(ServerLevel level, VfxType type, Vec3 a, Vec3 b, int color, int color2, float scale, int duration) {
        VfxPayload payload = new VfxPayload(type, a, b, color, color2, scale, duration, level.random.nextInt());
        ModNetwork.sendNear(level, a, BROADCAST_RADIUS, payload);
    }

    public static void send(ServerLevel level, VfxType type, Vec3 a, InfinityStone stone, float scale, int duration) {
        send(level, type, a, Vec3.ZERO, stone.color(), stone.highlight(), scale, duration);
    }

    public static void shockwave(ServerLevel level, Vec3 center, Vec3 normal, InfinityStone stone, float radius, int duration) {
        send(level, VfxType.SHOCKWAVE, center, normal, stone.color(), stone.highlight(), radius, duration);
    }

    public static void link(ServerLevel level, Vec3 from, Vec3 to, InfinityStone stone, int duration) {
        send(level, VfxType.LINK, from, to, stone.color(), stone.highlight(), 0.25f, duration);
    }

    public static void arc(ServerLevel level, Vec3 from, Vec3 to, InfinityStone stone, int duration) {
        send(level, VfxType.ARC, from, to, stone.color(), stone.highlight(), 1f, duration);
    }

    public static void dissolve(ServerLevel level, Entity entity, InfinityStone stone) {
        send(level, VfxType.DISSOLVE, entity.position(), new Vec3(entity.getBbWidth(), entity.getBbHeight(), 0),
                stone.color(), stone.highlight(), 1f, 20);
    }

    /** Camera shake/zoom/flash for every player within {@code radius}, attenuated by distance on the client. */
    public static void camera(ServerLevel level, Vec3 origin, float radius, float shake, float zoom, int zoomTicks, int flashColor,
                              float flashAlpha, int flashTicks) {
        CameraPayload payload = new CameraPayload(origin, radius, shake, zoom, zoomTicks, flashColor, flashAlpha, flashTicks);
        ModNetwork.sendNear(level, origin, radius, payload);
    }

    public static void shake(ServerLevel level, Vec3 origin, float radius, float strength) {
        camera(level, origin, radius, strength, 0, 0, 0, 0, 0);
    }

    public static void transition(ServerPlayer player, int ticks, InfinityStone stone) {
        ModNetwork.sendTo(player, new TransitionPayload(ticks, stone.color()));
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private Vfx() {
    }
}

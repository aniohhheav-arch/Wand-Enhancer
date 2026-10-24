package dev.telephoneorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Server-side effect helpers (particles + positional sound). All sounds are real positional audio. */
public final class Fx {
    private static final java.util.Random RNG = new java.util.Random();
    private Fx() {}

    public static void sound(ServerLevel level, Vec3 pos, String name, SoundSource src, float vol, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.get(name), src, vol, pitch);
    }

    public static void sound(Entity e, String name, SoundSource src, float vol, float pitch) {
        if (e.level() instanceof ServerLevel l) sound(l, e.position(), name, src, vol, pitch);
    }

    public static void soundTo(ServerPlayer p, String name, SoundSource src, float vol, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get(name), src, vol, pitch);
    }

    /** Sound only audible to a single player (e.g. their personal ringtone). */
    public static void privateSound(ServerPlayer p, Vec3 at, String name, SoundSource src, float vol, float pitch) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(ModSounds.get(name)), src, at.x, at.y, at.z, vol, pitch, p.getRandom().nextLong()));
    }

    public static int scaled(int n) {
        return Math.max(1, Math.round(n * ModConfig.get().particleScale));
    }

    public static void p(ServerLevel l, ParticleOptions o, Vec3 at, int count, double dx, double dy, double dz, double speed) {
        l.sendParticles(o, at.x, at.y, at.z, scaled(count), dx, dy, dz, speed);
    }

    /** Small, directional muzzle flash: hot sparks, a short orange cone and a thin smoke puff (no big sprites in the shooter's face). */
    public static void muzzle(ServerLevel l, Vec3 mz, Vec3 view, float power) {
        Vec3 o = mz.add(view.scale(0.4));
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, o.x, o.y, o.z, 0, view.x, view.y, view.z, 0.35 * power);
        for (int i = 0; i < 5; i++) {
            double j = 0.10;
            Vec3 d = view.add((RNG.nextDouble() - 0.5) * j * 2, (RNG.nextDouble() - 0.5) * j * 2, (RNG.nextDouble() - 0.5) * j * 2);
            int c = i % 2 == 0 ? 0xFFE08A : 0xFF8A2A;
            l.sendParticles(dust(c, 0.45f * power), o.x, o.y, o.z, 0, d.x, d.y, d.z, 0.28 * power);
        }
        Vec3 sm = mz.add(view.scale(0.8));
        l.sendParticles(dust(0x9A9A9A, 0.8f * power), sm.x, sm.y, sm.z, 0, view.x, view.y + 0.05, view.z, 0.04);
        l.sendParticles(dust(0x6A6A6A, 1.0f * power), sm.x, sm.y, sm.z, 0, view.x, view.y + 0.08, view.z, 0.02);
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb & 0xFFFFFF, size);
    }

    /** A line of particles (tracers, beams). */
    public static void line(ServerLevel l, ParticleOptions o, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.01) return;
        Vec3 n = d.scale(1.0 / len);
        int count = (int) Math.min(220, Math.ceil(len / step));
        double inc = len / count;
        for (int i = 0; i <= count; i++) {
            Vec3 p = a.add(n.scale(inc * i));
            l.sendParticles(o, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    public static void blockImpact(ServerLevel l, BlockPos pos, Vec3 at, int count) {
        BlockState st = l.getBlockState(pos);
        if (st.isAir()) return;
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y, at.z, scaled(count), 0.08, 0.08, 0.08, 0.08);
    }

    public static void ring(ServerLevel l, ParticleOptions o, Vec3 c, double r, int n, double yy) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            l.sendParticles(o, c.x + Math.cos(a) * r, c.y + yy, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void shake(ServerLevel l, Vec3 at, double radius, float strength, int ticks) {
        for (ServerPlayer p : l.players()) {
            double d = p.position().distanceTo(at);
            if (d <= radius) {
                float s = (float) (strength * (1.0 - d / radius));
                if (s > 0.02f) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.telephoneorder.net.Payloads.Shake(s, ticks));
            }
        }
    }

    public static void flash(ServerLevel l, Vec3 at, double radius, int rgb, int ticks) {
        for (ServerPlayer p : l.players()) {
            if (p.position().distanceTo(at) <= radius)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.telephoneorder.net.Payloads.ScreenFlash(rgb, ticks));
        }
    }
}

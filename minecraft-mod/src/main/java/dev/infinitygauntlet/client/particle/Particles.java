package dev.infinitygauntlet.client.particle;

import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.registry.ModParticles;
import dev.infinitygauntlet.vfx.EnergyParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side particle spawning shortcuts. Every call goes through the vanilla particle limiter. */
public final class Particles {
    public static boolean chance(float p) {
        return ThreadLocalRandom.current().nextFloat() < p;
    }

    public static void spark(Vec3 pos, Vec3 vel, int color, float size) {
        add(new EnergyParticleOptions(ModParticles.ENERGY_SPARK.get(), color, size, 0, EnergyParticleOptions.BEHAVIOR_DRIFT), pos, vel);
    }

    public static void glow(Vec3 pos, Vec3 vel, int color, float size, int life) {
        add(new EnergyParticleOptions(ModParticles.ENERGY_GLOW.get(), color, size, life, EnergyParticleOptions.BEHAVIOR_DRIFT), pos, vel);
    }

    public static void mote(Vec3 pos, Vec3 vel, int color, float size, int life) {
        add(new EnergyParticleOptions(ModParticles.ENERGY_MOTE.get(), color, size, life, EnergyParticleOptions.BEHAVIOR_DRIFT), pos, vel);
    }

    /** A mote that accelerates into {@code target} and vanishes there (things being sucked into a core). */
    public static void inward(Vec3 pos, Vec3 target, int color, float size) {
        add(new EnergyParticleOptions(ModParticles.ENERGY_MOTE.get(), color, size, 40, EnergyParticleOptions.BEHAVIOR_INWARD), pos, target);
    }

    public static void smoke(Vec3 pos, Vec3 vel, int color, float size, int life) {
        add(new EnergyParticleOptions(ModParticles.DIMENSIONAL_SMOKE.get(), color, size, life, EnergyParticleOptions.BEHAVIOR_BILLOW), pos, vel);
    }

    public static void shard(Vec3 pos, Vec3 vel, int color, float size, int life) {
        add(new EnergyParticleOptions(ModParticles.ENERGY_SHARD.get(), color, size, life, EnergyParticleOptions.BEHAVIOR_DRIFT), pos, vel);
    }

    private static void add(EnergyParticleOptions options, Vec3 pos, Vec3 vel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        mc.level.addParticle(options, false, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
    }

    /** Scales an emission count by the configured VFX quality, with probabilistic rounding. */
    public static int scaled(float count) {
        float n = count * ClientConfig.quality().density;
        int whole = (int) n;
        return whole + (chance(n - whole) ? 1 : 0);
    }

    private Particles() {
    }
}

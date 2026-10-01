package com.infinitemultiverse.client.vfx;

import com.infinitemultiverse.core.registry.ModParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Budget-aware particle emitter handed to every effect. */
public final class VfxSpawner {
    private final ParticleEngine engine;
    private final RandomSource random;
    private final VfxBudget budget;

    VfxSpawner(ParticleEngine engine, RandomSource random, VfxBudget budget) {
        this.engine = engine;
        this.random = random;
        this.budget = budget;
    }

    public int count(int highQualityCount) {
        return budget.count(highQualityCount);
    }

    public float rand() {
        return random.nextFloat();
    }

    public Vec3 randomUnit() {
        double theta = random.nextDouble() * Math.PI * 2.0;
        double y = random.nextDouble() * 2.0 - 1.0;
        double r = Math.sqrt(1.0 - y * y);
        return new Vec3(r * Math.cos(theta), y, r * Math.sin(theta));
    }

    public Vec3 jitter(double amount) {
        return new Vec3((random.nextDouble() - 0.5) * 2.0 * amount,
                (random.nextDouble() - 0.5) * 2.0 * amount,
                (random.nextDouble() - 0.5) * 2.0 * amount);
    }

    public void mote(Vec3 pos, Vec3 velocity, int startColor, int endColor, float size, int lifetime, float friction) {
        spawn(ModParticles.ENERGY_MOTE.get(), pos, velocity, startColor, endColor, size, lifetime, friction);
    }

    public void spark(Vec3 pos, Vec3 velocity, int startColor, int endColor, float size, int lifetime, float friction) {
        spawn(ModParticles.SPARK.get(), pos, velocity, startColor, endColor, size, lifetime, friction);
    }

    private void spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity, int startColor, int endColor, float size, int lifetime, float friction) {
        Particle particle = engine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
        if (particle instanceof EnergyParticle energy) {
            energy.configure(startColor, endColor, size, lifetime, friction);
        }
    }
}

package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import dev.riftverse.particle.GlowParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Riftverse.MODID);

    /** Tiny, hot, additive spark. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> SPARK = PARTICLES.register("spark", GlowParticleType::new);
    /** Soft round glow that drifts. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> MOTE = PARTICLES.register("mote", GlowParticleType::new);
    /** Velocity-stretched streak of light. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> STREAK = PARTICLES.register("streak", GlowParticleType::new);
    /** Expanding shockwave ring that faces the camera. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> RING = PARTICLES.register("ring", GlowParticleType::new);
    /** Flickering square data-fragment. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> GLITCH = PARTICLES.register("glitch", GlowParticleType::new);
    /** Large, slow volumetric-looking puff. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> DUST = PARTICLES.register("dust", GlowParticleType::new);
    /** Mote that spirals into its origin point (black holes, rift intake). */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> INFALL = PARTICLES.register("infall", GlowParticleType::new);

    private RvParticles() {}
}

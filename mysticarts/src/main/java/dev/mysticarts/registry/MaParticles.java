package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.particle.GlowParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MysticArts.MODID);

    /** Hot spark flung off spell edges (sling ring portals, eldritch whips). */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> SPARK = PARTICLES.register("spark", GlowParticleType::new);
    /** Soft drifting glow. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> MOTE = PARTICLES.register("mote", GlowParticleType::new);
    /** Velocity-stretched streak. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> STREAK = PARTICLES.register("streak", GlowParticleType::new);
    /** Expanding camera-facing shock ring. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> RING = PARTICLES.register("ring", GlowParticleType::new);
    /** Slowly spinning mystic glyph. */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> RUNE = PARTICLES.register("rune", GlowParticleType::new);
    /** Disintegration ash that peels away and darkens (the Snap). */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> ASH = PARTICLES.register("ash", GlowParticleType::new);
    /** Mote that spirals into its target point (absorption, soul drain, charge-ups). */
    public static final DeferredHolder<ParticleType<?>, GlowParticleType> INFALL = PARTICLES.register("infall", GlowParticleType::new);

    private MaParticles() {}
}

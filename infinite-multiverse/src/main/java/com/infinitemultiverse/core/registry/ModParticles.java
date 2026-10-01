package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, InfiniteMultiverse.MOD_ID);

    /** Soft glowing orb; tinted and sized per effect by the client VFX system. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENERGY_MOTE =
            PARTICLE_TYPES.register("energy_mote", () -> new SimpleParticleType(false));
    /** Four-pointed twinkling star used for impacts and highlights. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK =
            PARTICLE_TYPES.register("spark", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}

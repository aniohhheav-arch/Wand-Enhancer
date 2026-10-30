package dev.infinitygauntlet.registry;

import com.mojang.serialization.MapCodec;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.vfx.EnergyParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every particle type takes {@link EnergyParticleOptions} (color, size, lifetime, behaviour), so one set of
 * particle types covers the palette of all six stones.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, InfinityGauntlet.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<EnergyParticleOptions>> ENERGY_GLOW = register("energy_glow");
    public static final DeferredHolder<ParticleType<?>, ParticleType<EnergyParticleOptions>> ENERGY_SPARK = register("energy_spark");
    public static final DeferredHolder<ParticleType<?>, ParticleType<EnergyParticleOptions>> ENERGY_MOTE = register("energy_mote");
    public static final DeferredHolder<ParticleType<?>, ParticleType<EnergyParticleOptions>> DIMENSIONAL_SMOKE = register("dimensional_smoke");
    public static final DeferredHolder<ParticleType<?>, ParticleType<EnergyParticleOptions>> ENERGY_SHARD = register("energy_shard");

    private static DeferredHolder<ParticleType<?>, ParticleType<EnergyParticleOptions>> register(String name) {
        return REGISTER.register(name, () -> new ParticleType<EnergyParticleOptions>(false) {
            @Override
            public MapCodec<EnergyParticleOptions> codec() {
                return EnergyParticleOptions.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, EnergyParticleOptions> streamCodec() {
                return EnergyParticleOptions.streamCodec(this);
            }
        });
    }

    private ModParticles() {
    }
}

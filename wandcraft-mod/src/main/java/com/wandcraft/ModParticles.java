package com.wandcraft;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/**
 * Custom tinted particles. The colour travels as ARGB: RGB is the tint and the alpha byte packs the size
 * (glow/spark/wisp: size x 100, rune: radius x 20).
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, WandCraft.ID);

    public static final Supplier<ParticleType<ColorParticleOption>> GLOW = PARTICLES.register("glow", ModParticles::tinted);
    public static final Supplier<ParticleType<ColorParticleOption>> SPARK = PARTICLES.register("spark", ModParticles::tinted);
    public static final Supplier<ParticleType<ColorParticleOption>> WISP = PARTICLES.register("wisp", ModParticles::tinted);
    public static final Supplier<ParticleType<ColorParticleOption>> RUNE = PARTICLES.register("rune", ModParticles::tinted);

    private ModParticles() {}

    private static ParticleType<ColorParticleOption> tinted() {
        return new ParticleType<>(false) {
            @Override public MapCodec<ColorParticleOption> codec() { return ColorParticleOption.codec(this); }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() { return ColorParticleOption.streamCodec(this); }
        };
    }
}

package dev.riftverse.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class GlowParticleType extends ParticleType<GlowParticleOptions> {
    private final MapCodec<GlowParticleOptions> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, GlowParticleOptions> streamCodec;

    public GlowParticleType() {
        super(true);
        this.codec = GlowParticleOptions.codec(this);
        this.streamCodec = GlowParticleOptions.streamCodec(this);
    }

    @Override
    public MapCodec<GlowParticleOptions> codec() {
        return codec;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, GlowParticleOptions> streamCodec() {
        return streamCodec;
    }

    public GlowParticleOptions with(int rgb, float scale, int lifetime) {
        return GlowParticleOptions.of(this, rgb, scale, lifetime);
    }
}

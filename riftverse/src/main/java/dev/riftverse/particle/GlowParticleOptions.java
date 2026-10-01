package dev.riftverse.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Coloured, scalable options shared by every Riftverse glow particle. */
public final class GlowParticleOptions implements ParticleOptions {
    private final ParticleType<GlowParticleOptions> type;
    public final float r;
    public final float g;
    public final float b;
    public final float scale;
    public final int lifetime;

    public GlowParticleOptions(ParticleType<GlowParticleOptions> type, float r, float g, float b, float scale, int lifetime) {
        this.type = type;
        this.r = r;
        this.g = g;
        this.b = b;
        this.scale = scale;
        this.lifetime = lifetime;
    }

    public static GlowParticleOptions of(ParticleType<GlowParticleOptions> type, int rgb, float scale, int lifetime) {
        return new GlowParticleOptions(type, ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, scale, lifetime);
    }

    @Override
    public ParticleType<GlowParticleOptions> getType() {
        return type;
    }

    static MapCodec<GlowParticleOptions> codec(ParticleType<GlowParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("r").forGetter(o -> o.r),
                Codec.FLOAT.fieldOf("g").forGetter(o -> o.g),
                Codec.FLOAT.fieldOf("b").forGetter(o -> o.b),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(o -> o.scale),
                Codec.INT.optionalFieldOf("lifetime", 30).forGetter(o -> o.lifetime)
        ).apply(i, (r, g, b, s, l) -> new GlowParticleOptions(type, r, g, b, s, l)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, GlowParticleOptions> streamCodec(ParticleType<GlowParticleOptions> type) {
        return StreamCodec.composite(
                ByteBufCodecs.FLOAT, o -> o.r,
                ByteBufCodecs.FLOAT, o -> o.g,
                ByteBufCodecs.FLOAT, o -> o.b,
                ByteBufCodecs.FLOAT, o -> o.scale,
                ByteBufCodecs.VAR_INT, o -> o.lifetime,
                (r, g, b, s, l) -> new GlowParticleOptions(type, r, g, b, s, l));
    }
}

package dev.infinitygauntlet.vfx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Parameters shared by all mod particles.
 *
 * @param color    0xRRGGBB tint
 * @param size     quad size in blocks
 * @param lifetime ticks; 0 picks the particle's default
 * @param behavior one of the BEHAVIOR_* constants
 */
public record EnergyParticleOptions(ParticleType<EnergyParticleOptions> type, int color, float size, int lifetime, int behavior)
        implements ParticleOptions {
    /** Moves with its initial velocity and fades. */
    public static final int BEHAVIOR_DRIFT = 0;
    /** Treats the velocity as an offset to an attractor and accelerates toward it (sucked into a core). */
    public static final int BEHAVIOR_INWARD = 1;
    /** Rises slowly and expands (smoke). */
    public static final int BEHAVIOR_BILLOW = 2;
    /** Frozen in place; flickers (time-stop residue, suspended embers). */
    public static final int BEHAVIOR_SUSPENDED = 3;

    public static MapCodec<EnergyParticleOptions> codec(ParticleType<EnergyParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("color").forGetter(EnergyParticleOptions::color),
                Codec.FLOAT.fieldOf("size").forGetter(EnergyParticleOptions::size),
                Codec.INT.optionalFieldOf("lifetime", 0).forGetter(EnergyParticleOptions::lifetime),
                Codec.INT.optionalFieldOf("behavior", BEHAVIOR_DRIFT).forGetter(EnergyParticleOptions::behavior)
        ).apply(i, (c, s, l, b) -> new EnergyParticleOptions(type, c, s, l, b)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, EnergyParticleOptions> streamCodec(ParticleType<EnergyParticleOptions> type) {
        return StreamCodec.composite(
                ByteBufCodecs.INT, EnergyParticleOptions::color,
                ByteBufCodecs.FLOAT, EnergyParticleOptions::size,
                ByteBufCodecs.VAR_INT, EnergyParticleOptions::lifetime,
                ByteBufCodecs.VAR_INT, EnergyParticleOptions::behavior,
                (c, s, l, b) -> new EnergyParticleOptions(type, c, s, l, b));
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }
}

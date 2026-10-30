package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: a burst of reality distortion centred on {@code origin}, attenuated by distance on the client. */
public record GlitchPayload(Vec3 origin, float radius, float strength) implements CustomPacketPayload {
    public static final Type<GlitchPayload> TYPE = new Type<>(InfinityGauntlet.id("glitch"));
    public static final StreamCodec<ByteBuf, GlitchPayload> CODEC = StreamCodec.composite(
            ModCodecs.VEC3, GlitchPayload::origin,
            ByteBufCodecs.FLOAT, GlitchPayload::radius,
            ByteBufCodecs.FLOAT, GlitchPayload::strength,
            GlitchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

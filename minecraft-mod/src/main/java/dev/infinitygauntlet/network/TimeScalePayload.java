package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the time scale of one entity changed (1 = normal, 0 = frozen). {@code visible} is false when only
 * quiet sources (Power Stone hit-stop) slow it, so no Time Stone overlay is drawn.
 */
public record TimeScalePayload(int entityId, float scale, boolean visible) implements CustomPacketPayload {
    public static final Type<TimeScalePayload> TYPE = new Type<>(InfinityGauntlet.id("time_scale"));
    public static final StreamCodec<ByteBuf, TimeScalePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TimeScalePayload::entityId,
            ByteBufCodecs.FLOAT, TimeScalePayload::scale,
            ByteBufCodecs.BOOL, TimeScalePayload::visible,
            TimeScalePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the time scale of one entity changed (1 = normal, 0 = frozen). */
public record TimeScalePayload(int entityId, float scale) implements CustomPacketPayload {
    public static final Type<TimeScalePayload> TYPE = new Type<>(InfinityGauntlet.id("time_scale"));
    public static final StreamCodec<ByteBuf, TimeScalePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TimeScalePayload::entityId,
            ByteBufCodecs.FLOAT, TimeScalePayload::scale,
            TimeScalePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

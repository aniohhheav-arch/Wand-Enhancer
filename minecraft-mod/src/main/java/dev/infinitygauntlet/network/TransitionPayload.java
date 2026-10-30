package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: play the full-screen dimensional travel overlay. */
public record TransitionPayload(int ticks, int color) implements CustomPacketPayload {
    public static final Type<TransitionPayload> TYPE = new Type<>(InfinityGauntlet.id("transition"));
    public static final StreamCodec<ByteBuf, TransitionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TransitionPayload::ticks,
            ByteBufCodecs.INT, TransitionPayload::color,
            TransitionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

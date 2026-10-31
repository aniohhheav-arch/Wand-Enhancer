package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: a Mind Stone state of the receiving player starts (ticks &gt; 0) or ends (0). */
public record MindStatePayload(int state, int ticks) implements CustomPacketPayload {
    public static final int ASTRAL = 0;
    public static final int MINDS_EYE = 1;
    public static final Type<MindStatePayload> TYPE = new Type<>(InfinityGauntlet.id("mind_state"));
    public static final StreamCodec<ByteBuf, MindStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MindStatePayload::state,
            ByteBufCodecs.VAR_INT, MindStatePayload::ticks,
            MindStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

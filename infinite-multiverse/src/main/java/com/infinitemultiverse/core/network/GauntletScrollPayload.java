package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: scroll the held Infinity Gauntlet's stone page and/or technique. */
public record GauntletScrollPayload(int pageDelta, int slotDelta) implements CustomPacketPayload {
    public static final Type<GauntletScrollPayload> TYPE = new Type<>(InfiniteMultiverse.id("gauntlet_scroll"));
    public static final StreamCodec<ByteBuf, GauntletScrollPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GauntletScrollPayload::pageDelta,
            ByteBufCodecs.VAR_INT, GauntletScrollPayload::slotDelta,
            GauntletScrollPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

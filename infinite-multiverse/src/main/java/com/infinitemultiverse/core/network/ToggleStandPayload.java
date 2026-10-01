package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the dedicated Summon/Dismiss Stand key was pressed. */
public record ToggleStandPayload() implements CustomPacketPayload {
    public static final ToggleStandPayload INSTANCE = new ToggleStandPayload();
    public static final Type<ToggleStandPayload> TYPE = new Type<>(InfiniteMultiverse.id("toggle_stand"));
    public static final StreamCodec<ByteBuf, ToggleStandPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

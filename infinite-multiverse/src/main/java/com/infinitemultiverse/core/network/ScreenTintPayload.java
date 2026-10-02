package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: wash the screen in {@code color} at {@code strength} for {@code durationTicks} (0 clears). */
public record ScreenTintPayload(int color, float strength, int durationTicks) implements CustomPacketPayload {
    public static final Type<ScreenTintPayload> TYPE = new Type<>(InfiniteMultiverse.id("screen_tint"));
    public static final StreamCodec<ByteBuf, ScreenTintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ScreenTintPayload::color,
            ByteBufCodecs.FLOAT, ScreenTintPayload::strength,
            ByteBufCodecs.VAR_INT, ScreenTintPayload::durationTicks,
            ScreenTintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

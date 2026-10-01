package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: bind an ability to a loadout slot, or clear it when {@code ability} is empty. */
public record SetLoadoutSlotPayload(int slot, Optional<ResourceLocation> ability) implements CustomPacketPayload {
    public static final Type<SetLoadoutSlotPayload> TYPE = new Type<>(InfiniteMultiverse.id("set_loadout_slot"));
    public static final StreamCodec<ByteBuf, SetLoadoutSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetLoadoutSlotPayload::slot,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), SetLoadoutSlotPayload::ability,
            SetLoadoutSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

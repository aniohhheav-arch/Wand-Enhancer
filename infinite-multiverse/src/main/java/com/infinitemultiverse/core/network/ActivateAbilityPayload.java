package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the key for loadout slot {@code slot} (0-based) was pressed. The server resolves the ability. */
public record ActivateAbilityPayload(int slot) implements CustomPacketPayload {
    public static final Type<ActivateAbilityPayload> TYPE = new Type<>(InfiniteMultiverse.id("activate_ability"));
    public static final StreamCodec<ByteBuf, ActivateAbilityPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ActivateAbilityPayload::new, ActivateAbilityPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

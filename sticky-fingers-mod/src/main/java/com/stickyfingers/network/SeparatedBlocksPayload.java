package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: the blocks a zipper parted, so clients can draw them sliding apart like curtains. */
public record SeparatedBlocksPayload(int entityId, long[] positions, int[] states) implements CustomPacketPayload {
    public static final Type<SeparatedBlocksPayload> TYPE = new Type<>(StickyFingersMod.id("separated_blocks"));
    public static final StreamCodec<FriendlyByteBuf, SeparatedBlocksPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> { buf.writeVarInt(p.entityId); buf.writeLongArray(p.positions); buf.writeVarIntArray(p.states); },
            buf -> new SeparatedBlocksPayload(buf.readVarInt(), buf.readLongArray(), buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

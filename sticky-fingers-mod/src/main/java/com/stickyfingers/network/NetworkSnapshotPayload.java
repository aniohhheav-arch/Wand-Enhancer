package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** S2C: the owner's active zipper network, for the network screen. */
public record NetworkSnapshotPayload(List<Entry> entries, boolean openScreen) implements CustomPacketPayload {
    public record Entry(int id, int kind, BlockPos pos, String dimension, boolean open, int life, int[] links) {}

    public static final Type<NetworkSnapshotPayload> TYPE = new Type<>(StickyFingersMod.id("network_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, NetworkSnapshotPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) {
                    buf.writeVarInt(e.id); buf.writeVarInt(e.kind); buf.writeBlockPos(e.pos); buf.writeUtf(e.dimension);
                    buf.writeBoolean(e.open); buf.writeVarInt(e.life + 1); buf.writeVarIntArray(e.links);
                }
                buf.writeBoolean(p.openScreen);
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), buf.readUtf(),
                            buf.readBoolean(), buf.readVarInt() - 1, buf.readVarIntArray()));
                }
                return new NetworkSnapshotPayload(list, buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

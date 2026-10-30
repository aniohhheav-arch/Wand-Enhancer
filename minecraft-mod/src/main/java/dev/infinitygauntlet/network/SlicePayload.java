package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → chamber visitor: the blocks around the real target (block state ids, x fastest, then z, then y),
 * run-length encoded as (count, id) pairs. Drives the live view and the survey hologram.
 */
public record SlicePayload(int specimenId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int[] states) implements CustomPacketPayload {
    public static final Type<SlicePayload> TYPE = new Type<>(InfinityGauntlet.id("slice"));
    public static final StreamCodec<ByteBuf, SlicePayload> CODEC = new StreamCodec<>() {
        @Override
        public SlicePayload decode(ByteBuf buf) {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            BlockPos origin = BlockPos.of(buf.readLong());
            int sx = buf.readUnsignedByte(), sy = buf.readUnsignedByte(), sz = buf.readUnsignedByte();
            int total = sx * sy * sz;
            int[] states = new int[total];
            int runs = ByteBufCodecs.VAR_INT.decode(buf);
            int i = 0;
            for (int r = 0; r < runs && i < total; r++) {
                int count = ByteBufCodecs.VAR_INT.decode(buf), state = ByteBufCodecs.VAR_INT.decode(buf);
                for (int k = 0; k < count && i < total; k++) {
                    states[i++] = state;
                }
            }
            return new SlicePayload(id, origin, sx, sy, sz, states);
        }

        @Override
        public void encode(ByteBuf buf, SlicePayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.specimenId);
            buf.writeLong(p.origin.asLong());
            buf.writeByte(p.sizeX);
            buf.writeByte(p.sizeY);
            buf.writeByte(p.sizeZ);
            List<int[]> runs = new ArrayList<>();
            for (int i = 0; i < p.states.length; ) {
                int j = i;
                while (j < p.states.length && p.states[j] == p.states[i]) {
                    j++;
                }
                runs.add(new int[]{j - i, p.states[i]});
                i = j;
            }
            ByteBufCodecs.VAR_INT.encode(buf, runs.size());
            for (int[] r : runs) {
                ByteBufCodecs.VAR_INT.encode(buf, r[0]);
                ByteBufCodecs.VAR_INT.encode(buf, r[1]);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server → chamber visitor: the blocks around the real target (block state ids, x-fastest then z then y), rendered
 * as a miniature survey hologram beside the specimen. {@code targetX/Y/Z} is the target's offset inside the box.
 */
public record SurveyPayload(int specimenId, BlockPos origin, int sizeX, int sizeY, int sizeZ, List<Integer> states, float targetX, float targetY,
                            float targetZ) implements CustomPacketPayload {
    public static final Type<SurveyPayload> TYPE = new Type<>(InfinityGauntlet.id("survey"));
    public static final StreamCodec<ByteBuf, SurveyPayload> CODEC = new StreamCodec<>() {
        @Override
        public SurveyPayload decode(ByteBuf buf) {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            BlockPos origin = BlockPos.of(buf.readLong());
            int sx = buf.readByte(), sy = buf.readByte(), sz = buf.readByte();
            List<Integer> states = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4096)).decode(buf);
            return new SurveyPayload(id, origin, sx, sy, sz, states, buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SurveyPayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.specimenId);
            buf.writeLong(p.origin.asLong());
            buf.writeByte(p.sizeX);
            buf.writeByte(p.sizeY);
            buf.writeByte(p.sizeZ);
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4096)).encode(buf, p.states);
            buf.writeFloat(p.targetX);
            buf.writeFloat(p.targetY);
            buf.writeFloat(p.targetZ);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

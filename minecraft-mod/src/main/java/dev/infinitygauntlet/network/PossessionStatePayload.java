package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → operator: whether they are at the controls, of which Nexus, which creature is active, and the view
 * rotation to start predicting from (sent on entering control and on switching feeds).
 */
public record PossessionStatePayload(boolean controlling, BlockPos nexus, int activeEntity, float yaw, float pitch, float anchorYaw)
        implements CustomPacketPayload {
    public static final Type<PossessionStatePayload> TYPE = new Type<>(InfinityGauntlet.id("possession_state"));
    public static final StreamCodec<ByteBuf, PossessionStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public PossessionStatePayload decode(ByteBuf buf) {
            return new PossessionStatePayload(buf.readBoolean(), BlockPos.of(buf.readLong()), buf.readInt(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, PossessionStatePayload p) {
            buf.writeBoolean(p.controlling);
            buf.writeLong(p.nexus.asLong());
            buf.writeInt(p.activeEntity);
            buf.writeFloat(p.yaw);
            buf.writeFloat(p.pitch);
            buf.writeFloat(p.anchorYaw);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

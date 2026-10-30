package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server, every tick while operating a Nexus: movement and view of the possessed creature.
 * {@code flags}: 1 jump, 2 sprint, 4 attack (edge), 8 special (edge).
 */
public record ControlInputPayload(float forward, float strafe, float yaw, float pitch, int flags) implements CustomPacketPayload {
    public static final int JUMP = 1, SPRINT = 2, ATTACK = 4, SPECIAL = 8;
    public static final Type<ControlInputPayload> TYPE = new Type<>(InfinityGauntlet.id("control_input"));
    public static final StreamCodec<ByteBuf, ControlInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public ControlInputPayload decode(ByteBuf buf) {
            return new ControlInputPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
        }

        @Override
        public void encode(ByteBuf buf, ControlInputPayload p) {
            buf.writeFloat(p.forward);
            buf.writeFloat(p.strafe);
            buf.writeFloat(p.yaw);
            buf.writeFloat(p.pitch);
            buf.writeByte(p.flags);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

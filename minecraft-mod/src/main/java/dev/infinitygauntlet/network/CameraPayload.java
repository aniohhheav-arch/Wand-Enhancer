package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: camera shake / zoom / flash centred on {@code origin}. Clients scale the effect by their
 * own distance to the origin, so one broadcast serves everybody nearby.
 */
public record CameraPayload(Vec3 origin, float radius, float shake, float zoom, int zoomTicks, int flashColor, float flashAlpha,
                            int flashTicks) implements CustomPacketPayload {
    public static final Type<CameraPayload> TYPE = new Type<>(InfinityGauntlet.id("camera"));
    public static final StreamCodec<ByteBuf, CameraPayload> CODEC = new StreamCodec<>() {
        @Override
        public CameraPayload decode(ByteBuf buf) {
            return new CameraPayload(ModCodecs.VEC3.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(),
                    buf.readInt(), buf.readFloat(), buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, CameraPayload p) {
            ModCodecs.VEC3.encode(buf, p.origin);
            buf.writeFloat(p.radius);
            buf.writeFloat(p.shake);
            buf.writeFloat(p.zoom);
            buf.writeInt(p.zoomTicks);
            buf.writeInt(p.flashColor);
            buf.writeFloat(p.flashAlpha);
            buf.writeInt(p.flashTicks);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

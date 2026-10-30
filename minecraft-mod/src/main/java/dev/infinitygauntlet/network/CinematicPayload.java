package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: take over the camera for a cinematic shot. The camera travels from {@code focus + from} to
 * {@code focus + to} (eased) while looking at the focus, which follows {@code focusEntity} when it is not -1.
 *
 * @param roll   dutch angle in degrees at the peak of the shot
 * @param fovMul field-of-view multiplier at the peak of the shot
 */
public record CinematicPayload(int focusEntity, Vec3 focus, Vec3 from, Vec3 to, int ticks, float roll, float fovMul) implements CustomPacketPayload {
    public static final Type<CinematicPayload> TYPE = new Type<>(InfinityGauntlet.id("cinematic"));
    public static final StreamCodec<ByteBuf, CinematicPayload> CODEC = new StreamCodec<>() {
        @Override
        public CinematicPayload decode(ByteBuf buf) {
            return new CinematicPayload(buf.readInt(), ModCodecs.VEC3.decode(buf), ModCodecs.VEC3.decode(buf), ModCodecs.VEC3.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, CinematicPayload p) {
            buf.writeInt(p.focusEntity);
            ModCodecs.VEC3.encode(buf, p.focus);
            ModCodecs.VEC3.encode(buf, p.from);
            ModCodecs.VEC3.encode(buf, p.to);
            ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
            buf.writeFloat(p.roll);
            buf.writeFloat(p.fovMul);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

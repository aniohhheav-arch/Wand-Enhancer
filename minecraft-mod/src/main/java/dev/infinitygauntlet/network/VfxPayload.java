package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.vfx.VfxType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: spawn one client-side cinematic effect. Gameplay already happened on the server;
 * this only carries what is needed to draw it.
 *
 * @param a        primary position (center / beam start)
 * @param b        secondary vector (beam end, ring normal, or size depending on type)
 * @param color    primary 0xRRGGBB
 * @param color2   secondary 0xRRGGBB
 * @param scale    size in blocks
 * @param duration lifetime in ticks
 * @param seed     shared randomness so every client draws the same shape
 */
public record VfxPayload(VfxType vfx, Vec3 a, Vec3 b, int color, int color2, float scale, int duration, int seed)
        implements CustomPacketPayload {
    public static final Type<VfxPayload> TYPE = new Type<>(InfinityGauntlet.id("vfx"));
    public static final StreamCodec<ByteBuf, VfxPayload> CODEC = new StreamCodec<>() {
        @Override
        public VfxPayload decode(ByteBuf buf) {
            return new VfxPayload(VfxType.STREAM_CODEC.decode(buf), ModCodecs.VEC3.decode(buf), ModCodecs.VEC3.decode(buf),
                    buf.readInt(), buf.readInt(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, VfxPayload p) {
            VfxType.STREAM_CODEC.encode(buf, p.vfx);
            ModCodecs.VEC3.encode(buf, p.a);
            ModCodecs.VEC3.encode(buf, p.b);
            buf.writeInt(p.color);
            buf.writeInt(p.color2);
            buf.writeFloat(p.scale);
            ByteBufCodecs.VAR_INT.encode(buf, p.duration);
            buf.writeInt(p.seed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

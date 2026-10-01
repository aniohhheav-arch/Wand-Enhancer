package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server → nearby clients: play a named visual effect. Clients render it according to their own quality,
 * distance and particle-cap settings, so the server never decides particle counts.
 */
public record VfxPayload(ResourceLocation effect, Vec3 origin, Vec3 vector, float scale) implements CustomPacketPayload {
    public static final Type<VfxPayload> TYPE = new Type<>(InfiniteMultiverse.id("vfx"));
    public static final StreamCodec<FriendlyByteBuf, VfxPayload> STREAM_CODEC = StreamCodec.ofMember(VfxPayload::write, VfxPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(effect);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeFloat((float) vector.x);
        buf.writeFloat((float) vector.y);
        buf.writeFloat((float) vector.z);
        buf.writeFloat(scale);
    }

    private static VfxPayload read(FriendlyByteBuf buf) {
        ResourceLocation effect = buf.readResourceLocation();
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 vector = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new VfxPayload(effect, origin, vector, buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

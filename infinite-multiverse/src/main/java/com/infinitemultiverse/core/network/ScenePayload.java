package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: start a rendered scene (3D effect geometry, optionally a cutscene).
 * {@code entityId} is the caster (or -1); {@code param} is a scene-specific size such as a radius or length.
 */
public record ScenePayload(ResourceLocation scene, Vec3 origin, Vec3 dir, int color, int duration, int entityId, float param, int flags)
        implements CustomPacketPayload {
    public static final Type<ScenePayload> TYPE = new Type<>(InfiniteMultiverse.id("scene"));
    public static final StreamCodec<FriendlyByteBuf, ScenePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeResourceLocation(p.scene);
        buf.writeDouble(p.origin.x).writeDouble(p.origin.y).writeDouble(p.origin.z);
        buf.writeFloat((float) p.dir.x).writeFloat((float) p.dir.y).writeFloat((float) p.dir.z);
        buf.writeInt(p.color);
        buf.writeVarInt(p.duration);
        buf.writeVarInt(p.entityId + 1);
        buf.writeFloat(p.param);
        buf.writeVarInt(p.flags);
    }, buf -> new ScenePayload(buf.readResourceLocation(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

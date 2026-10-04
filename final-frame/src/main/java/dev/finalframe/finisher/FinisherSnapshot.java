package dev.finalframe.finisher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Everything a client needs to replay a finisher deterministically: the choreography frame and the
 * server-planned target path. Both sides derive every pose from this plus the elapsed tick.
 */
public record FinisherSnapshot(
    int sessionId,
    ResourceLocation finisherId,
    int performerId,
    int targetId,
    Vec3 anchor,
    float anchorYaw,
    Vec3 targetStart,
    Vec3 targetEnd,
    float targetStartYaw,
    float targetHeight
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, FinisherSnapshot> STREAM_CODEC = StreamCodec.of(
        (buf, s) -> {
            buf.writeVarInt(s.sessionId);
            buf.writeResourceLocation(s.finisherId);
            buf.writeVarInt(s.performerId);
            buf.writeVarInt(s.targetId);
            writeVec(buf, s.anchor);
            buf.writeFloat(s.anchorYaw);
            writeVec(buf, s.targetStart);
            writeVec(buf, s.targetEnd);
            buf.writeFloat(s.targetStartYaw);
            buf.writeFloat(s.targetHeight);
        },
        buf -> new FinisherSnapshot(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(),
            readVec(buf), buf.readFloat(), readVec(buf), readVec(buf), buf.readFloat(), buf.readFloat()));

    public AnchorFrame frame() {
        return new AnchorFrame(anchor, anchorYaw);
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}

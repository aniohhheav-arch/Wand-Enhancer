package com.infinitemultiverse.core.network;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → nearby clients: a time stop started or ended, for the screen-space time-stop presentation. */
public record TimeStopPayload(boolean active, int ownerEntityId, Vec3 center, float radius, int durationTicks) implements CustomPacketPayload {
    public static final Type<TimeStopPayload> TYPE = new Type<>(InfiniteMultiverse.id("time_stop"));
    public static final StreamCodec<FriendlyByteBuf, TimeStopPayload> STREAM_CODEC = StreamCodec.ofMember(TimeStopPayload::write, TimeStopPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(ownerEntityId);
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeFloat(radius);
        buf.writeVarInt(durationTicks);
    }

    private static TimeStopPayload read(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        int owner = buf.readVarInt();
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new TimeStopPayload(active, owner, center, buf.readFloat(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

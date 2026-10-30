package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: a player strikes a Power Stone pose ({@code PowerPose} ordinal) for {@code ticks} ticks. */
public record PowerPosePayload(int entityId, int pose, int ticks) implements CustomPacketPayload {
    public static final Type<PowerPosePayload> TYPE = new Type<>(InfinityGauntlet.id("power_pose"));
    public static final StreamCodec<ByteBuf, PowerPosePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PowerPosePayload::entityId,
            ByteBufCodecs.VAR_INT, PowerPosePayload::pose,
            ByteBufCodecs.VAR_INT, PowerPosePayload::ticks,
            PowerPosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

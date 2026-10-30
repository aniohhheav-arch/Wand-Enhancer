package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: a finisher starts ({@code ended} false) or stops early ({@code ended} true). Clients run the same
 * deterministic timeline as the server from the start positions.
 *
 * @param kind        {@code FinisherType} ordinal
 * @param casterStart the caster's feet when it began
 * @param targetPos   the target's feet, where it is held for the whole sequence
 */
public record FinisherPayload(int kind, int casterId, int targetId, Vec3 casterStart, Vec3 targetPos, boolean ended) implements CustomPacketPayload {
    public static final Type<FinisherPayload> TYPE = new Type<>(InfinityGauntlet.id("finisher"));
    public static final StreamCodec<ByteBuf, FinisherPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FinisherPayload::kind,
            ByteBufCodecs.VAR_INT, FinisherPayload::casterId,
            ByteBufCodecs.VAR_INT, FinisherPayload::targetId,
            ModCodecs.VEC3, FinisherPayload::casterStart,
            ModCodecs.VEC3, FinisherPayload::targetPos,
            ByteBufCodecs.BOOL, FinisherPayload::ended,
            FinisherPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

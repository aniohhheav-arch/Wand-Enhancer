package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: a Nexus console command. */
public record PossessionActionPayload(int action) implements CustomPacketPayload {
    public static final int LEAVE = 0, DISCONNECT = 1, SWITCH = 2, BLINK = 3, OVERRIDE = 4, EXTRACT = 5;
    public static final Type<PossessionActionPayload> TYPE = new Type<>(InfinityGauntlet.id("possession_action"));
    public static final StreamCodec<ByteBuf, PossessionActionPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(PossessionActionPayload::new, PossessionActionPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

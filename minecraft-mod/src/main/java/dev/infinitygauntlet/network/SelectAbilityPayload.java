package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: select a stone and one of its abilities on the held gauntlet. */
public record SelectAbilityPayload(int stone, int ability) implements CustomPacketPayload {
    public static final Type<SelectAbilityPayload> TYPE = new Type<>(InfinityGauntlet.id("select_ability"));
    public static final StreamCodec<ByteBuf, SelectAbilityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectAbilityPayload::stone,
            ByteBufCodecs.VAR_INT, SelectAbilityPayload::ability,
            SelectAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

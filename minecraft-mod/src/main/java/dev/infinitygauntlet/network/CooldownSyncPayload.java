package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/** Server → client: remaining cooldown ticks per ability id, for the HUD and ability wheel. */
public record CooldownSyncPayload(Map<String, Integer> remaining) implements CustomPacketPayload {
    public static final Type<CooldownSyncPayload> TYPE = new Type<>(InfinityGauntlet.id("cooldowns"));
    public static final StreamCodec<ByteBuf, CooldownSyncPayload> CODEC = ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(
                    HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
            .map(CooldownSyncPayload::new, CooldownSyncPayload::remaining);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

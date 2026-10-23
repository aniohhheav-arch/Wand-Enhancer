package com.wandcraft.net;

import com.wandcraft.WandCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: where a possessed mob is in the possession timeline. {@code elapsed < 0} clears it. */
public record PossessionPayload(int entityId, int elapsed) implements CustomPacketPayload {
    public static final Type<PossessionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "possession"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PossessionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PossessionPayload::entityId, ByteBufCodecs.VAR_INT, PossessionPayload::elapsed, PossessionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

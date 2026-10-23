package com.wandcraft.net;

import com.wandcraft.WandCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: full copy of the player's magic state for HUD and wheel. */
public record SyncMagicPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncMagicPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "sync_magic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMagicPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SyncMagicPayload::data, SyncMagicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

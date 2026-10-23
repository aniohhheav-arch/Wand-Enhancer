package com.wandcraft.net;

import com.wandcraft.WandCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: choose the active spell. The server validates that it exists and is unlocked. */
public record SelectSpellPayload(String spellId) implements CustomPacketPayload {
    public static final Type<SelectSpellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "select_spell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpellPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectSpellPayload::spellId, SelectSpellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

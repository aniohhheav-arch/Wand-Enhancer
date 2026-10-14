package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: activate an ability. {@code alt} is the sneak-modified variant (close / permanent / reverse). */
public record UseAbilityPayload(int ability, boolean alt) implements CustomPacketPayload {
    public static final Type<UseAbilityPayload> TYPE = new Type<>(StickyFingersMod.id("use_ability"));
    public static final StreamCodec<FriendlyByteBuf, UseAbilityPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> { buf.writeVarInt(p.ability); buf.writeBoolean(p.alt); },
            buf -> new UseAbilityPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

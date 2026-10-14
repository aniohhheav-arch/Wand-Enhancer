package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScreenFxPayload(int fx, float intensity, int duration) implements CustomPacketPayload {
    public static final Type<ScreenFxPayload> TYPE = new Type<>(StickyFingersMod.id("screen_fx"));
    public static final StreamCodec<FriendlyByteBuf, ScreenFxPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> { buf.writeVarInt(p.fx); buf.writeFloat(p.intensity); buf.writeVarInt(p.duration); },
            buf -> new ScreenFxPayload(buf.readVarInt(), buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

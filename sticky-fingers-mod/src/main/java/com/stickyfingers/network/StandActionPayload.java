package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: generic Stand action. {@code a}/{@code b} are action specific (ability id, zipper ids).
 */
public record StandActionPayload(Action action, int a, int b) implements CustomPacketPayload {
    public enum Action { TOGGLE_STAND, SELECT, REQUEST_NETWORK, REFRESH_NETWORK, NET_TRAVEL, NET_LINK, NET_UNLINK, NET_CLOSE, NET_SET_EXIT, NET_TOGGLE_OPEN, CLOSE_ALL, SET_ORIENT }

    public static final Type<StandActionPayload> TYPE = new Type<>(StickyFingersMod.id("stand_action"));
    public static final StreamCodec<FriendlyByteBuf, StandActionPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> { buf.writeEnum(p.action); buf.writeVarInt(p.a + 1); buf.writeVarInt(p.b + 1); },
            buf -> new StandActionPayload(buf.readEnum(Action.class), buf.readVarInt() - 1, buf.readVarInt() - 1));

    public static StandActionPayload of(Action action) { return new StandActionPayload(action, -1, -1); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

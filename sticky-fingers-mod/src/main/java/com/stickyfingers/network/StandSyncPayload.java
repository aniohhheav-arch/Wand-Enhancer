package com.stickyfingers.network;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Owner-only snapshot of the Stand state for the HUD and wheel. Cooldowns are sent as remaining ticks. */
public record StandSyncPayload(boolean hasStand, boolean summoned, float energy, int selected, boolean hidden,
                               int exitZipper, int[] cooldowns) implements CustomPacketPayload {
    public static final Type<StandSyncPayload> TYPE = new Type<>(StickyFingersMod.id("stand_sync"));
    public static final StreamCodec<FriendlyByteBuf, StandSyncPayload> CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeBoolean(p.hasStand); buf.writeBoolean(p.summoned); buf.writeFloat(p.energy);
                buf.writeVarInt(p.selected); buf.writeBoolean(p.hidden); buf.writeVarInt(p.exitZipper + 1);
                buf.writeVarIntArray(p.cooldowns);
            },
            buf -> new StandSyncPayload(buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt() - 1, buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

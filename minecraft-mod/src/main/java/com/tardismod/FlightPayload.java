package com.tardismod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: "the TARDIS is in the time vortex for this many ticks". Drives the client vortex overlay. */
public record FlightPayload(int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FlightPayload> TYPE = new CustomPacketPayload.Type<>(TardisMod.id("flight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, FlightPayload::ticks, FlightPayload::new);

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player pressed a button on an observation panel (validated against the panel's geometry). */
public record PanelPressPayload(int panelId, int control) implements CustomPacketPayload {
    public static final Type<PanelPressPayload> TYPE = new Type<>(InfinityGauntlet.id("panel_press"));
    public static final StreamCodec<ByteBuf, PanelPressPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PanelPressPayload::panelId,
            ByteBufCodecs.VAR_INT, PanelPressPayload::control,
            PanelPressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

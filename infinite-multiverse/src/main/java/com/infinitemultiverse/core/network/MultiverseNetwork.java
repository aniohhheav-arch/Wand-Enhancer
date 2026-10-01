package com.infinitemultiverse.core.network;

import com.infinitemultiverse.client.ClientPayloadHandler;
import com.infinitemultiverse.core.ability.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MultiverseNetwork {
    /** Bump when any payload layout changes so mismatched client/server versions refuse to connect. */
    public static final String PROTOCOL_VERSION = "1";

    private MultiverseNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AbilityManager.onSlotPressed(player, payload.slot());
                    }
                }));

        registrar.playToServer(SetLoadoutSlotPayload.TYPE, SetLoadoutSlotPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AbilityManager.setLoadoutSlot(player, payload.slot(), payload.ability());
                    }
                }));

        // Lambda bodies are linked lazily, so ClientPayloadHandler is never loaded on a dedicated server.
        registrar.playToClient(SyncPlayerDataPayload.TYPE, SyncPlayerDataPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleSync(payload)));

        registrar.playToClient(VfxPayload.TYPE, VfxPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleVfx(payload)));
    }
}

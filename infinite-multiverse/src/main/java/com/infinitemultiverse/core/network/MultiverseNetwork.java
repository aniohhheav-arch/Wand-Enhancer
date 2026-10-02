package com.infinitemultiverse.core.network;

import com.infinitemultiverse.client.ClientPayloadHandler;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.stand.StandEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MultiverseNetwork {
    /** Bump when any payload layout changes so mismatched client/server versions refuse to connect. */
    public static final String PROTOCOL_VERSION = "5";

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

        registrar.playToServer(ToggleStandPayload.TYPE, ToggleStandPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StandEvents.onToggleKey(player);
                    }
                }));

        registrar.playToServer(GauntletScrollPayload.TYPE, GauntletScrollPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        var gauntlet = com.infinitemultiverse.cosmic.gauntlet.Gauntlet.held(player);
                        if (!gauntlet.isEmpty()) {
                            com.infinitemultiverse.cosmic.gauntlet.Gauntlet.scroll(gauntlet, Integer.signum(payload.pageDelta()), Integer.signum(payload.slotDelta()));
                        }
                    }
                }));

        // Lambda bodies are linked lazily, so ClientPayloadHandler is never loaded on a dedicated server.
        registrar.playToClient(SyncPlayerDataPayload.TYPE, SyncPlayerDataPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleSync(payload)));

        registrar.playToClient(VfxPayload.TYPE, VfxPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleVfx(payload)));

        registrar.playToClient(ScreenTintPayload.TYPE, ScreenTintPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleTint(payload)));

        registrar.playToClient(ScenePayload.TYPE, ScenePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleScene(payload)));

        registrar.playToClient(TimeStopPayload.TYPE, TimeStopPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.handleTimeStop(payload)));
    }
}

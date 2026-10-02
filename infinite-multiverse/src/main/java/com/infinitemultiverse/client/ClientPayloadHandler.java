package com.infinitemultiverse.client;

import com.infinitemultiverse.client.vfx.ClientVfx;
import com.infinitemultiverse.core.network.ScreenTintPayload;
import com.infinitemultiverse.core.network.SyncPlayerDataPayload;
import com.infinitemultiverse.core.network.TimeStopPayload;
import com.infinitemultiverse.core.network.VfxPayload;

/** Client-only payload sinks; referenced from {@code MultiverseNetwork} through lazily linked lambdas. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleSync(SyncPlayerDataPayload payload) {
        ClientMultiverseState.apply(payload);
    }

    public static void handleVfx(VfxPayload payload) {
        ClientVfx.play(payload);
    }

    public static void handleTimeStop(TimeStopPayload payload) {
        ClientTimeStop.apply(payload);
    }

    public static void handleTint(ScreenTintPayload payload) {
        ClientScreenTint.apply(payload);
    }

    public static void handleScene(com.infinitemultiverse.core.network.ScenePayload payload) {
        com.infinitemultiverse.client.cinematic.SceneManager.start(payload);
    }
}

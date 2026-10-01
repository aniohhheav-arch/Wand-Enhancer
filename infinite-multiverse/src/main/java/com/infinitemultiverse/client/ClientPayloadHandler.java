package com.infinitemultiverse.client;

import com.infinitemultiverse.client.vfx.ClientVfx;
import com.infinitemultiverse.core.network.SyncPlayerDataPayload;
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
}

package dev.infinitygauntlet.client;

import dev.infinitygauntlet.client.vfx.CameraEffects;
import dev.infinitygauntlet.client.vfx.ClientVfxManager;
import dev.infinitygauntlet.network.CameraPayload;
import dev.infinitygauntlet.network.CooldownSyncPayload;
import dev.infinitygauntlet.network.TransitionPayload;
import dev.infinitygauntlet.network.VfxPayload;

/** Handlers for server → client payloads; only loaded on the client. */
public final class ClientPayloadHandler {
    public static void vfx(VfxPayload payload) {
        ClientVfxManager.spawn(payload);
    }

    public static void camera(CameraPayload payload) {
        CameraEffects.apply(payload);
    }

    public static void transition(TransitionPayload payload) {
        CameraEffects.transition(payload.ticks(), payload.color());
    }

    public static void cooldowns(CooldownSyncPayload payload) {
        ClientCooldowns.sync(payload.remaining());
    }

    private ClientPayloadHandler() {
    }
}

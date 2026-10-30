package dev.infinitygauntlet.client;

import dev.infinitygauntlet.client.time.ClientTime;
import dev.infinitygauntlet.client.vfx.CameraEffects;
import dev.infinitygauntlet.client.vfx.ClientVfxManager;
import dev.infinitygauntlet.network.CameraPayload;
import dev.infinitygauntlet.network.CooldownSyncPayload;
import dev.infinitygauntlet.network.TimeScalePayload;
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

    public static void timeScale(TimeScalePayload payload) {
        ClientTime.set(payload.entityId(), payload.scale());
    }

    public static void possession(dev.infinitygauntlet.network.PossessionStatePayload payload) {
        dev.infinitygauntlet.client.nexus.RemoteControlClient.state(payload);
    }

    public static void slice(dev.infinitygauntlet.network.SlicePayload payload) {
        dev.infinitygauntlet.client.breach.LiveView.slice(payload);
    }

    public static void view(dev.infinitygauntlet.network.ViewPayload payload) {
        dev.infinitygauntlet.client.breach.LiveView.view(payload);
    }

    public static void glitch(dev.infinitygauntlet.network.GlitchPayload payload) {
        dev.infinitygauntlet.client.breach.RealityDistortion.glitch(payload);
    }

    public static void cooldowns(CooldownSyncPayload payload) {
        ClientCooldowns.sync(payload.remaining());
    }

    private ClientPayloadHandler() {
    }
}

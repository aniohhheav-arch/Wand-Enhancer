package dev.mysticarts.client;

import dev.mysticarts.network.Payloads;

/** Client handlers for Mystic Arts payloads. Only ever invoked on the logical client. */
public final class ClientNetwork {
    private ClientNetwork() {}

    public static void onPowerSync(Payloads.PowerSync p) {
        ClientPower.update(p);
    }

    public static void onCasterState(Payloads.CasterState p) {
        CasterStates.update(p);
    }

    public static void onFx(Payloads.Fx p) {
        ClientFx.handle(p);
    }

    public static void onMarks(Payloads.Marks p) {
        ClientMarks.update(p);
    }
}

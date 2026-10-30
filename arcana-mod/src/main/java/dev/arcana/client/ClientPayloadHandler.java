package dev.arcana.client;

import dev.arcana.net.Net;

public final class ClientPayloadHandler {
    public static void sync(Net.Sync p) {
        ClientState.applySync(p.mana(), p.max(), p.xp(), p.cooldowns(), p.recentMask());
    }

    public static void cinematic(Net.Cinematic p) {
        ClientState.startCinematic(p.rgb(), p.ticks(), p.kind(), p.spell(), p.caster());
    }

    private ClientPayloadHandler() {}
}

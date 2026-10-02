package com.infinitemultiverse.power;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Per-tick and lifecycle hooks for power systems that keep server-side state (astral projection, mirror dimension, portals). */
public final class PowerHooks {
    private PowerHooks() {
    }

    static void tick(MinecraftServer server) {
        com.infinitemultiverse.power.mystic.MysticArts.tick(server);
    }

    static void onLogin(ServerPlayer player) {
        com.infinitemultiverse.power.mystic.MysticArts.onLogin(player);
    }

    static void onLogout(ServerPlayer player) {
    }
}

package com.stickyfingers.client;

import com.stickyfingers.ability.AbilityType;
import com.stickyfingers.client.gui.ZipperNetworkScreen;
import com.stickyfingers.network.NetworkSnapshotPayload;
import com.stickyfingers.network.StandSyncPayload;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client mirror of the local player's Stand state. */
public final class ClientState {
    public static boolean hasStand;
    public static boolean summoned;
    public static float energy;
    public static float displayEnergy;
    public static int selected;
    public static boolean hidden;
    public static int exitZipper = -1;
    public static int orientation;
    private static int[] cooldowns = new int[AbilityType.VALUES.length];
    private static long syncTick;
    public static List<NetworkSnapshotPayload.Entry> network = List.of();
    /** entity id -> client tick until which a zipper is attached (drives render distortion). */
    private static final Map<Integer, Long> UNZIPPED = new HashMap<>();
    private static final Map<Integer, Long> BODY = new HashMap<>();
    private static long clientTicks;

    public static void onSync(StandSyncPayload p) {
        hasStand = p.hasStand();
        summoned = p.summoned();
        energy = p.energy();
        selected = p.selected();
        hidden = p.hidden();
        exitZipper = p.exitZipper();
        cooldowns = p.cooldowns();
        syncTick = clientTicks;
    }

    public static void onNetwork(NetworkSnapshotPayload p) {
        network = p.entries();
        Minecraft mc = Minecraft.getInstance();
        if (p.openScreen() && !(mc.screen instanceof ZipperNetworkScreen)) mc.setScreen(new ZipperNetworkScreen());
    }

    public static void tick() {
        clientTicks++;
        displayEnergy += (energy - displayEnergy) * 0.25f;
        UNZIPPED.values().removeIf(t -> t < clientTicks);
        BODY.values().removeIf(t -> t < clientTicks);
    }

    /** Remaining cooldown ticks, counted down locally between syncs. */
    public static int cooldown(AbilityType t) {
        int i = t.ordinal();
        if (i >= cooldowns.length) return 0;
        return (int) Math.max(0, cooldowns[i] - (clientTicks - syncTick));
    }

    public static AbilityType selectedAbility() { return AbilityType.byId(selected); }

    public static void markUnzipped(int entityId) { UNZIPPED.put(entityId, clientTicks + 3); }
    public static boolean isUnzipped(int entityId) { return UNZIPPED.containsKey(entityId); }
    public static void markBody(int entityId) { BODY.put(entityId, clientTicks + 3); }
    public static boolean isBody(int entityId) { return BODY.containsKey(entityId); }
    public static long ticks() { return clientTicks; }

    private ClientState() {}
}

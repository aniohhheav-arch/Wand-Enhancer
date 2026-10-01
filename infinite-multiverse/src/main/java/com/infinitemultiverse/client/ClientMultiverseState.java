package com.infinitemultiverse.client;

import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.network.SyncPlayerDataPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The local player's last synced multiverse state. Held outside the player entity so it survives respawns
 * and dimension changes. Cooldowns are counted down locally between syncs.
 */
public final class ClientMultiverseState {
    private static boolean synced;
    private static float energy;
    private static float maxEnergy = 100f;
    private static float displayedEnergy;
    private static final Map<ResourceLocation, Integer> COOLDOWNS = new HashMap<>();
    private static final List<Optional<ResourceLocation>> LOADOUT = new ArrayList<>();
    private static Set<ResourceLocation> active = Set.of();
    private static Set<ResourceLocation> unlocked = Set.of();

    static {
        reset();
    }

    private ClientMultiverseState() {
    }

    static void apply(SyncPlayerDataPayload payload) {
        if (!synced) {
            displayedEnergy = payload.energy();
        }
        synced = true;
        energy = payload.energy();
        maxEnergy = Math.max(1f, payload.maxEnergy());
        COOLDOWNS.clear();
        COOLDOWNS.putAll(payload.cooldowns());
        LOADOUT.clear();
        for (int i = 0; i < PlayerMultiverseData.LOADOUT_SIZE; i++) {
            LOADOUT.add(i < payload.loadout().size() ? payload.loadout().get(i) : Optional.empty());
        }
        active = Set.copyOf(payload.active());
        unlocked = Set.copyOf(payload.unlocked());
    }

    static void tick() {
        if (!COOLDOWNS.isEmpty()) {
            Iterator<Map.Entry<ResourceLocation, Integer>> it = COOLDOWNS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ResourceLocation, Integer> entry = it.next();
                if (entry.getValue() <= 1) {
                    it.remove();
                } else {
                    entry.setValue(entry.getValue() - 1);
                }
            }
        }
        displayedEnergy += (energy - displayedEnergy) * 0.35f;
    }

    static void reset() {
        synced = false;
        energy = 0f;
        displayedEnergy = 0f;
        maxEnergy = 100f;
        COOLDOWNS.clear();
        LOADOUT.clear();
        for (int i = 0; i < PlayerMultiverseData.LOADOUT_SIZE; i++) {
            LOADOUT.add(Optional.empty());
        }
        active = Set.of();
        unlocked = Set.of();
    }

    public static boolean isSynced() {
        return synced;
    }

    public static float energy() {
        return energy;
    }

    public static float displayedEnergy() {
        return displayedEnergy;
    }

    public static float maxEnergy() {
        return maxEnergy;
    }

    public static int cooldown(ResourceLocation ability) {
        return COOLDOWNS.getOrDefault(ability, 0);
    }

    public static Optional<ResourceLocation> loadoutSlot(int slot) {
        return LOADOUT.get(slot);
    }

    public static boolean isActive(ResourceLocation ability) {
        return active.contains(ability);
    }

    public static boolean hasUnlocked(ResourceLocation ability) {
        return unlocked.contains(ability);
    }

    public static int slotOf(ResourceLocation ability) {
        for (int i = 0; i < LOADOUT.size(); i++) {
            if (LOADOUT.get(i).filter(ability::equals).isPresent()) {
                return i;
            }
        }
        return -1;
    }
}

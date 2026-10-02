package com.infinitemultiverse.power;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class PowerManager {
    private PowerManager() {
    }

    @Nullable
    public static PowerSet powerOf(ServerPlayer player, MultiverseSystem system) {
        return AbilityManager.data(player).power(system.id()).map(MultiverseRegistries.POWER_SETS::get).orElse(null);
    }

    public static List<PowerSet> setsFor(MultiverseSystem system) {
        return MultiverseRegistries.POWER_SETS.stream().filter(set -> set.system() == system).toList();
    }

    /** Grants a power set: replaces any set of the same system, unlocks its abilities and fills empty loadout slots. */
    public static void grant(ServerPlayer player, PowerSet set) {
        forget(player, set.system());
        PlayerMultiverseData data = AbilityManager.data(player);
        data.setPower(set.system().id(), set.id());
        java.util.Set<net.minecraft.resources.ResourceLocation> fresh = new java.util.HashSet<>();
        set.abilities().forEach(ability -> fresh.add(ability.id()));
        for (Ability ability : set.abilities()) {
            data.unlock(ability.id());
            if (data.isInLoadout(ability.id())) {
                continue;
            }
            // Empty slots first; otherwise the newly awakened power takes over slots held by other powers.
            int target = -1;
            for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE && target < 0; slot++) {
                if (data.loadoutSlot(slot).isEmpty()) {
                    target = slot;
                }
            }
            for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE && target < 0; slot++) {
                if (data.loadoutSlot(slot).filter(fresh::contains).isEmpty() && !data.loadoutSlot(slot).map(data::isActive).orElse(false)) {
                    target = slot;
                }
            }
            if (target >= 0) {
                data.setLoadoutSlot(target, ability.id());
            }
        }
        AbilityManager.syncNow(player);
    }

    public static void forget(ServerPlayer player, MultiverseSystem system) {
        PowerSet current = powerOf(player, system);
        if (current == null) {
            return;
        }
        PlayerMultiverseData data = AbilityManager.data(player);
        for (Ability ability : current.abilities()) {
            if (data.isActive(ability.id())) {
                AbilityManager.deactivate(player, data, ability, DeactivationReason.ADMIN);
            }
            data.lock(ability.id());
            for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
                if (data.loadoutSlot(slot).filter(ability.id()::equals).isPresent()) {
                    data.setLoadoutSlot(slot, null);
                }
            }
        }
        data.setPower(system.id(), null);
        AbilityManager.syncNow(player);
    }
}

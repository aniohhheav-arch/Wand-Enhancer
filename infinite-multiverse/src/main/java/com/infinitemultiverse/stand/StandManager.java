package com.infinitemultiverse.stand;

import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.registry.ModEntities;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Server-side lookup of each player's manifested Stand. Entities are never saved, so this map is the only link. */
public final class StandManager {
    private static final Map<UUID, StandEntity> ACTIVE = new HashMap<>();

    private StandManager() {
    }

    @Nullable
    public static StandType standTypeOf(ServerPlayer player) {
        return AbilityManager.data(player).standType().map(MultiverseRegistries.STAND_TYPES::get).orElse(null);
    }

    /** The player's Stand if it is alive, not leaving, and in the player's level. */
    @Nullable
    public static StandEntity get(ServerPlayer player) {
        StandEntity stand = ACTIVE.get(player.getUUID());
        if (stand == null || stand.isRemoved() || stand.level() != player.level() || stand.action() == StandAction.DISMISSING) {
            return null;
        }
        return stand;
    }

    public static StandEntity summon(ServerPlayer player, StandType type) {
        dismiss(player);
        StandEntity stand = new StandEntity(ModEntities.STAND.get(), player.level());
        stand.setOwner(player);
        stand.setStandType(type);
        stand.moveTo(player.getX(), player.getY(), player.getZ(), player.getYHeadRot(), 0f);
        player.serverLevel().addFreshEntity(stand);
        ACTIVE.put(player.getUUID(), stand);
        return stand;
    }

    public static void dismiss(ServerPlayer player) {
        StandEntity stand = ACTIVE.remove(player.getUUID());
        if (stand != null && !stand.isRemoved()) {
            stand.beginDismiss();
        }
    }

    /** Grants a Stand: records it, unlocks its abilities and binds them into empty loadout slots. */
    public static void awaken(ServerPlayer player, StandType type) {
        forget(player);
        PlayerMultiverseData data = AbilityManager.data(player);
        data.setStandType(type.id());
        for (Ability ability : type.abilities()) {
            data.unlock(ability.id());
            if (!data.isInLoadout(ability.id())) {
                for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
                    if (data.loadoutSlot(slot).isEmpty()) {
                        data.setLoadoutSlot(slot, ability.id());
                        break;
                    }
                }
            }
        }
        AbilityManager.syncNow(player);
    }

    /** Removes the player's Stand, its unlocks and its bindings. */
    public static void forget(ServerPlayer player) {
        PlayerMultiverseData data = AbilityManager.data(player);
        StandType current = standTypeOf(player);
        if (current == null) {
            return;
        }
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
        dismiss(player);
        data.setStandType(null);
        AbilityManager.syncNow(player);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}

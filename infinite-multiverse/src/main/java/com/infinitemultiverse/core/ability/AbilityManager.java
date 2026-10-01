package com.infinitemultiverse.core.ability;

import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.network.SyncPlayerDataPayload;
import com.infinitemultiverse.core.registry.ModAttachments;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative ability pipeline. Clients only ever send "slot N pressed" or "bind slot N"; everything else is decided here. */
public final class AbilityManager {
    private static final int ENERGY_SYNC_INTERVAL = 5;

    private AbilityManager() {
    }

    public static PlayerMultiverseData data(Player player) {
        return player.getData(ModAttachments.PLAYER_DATA);
    }

    public static float maxEnergy() {
        return MultiverseConfig.SERVER.maxEnergy.get().floatValue();
    }

    public static boolean isEnergyFree(ServerPlayer player) {
        return player.isCreative() && MultiverseConfig.SERVER.creativeIgnoresEnergy.get();
    }

    public static boolean isUnlocked(Player player, PlayerMultiverseData data, Ability ability) {
        return ability.isUnlockedByDefault() || player.isCreative() || data.hasUnlocked(ability.id());
    }

    @Nullable
    public static Ability lookup(ResourceLocation id) {
        return MultiverseRegistries.ABILITIES.get(id);
    }

    // ---- input ----

    public static void onSlotPressed(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= PlayerMultiverseData.LOADOUT_SIZE) {
            return;
        }
        PlayerMultiverseData data = data(player);
        Optional<ResourceLocation> bound = data.loadoutSlot(slot);
        if (bound.isEmpty()) {
            deny(player, Component.translatable("message.infinitemultiverse.slot_empty", slot + 1));
            return;
        }
        Ability ability = lookup(bound.get());
        if (ability == null) {
            data.setLoadoutSlot(slot, null);
            deny(player, Component.translatable("message.infinitemultiverse.slot_empty", slot + 1));
            return;
        }
        tryActivate(player, ability);
    }

    public static void setLoadoutSlot(ServerPlayer player, int slot, Optional<ResourceLocation> abilityId) {
        if (slot < 0 || slot >= PlayerMultiverseData.LOADOUT_SIZE) {
            return;
        }
        PlayerMultiverseData data = data(player);
        if (abilityId.isPresent()) {
            Ability ability = lookup(abilityId.get());
            if (ability == null) {
                return;
            }
            if (!isUnlocked(player, data, ability)) {
                deny(player, Component.translatable("message.infinitemultiverse.locked", ability.displayName()));
                return;
            }
            for (int i = 0; i < PlayerMultiverseData.LOADOUT_SIZE; i++) {
                if (i != slot && data.loadoutSlot(i).filter(abilityId.get()::equals).isPresent()) {
                    data.setLoadoutSlot(i, null);
                }
            }
        }
        data.setLoadoutSlot(slot, abilityId.orElse(null));
        // An active toggle that is no longer bound could never be switched off by the player.
        for (ResourceLocation active : List.copyOf(data.activeView())) {
            Ability ability = lookup(active);
            if (ability != null && !data.isInLoadout(active)) {
                deactivate(player, data, ability, DeactivationReason.UNBOUND);
            }
        }
        syncNow(player);
    }

    // ---- activation ----

    public static boolean tryActivate(ServerPlayer player, Ability ability) {
        if (player.isSpectator()) {
            return false;
        }
        if (!MultiverseConfig.isSystemEnabled(ability.system())) {
            deny(player, Component.translatable("message.infinitemultiverse.system_disabled", ability.system().displayName()));
            return false;
        }
        PlayerMultiverseData data = data(player);
        ResourceLocation id = ability.id();

        if (ability.activationType() == ActivationType.TOGGLE && data.isActive(id)) {
            deactivate(player, data, ability, DeactivationReason.MANUAL);
            return true;
        }
        if (!isUnlocked(player, data, ability)) {
            deny(player, Component.translatable("message.infinitemultiverse.locked", ability.displayName()));
            return false;
        }
        int cooldown = data.cooldown(id);
        if (cooldown > 0) {
            deny(player, Component.translatable("message.infinitemultiverse.cooldown", ability.displayName(),
                    String.format(Locale.ROOT, "%.1f", cooldown / 20f)));
            return false;
        }
        boolean free = isEnergyFree(player);
        float cost = ability.energyCost();
        if (!free && data.energy() < cost) {
            deny(player, Component.translatable("message.infinitemultiverse.not_enough_energy", ability.displayName(), (int) Math.ceil(cost)));
            return false;
        }

        if (!ability.activate(new AbilityContext(player, player.serverLevel(), data))) {
            return false;
        }

        if (!free) {
            data.consume(cost, MultiverseConfig.SERVER.regenDelayTicks.get());
        }
        if (ability.activationType() == ActivationType.TOGGLE) {
            data.setActive(id);
        } else {
            data.startCooldown(id, ability.cooldownTicks());
        }
        syncNow(player);
        return true;
    }

    public static void deactivate(ServerPlayer player, PlayerMultiverseData data, Ability ability, DeactivationReason reason) {
        ResourceLocation id = ability.id();
        int activeTicks = data.activeTicks(id);
        if (!data.removeActive(id)) {
            return;
        }
        ability.onDeactivate(new AbilityContext(player, player.serverLevel(), data), reason);
        if (reason != DeactivationReason.ADMIN) {
            data.startCooldown(id, ability.toggleCooldown(player, activeTicks));
        }
        if (reason == DeactivationReason.ENERGY_DEPLETED) {
            player.displayClientMessage(Component.translatable("message.infinitemultiverse.collapsed", ability.displayName()), true);
        }
    }

    public static void deactivateAll(ServerPlayer player, DeactivationReason reason) {
        PlayerMultiverseData data = data(player);
        for (ResourceLocation id : List.copyOf(data.activeView())) {
            Ability ability = lookup(id);
            if (ability != null) {
                deactivate(player, data, ability, reason);
            } else {
                data.removeActive(id);
            }
        }
    }

    // ---- ticking ----

    public static void tick(ServerPlayer player) {
        PlayerMultiverseData data = data(player);
        float max = maxEnergy();
        if (!data.isEnergyInitialised() || data.energy() > max) {
            data.setEnergy(max, max);
        }

        data.tickCooldowns();
        data.tickFallProtection();
        tickToggles(player, data);
        data.tickRegen(MultiverseConfig.SERVER.regenPerSecond.get().floatValue() / 20f, max);

        if (data.isStructuralDirty() || (data.isEnergyDirty() && player.tickCount % ENERGY_SYNC_INTERVAL == 0)) {
            sync(player, data);
        }
    }

    private static void tickToggles(ServerPlayer player, PlayerMultiverseData data) {
        if (data.activeView().isEmpty()) {
            return;
        }
        boolean free = isEnergyFree(player);
        for (ResourceLocation id : List.copyOf(data.activeView())) {
            Ability ability = lookup(id);
            if (ability == null) {
                data.removeActive(id);
                continue;
            }
            if (!MultiverseConfig.isSystemEnabled(ability.system())) {
                deactivate(player, data, ability, DeactivationReason.SYSTEM_DISABLED);
                continue;
            }
            if (!free && !data.drain(ability.upkeepPerSecond() / 20f)) {
                deactivate(player, data, ability, DeactivationReason.ENERGY_DEPLETED);
                continue;
            }
            ability.tickActive(new AbilityContext(player, player.serverLevel(), data), data.incrementActiveTicks(id));
        }
    }

    public static void dispatchIncomingDamage(ServerPlayer player, LivingIncomingDamageEvent event) {
        PlayerMultiverseData data = data(player);
        if (data.activeView().isEmpty()) {
            return;
        }
        for (ResourceLocation id : List.copyOf(data.activeView())) {
            if (lookup(id) instanceof OwnerDamageInterceptor interceptor && data.isActive(id)) {
                interceptor.onOwnerIncomingDamage(new AbilityContext(player, player.serverLevel(), data), event);
            }
        }
    }

    // ---- lifecycle ----

    public static void onLogin(ServerPlayer player) {
        PlayerMultiverseData data = data(player);
        data.prune(MultiverseRegistries.ABILITIES::containsKey);
        if (!data.isEnergyInitialised()) {
            data.setEnergy(maxEnergy(), maxEnergy());
        }
        syncNow(player);
    }

    // ---- sync & feedback ----

    public static void syncNow(ServerPlayer player) {
        sync(player, data(player));
    }

    private static void sync(ServerPlayer player, PlayerMultiverseData data) {
        PacketDistributor.sendToPlayer(player, SyncPlayerDataPayload.of(data, maxEnergy()));
        data.clearDirty();
    }

    public static void deny(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        player.playNotifySound(ModSounds.UI_DENIED.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
    }
}

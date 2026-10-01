package com.infinitemultiverse.core.event;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.command.MultiverseCommand;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.deactivateAll(player, DeactivationReason.LOGGED_OUT);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.deactivateAll(player, DeactivationReason.DIED);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.dispatchIncomingDamage(player, event);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMultiverseData data = AbilityManager.data(player);
            if (data.fallProtectionTicks() > 0) {
                event.setDistance(0f);
                event.setDamageMultiplier(0f);
                data.clearFallProtection();
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MultiverseCommand.register(event.getDispatcher());
    }
}

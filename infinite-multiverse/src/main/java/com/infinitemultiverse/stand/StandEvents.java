package com.infinitemultiverse.stand;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.abilities.stand.StandAbilities;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID)
public final class StandEvents {
    private StandEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TimeStopManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TimeStopManager.clear();
        StandManager.clear();
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            TimeStopManager.repairIfStale(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.data(player).pruneStand(MultiverseRegistries.STAND_TYPES::containsKey);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TimeStopManager.endAll(player);
            StandManager.dismiss(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (TimeStopManager.holdDamage(target, event.getSource(), event.getAmount())) {
            event.setCanceled(true);
            return;
        }
        if (target instanceof ServerPlayer player && event.getSource().getDirectEntity() instanceof LivingEntity) {
            StandEntity stand = StandManager.get(player);
            if (stand != null && stand.isGuarding()) {
                event.setAmount(event.getAmount() * (1f - MultiverseConfig.SERVER.guardReduction.get().floatValue()));
            }
        }
    }

    // A player frozen in time can look around but cannot act.

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        cancelIfFrozen(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfFrozen(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfFrozen(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfFrozen(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfFrozen(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        cancelIfFrozen(event.getPlayer(), event);
    }

    private static void cancelIfFrozen(Player player, ICancellableEvent event) {
        if (!player.level().isClientSide && TimeStopManager.isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /** Handles the dedicated Summon/Dismiss key. */
    public static void onToggleKey(ServerPlayer player) {
        if (StandManager.standTypeOf(player) == null) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_stand"));
            return;
        }
        AbilityManager.tryActivate(player, StandAbilities.MANIFEST.get());
    }
}

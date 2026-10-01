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
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import com.infinitemultiverse.abilities.stand.LifeGiverAbility;
import com.infinitemultiverse.abilities.stand.NailShotAbility;
import com.infinitemultiverse.abilities.stand.TimeEraseAbility;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
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
        StandScheduler.tick();
        RewindTracker.tick(event.getServer(), event.getServer().getTickCount());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TimeStopManager.clear();
        StandManager.clear();
        StandScheduler.clear();
        RewindTracker.reset();
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
        if (target instanceof ServerPlayer erased && TimeEraseAbility.isErased(erased)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() != null
                && event.getSource().getEntity().getPersistentData().hasUUID(LifeGiverAbility.CREATOR_TAG)
                && target.getUUID().equals(event.getSource().getEntity().getPersistentData().getUUID(LifeGiverAbility.CREATOR_TAG))) {
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

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide || !projectile.getPersistentData().contains(NailShotAbility.VARIANT_TAG)) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ServerLevel level = (ServerLevel) projectile.level();
        String variant = projectile.getPersistentData().getString(NailShotAbility.VARIANT_TAG);
        Vec3 direction = projectile.getDeltaMovement().lengthSqr() > 1.0E-6 ? projectile.getDeltaMovement().normalize() : Vec3.ZERO;
        MultiverseVfx.broadcast(level, VfxIds.STAND_PUNCH, target.getBoundingBox().getCenter(), direction, 1f);
        if (NailShotAbility.Variant.GOLDEN.name().equals(variant)) {
            target.knockback(1.5, -direction.x, -direction.z);
            MultiverseVfx.broadcast(level, VfxIds.STAND_HEAVY, target.getBoundingBox().getCenter(), direction, 1f);
        } else if (NailShotAbility.Variant.INFINITE.name().equals(variant)) {
            // The rotation never stops: unblockable damage every half second for 10 seconds, plus withering.
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
            Entity owner = projectile.getOwner();
            StandScheduler.repeat(10, 10, 20, index -> {
                if (!target.isAlive()) {
                    return false;
                }
                target.invulnerableTime = 0;
                target.hurt(owner != null ? level.damageSources().indirectMagic(projectile, owner) : level.damageSources().magic(), 1.5f);
                MultiverseVfx.broadcast(level, VfxIds.STAND_PUNCH, target.getBoundingBox().getCenter(), Vec3.ZERO, 0.6f);
                return true;
            });
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

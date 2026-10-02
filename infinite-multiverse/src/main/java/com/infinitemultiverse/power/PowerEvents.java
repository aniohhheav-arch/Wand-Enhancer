package com.infinitemultiverse.power;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.power.cursed.BlackFlashAbility;
import com.infinitemultiverse.power.cursed.DomainManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID)
public final class PowerEvents {
    private PowerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DomainManager.tick(event.getServer());
        PowerHooks.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.data(player).prunePowers(MultiverseRegistries.POWER_SETS::containsKey);
            PowerHooks.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DomainManager.close(player);
            PowerHooks.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DomainManager.closeAll();
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypes.PLAYER_ATTACK) && event.getSource().getDirectEntity() instanceof ServerPlayer attacker
                && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            BlackFlashAbility.onMeleeHit(attacker, event.getEntity(), event);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getProjectile().getTags().contains(com.infinitemultiverse.power.mutant.MutantAbilities.ICE_SHARD_TAG)
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity target
                && event.getProjectile().getOwner() instanceof ServerPlayer owner
                && com.infinitemultiverse.core.ability.AbilityTargeting.isHostileTarget(owner, target)) {
            target.invulnerableTime = 0;
            target.hurt(owner.damageSources().indirectMagic(event.getProjectile(), owner), 6f);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
        }
    }
}

package com.infinitemultiverse.cosmic.space;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModEntities;
import com.infinitemultiverse.cosmic.rift.RiftEntity;
import com.infinitemultiverse.power.mystic.AstralState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Low gravity and oxygen in Orbit; space-suit tanks refill planetside; natural rifts at night. */
@EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID)
public final class SpaceEvents {
    private static final ResourceLocation LOW_GRAVITY = InfiniteMultiverse.id("orbit_gravity");

    private SpaceEvents() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide || living.tickCount % 20 != 0) {
            return;
        }
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        boolean space = Space.inSpace(living);
        if (space && !gravity.hasModifier(LOW_GRAVITY)) {
            gravity.addTransientModifier(new AttributeModifier(LOW_GRAVITY, MultiverseConfig.SERVER.orbitGravity.get() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 40, 2, false, false));
        } else if (!space && gravity.hasModifier(LOW_GRAVITY)) {
            gravity.removeModifier(LOW_GRAVITY);
        }
        if (space && living.tickCount % 20 == 0 && !(living instanceof ServerPlayer)) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 40, 2, false, false));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean suit = Space.wearingSuit(player);
        if (!Space.inSpace(player)) {
            if (suit && player.tickCount % 2 == 0 && Space.oxygen(helmet) < Space.TANK) {
                Space.setOxygen(helmet, Space.oxygen(helmet) + 40);
            }
            return;
        }
        if (player.isCreative() || player.isSpectator() || AstralState.isProjecting(player)) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 40, 2, false, false));
        }
        int max = player.getMaxAirSupply();
        if (suit && Space.oxygen(helmet) > 0) {
            int o2 = Space.oxygen(helmet) - 1;
            Space.setOxygen(helmet, o2);
            player.setAirSupply(Math.max(1, Math.round(max * o2 / (float) Space.TANK)));
            return;
        }
        // No suit (or an empty tank): you are holding your breath in vacuum.
        int air = player.getAirSupply() - 6;
        if (air <= -20) {
            air = 0;
            player.hurt(player.damageSources().drown(), 3f);
        }
        player.setAirSupply(air);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 6000 != 0) {
            return;
        }
        double chance = MultiverseConfig.SERVER.naturalRiftChance.get();
        if (chance <= 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (level.dimension() != Level.OVERWORLD || level.isDay() || level.random.nextDouble() >= chance) {
                continue;
            }
            double a = level.random.nextDouble() * Math.PI * 2, d = 12 + level.random.nextDouble() * 10;
            int x = (int) (player.getX() + Math.cos(a) * d), z = (int) (player.getZ() + Math.sin(a) * d);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            RiftEntity rift = ModEntities.RIFT.get().create(level);
            if (rift != null) {
                rift.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360, 0);
                rift.setLifetime(900);
                level.addFreshEntity(rift);
            }
        }
    }
}

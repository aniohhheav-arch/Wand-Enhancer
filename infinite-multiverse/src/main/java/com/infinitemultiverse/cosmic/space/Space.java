package com.infinitemultiverse.cosmic.space;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.cosmic.CosmicContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Shared space rules: the Orbit dimension, the space suit's oxygen tank, and the orbital station. */
public final class Space {
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION, InfiniteMultiverse.id("orbit"));
    public static final int TANK = 6000;
    public static final int STATION_Y = 150;

    private Space() {
    }

    public static boolean inSpace(LivingEntity entity) {
        return entity.level().dimension() == ORBIT;
    }

    public static boolean wearingSuit(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getMaterial().value() != CosmicContent.SPACE_SUIT_MATERIAL.value()) {
                return false;
            }
        }
        return true;
    }

    public static int oxygen(ItemStack helmet) {
        CustomData data = helmet.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.copyTag().contains("oxygen") ? TANK : data.copyTag().getInt("oxygen");
    }

    public static void setOxygen(ItemStack helmet, int value) {
        CustomData data = helmet.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putInt("oxygen", Math.max(0, Math.min(TANK, value)));
        helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** A small open-air station deck under the arrival point, built once and kept. */
    public static void ensureStation(ServerLevel orbit, int x, int z) {
        BlockPos center = new BlockPos(x, STATION_Y, z);
        if (!orbit.getBlockState(center).isAir()) {
            return;
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean edge = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                boolean light = (dx % 3 == 0) && (dz % 3 == 0);
                orbit.setBlockAndUpdate(center.offset(dx, 0, dz), edge ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : light ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
                if (edge && (dx + dz) % 2 == 0) {
                    orbit.setBlockAndUpdate(center.offset(dx, 1, dz), Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
    }
}

package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, StickyFingersMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> STAND = REGISTER.register("sticky_fingers",
            () -> EntityType.Builder.<StandEntity>of(StandEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).updateInterval(1).noSummon().noSave().build("sticky_fingers"));
    public static final DeferredHolder<EntityType<?>, EntityType<ZipperEntity>> ZIPPER = REGISTER.register("zipper",
            () -> EntityType.Builder.<ZipperEntity>of(ZipperEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(2).noSummon().fireImmune().build("zipper"));
    public static final DeferredHolder<EntityType<?>, EntityType<ZipperProjectileEntity>> ZIPPER_PROJECTILE = REGISTER.register("zipper_projectile",
            () -> EntityType.Builder.<ZipperProjectileEntity>of(ZipperProjectileEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).noSummon().noSave().build("zipper_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<DecoyEntity>> DECOY = REGISTER.register("zipper_decoy",
            () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).noSummon().noSave().build("zipper_decoy"));

    private ModEntities() {}
}

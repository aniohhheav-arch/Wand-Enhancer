package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, InfiniteMultiverse.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> STAND = ENTITY_TYPES.register("stand",
            () -> EntityType.Builder.<StandEntity>of(StandEntity::new, MobCategory.MISC)
                    .sized(0.9f, 2.3f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("stand"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.infinitemultiverse.power.mystic.PhysicalBodyEntity>> PHYSICAL_BODY = ENTITY_TYPES.register("physical_body",
            () -> EntityType.Builder.<com.infinitemultiverse.power.mystic.PhysicalBodyEntity>of(com.infinitemultiverse.power.mystic.PhysicalBodyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSummon()
                    .build("physical_body"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.infinitemultiverse.cosmic.portal.PortalEntity>> PORTAL = ENTITY_TYPES.register("portal",
            () -> EntityType.Builder.<com.infinitemultiverse.cosmic.portal.PortalEntity>of(com.infinitemultiverse.cosmic.portal.PortalEntity::new, MobCategory.MISC)
                    .sized(1.0f, 2.0f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSummon()
                    .fireImmune()
                    .build("portal"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.infinitemultiverse.cosmic.rift.RiftEntity>> RIFT = ENTITY_TYPES.register("rift",
            () -> EntityType.Builder.<com.infinitemultiverse.cosmic.rift.RiftEntity>of(com.infinitemultiverse.cosmic.rift.RiftEntity::new, MobCategory.MISC)
                    .sized(1.2f, 3.0f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("rift"));

    private ModEntities() {
    }
}

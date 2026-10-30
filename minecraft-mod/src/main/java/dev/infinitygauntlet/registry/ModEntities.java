package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.entity.MiniPortalEntity;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.entity.SpaceBoltEntity;
import dev.infinitygauntlet.entity.SpatialCompressionEntity;
import dev.infinitygauntlet.entity.SpatialPrisonEntity;
import dev.infinitygauntlet.entity.SpatialRiftEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, InfinityGauntlet.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<RupturePortalEntity>> RUPTURE_PORTAL =
            register("rupture_portal", RupturePortalEntity::new, 1.0f, 1.0f, 16, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<SpatialRiftEntity>> SPATIAL_RIFT =
            register("spatial_rift", SpatialRiftEntity::new, 1.0f, 0.25f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<SpatialCompressionEntity>> SPATIAL_COMPRESSION =
            register("spatial_compression", SpatialCompressionEntity::new, 1.0f, 1.0f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<SpatialPrisonEntity>> SPATIAL_PRISON =
            register("spatial_prison", SpatialPrisonEntity::new, 1.0f, 1.0f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<MiniPortalEntity>> MINI_PORTAL =
            register("mini_portal", MiniPortalEntity::new, 0.6f, 0.6f, 8, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<SpaceBoltEntity>> SPACE_BOLT =
            register("space_bolt", SpaceBoltEntity::new, 0.4f, 0.4f, 8, 1);

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            String name, EntityType.EntityFactory<T> factory, float width, float height, int trackingRange, int updateInterval) {
        return REGISTER.register(name, () -> EntityType.Builder.of(factory, MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .fireImmune()
                .noSummon()
                .build(InfinityGauntlet.MOD_ID + ":" + name));
    }

    private ModEntities() {
    }
}

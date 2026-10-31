package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.entity.ChronoBarrierEntity;
import dev.infinitygauntlet.entity.MiniPortalEntity;
import dev.infinitygauntlet.entity.TemporalEchoEntity;
import dev.infinitygauntlet.entity.TemporalLoopEntity;
import dev.infinitygauntlet.entity.TimeFieldEntity;
import dev.infinitygauntlet.entity.TimeFractureEntity;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.entity.SpaceBoltEntity;
import dev.infinitygauntlet.entity.SpatialCompressionEntity;
import dev.infinitygauntlet.entity.SpatialPrisonEntity;
import dev.infinitygauntlet.entity.SpatialRiftEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
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

    public static final DeferredHolder<EntityType<?>, EntityType<TimeFieldEntity>> TIME_FIELD =
            register("time_field", TimeFieldEntity::new, 1.0f, 1.0f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<TemporalLoopEntity>> TEMPORAL_LOOP =
            register("temporal_loop", TemporalLoopEntity::new, 1.0f, 0.25f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<ChronoBarrierEntity>> CHRONO_BARRIER =
            register("chrono_barrier", ChronoBarrierEntity::new, 0.5f, 0.5f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<TimeFractureEntity>> TIME_FRACTURE =
            register("time_fracture", TimeFractureEntity::new, 1.0f, 1.0f, 16, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<TemporalEchoEntity>> TEMPORAL_ECHO =
            REGISTER.register("temporal_echo", () -> EntityType.Builder.<TemporalEchoEntity>of(TemporalEchoEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .noSummon()
                    .build(InfinityGauntlet.MOD_ID + ":temporal_echo"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.breach.DimensionalRiftEntity>> DIMENSIONAL_RIFT =
            register("dimensional_rift", dev.infinitygauntlet.breach.DimensionalRiftEntity::new, 1.0f, 1.0f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.breach.ObservationPanelEntity>> OBSERVATION_PANEL =
            register("observation_panel", dev.infinitygauntlet.breach.ObservationPanelEntity::new, 2.5f, 1.4f, 8, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.breach.SpecimenEntity>> SPECIMEN =
            register("specimen", dev.infinitygauntlet.breach.SpecimenEntity::new, 1.5f, 2.0f, 8, 1);

    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.entity.power.PowerOrbEntity>> POWER_ORB =
            register("power_orb", dev.infinitygauntlet.entity.power.PowerOrbEntity::new, 0.8f, 0.8f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.entity.power.GravityCoreEntity>> GRAVITY_CORE =
            register("gravity_core", dev.infinitygauntlet.entity.power.GravityCoreEntity::new, 2.0f, 2.0f, 12, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.entity.power.PlanetaryCoreEntity>> PLANETARY_CORE =
            register("planetary_core", dev.infinitygauntlet.entity.power.PlanetaryCoreEntity::new, 2.0f, 2.0f, 16, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.entity.power.SeveredHeadEntity>> SEVERED_HEAD =
            register("severed_head", dev.infinitygauntlet.entity.power.SeveredHeadEntity::new, 0.4f, 0.4f, 8, 1);

    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.mind.AstralBodyEntity>> ASTRAL_BODY =
            register("astral_body", dev.infinitygauntlet.mind.AstralBodyEntity::new, 0.6f, 1.8f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.mind.LevitationFieldEntity>> LEVITATION_FIELD =
            register("levitation_field", dev.infinitygauntlet.mind.LevitationFieldEntity::new, 1.0f, 1.0f, 10, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<dev.infinitygauntlet.mind.MentalShieldEntity>> MENTAL_SHIELD =
            register("mental_shield", dev.infinitygauntlet.mind.MentalShieldEntity::new, 1.0f, 1.0f, 10, 1);

    @SubscribeEvent
    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TEMPORAL_ECHO.get(), TemporalEchoEntity.attributes().build());
    }

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

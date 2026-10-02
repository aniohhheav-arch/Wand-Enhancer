package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.entity.MysticBoltEntity;
import dev.mysticarts.entity.MysticCloneEntity;
import dev.mysticarts.entity.ShockwaveEntity;
import dev.mysticarts.entity.SlingPortalEntity;
import dev.mysticarts.entity.SoulWispEntity;
import dev.mysticarts.entity.SpectralBeastEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MysticArts.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SlingPortalEntity>> SLING_PORTAL = ENTITIES.register("sling_portal",
            () -> EntityType.Builder.<SlingPortalEntity>of(SlingPortalEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(12).updateInterval(10).fireImmune().build("sling_portal"));
    public static final DeferredHolder<EntityType<?>, EntityType<MysticBoltEntity>> MYSTIC_BOLT = ENTITIES.register("mystic_bolt",
            () -> EntityType.Builder.<MysticBoltEntity>of(MysticBoltEntity::new, MobCategory.MISC).sized(0.35F, 0.35F)
                    .clientTrackingRange(8).updateInterval(1).fireImmune().build("mystic_bolt"));
    public static final DeferredHolder<EntityType<?>, EntityType<ShockwaveEntity>> SHOCKWAVE = ENTITIES.register("shockwave",
            () -> EntityType.Builder.<ShockwaveEntity>of(ShockwaveEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(12).updateInterval(20).fireImmune().build("shockwave"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellFieldEntity>> SPELL_FIELD = ENTITIES.register("spell_field",
            () -> EntityType.Builder.<SpellFieldEntity>of(SpellFieldEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(16).updateInterval(2).fireImmune().build("spell_field"));
    public static final DeferredHolder<EntityType<?>, EntityType<SoulWispEntity>> SOUL_WISP = ENTITIES.register("soul_wisp",
            () -> EntityType.Builder.<SoulWispEntity>of(SoulWispEntity::new, MobCategory.MISC).sized(0.4F, 0.4F)
                    .clientTrackingRange(8).updateInterval(2).fireImmune().build("soul_wisp"));
    public static final DeferredHolder<EntityType<?>, EntityType<MysticCloneEntity>> MYSTIC_CLONE = ENTITIES.register("mystic_clone",
            () -> EntityType.Builder.<MysticCloneEntity>of(MysticCloneEntity::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).fireImmune().build("mystic_clone"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpectralBeastEntity>> SPECTRAL_BEAST = ENTITIES.register("spectral_beast",
            () -> EntityType.Builder.<SpectralBeastEntity>of(SpectralBeastEntity::new, MobCategory.MISC).sized(1.1F, 1.4F)
                    .clientTrackingRange(10).fireImmune().build("spectral_beast"));

    private MaEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MYSTIC_CLONE.get(), MysticCloneEntity.createAttributes().build());
        event.put(SPECTRAL_BEAST.get(), SpectralBeastEntity.createAttributes().build());
    }
}

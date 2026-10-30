package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.entity.ArchmageEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ArcanaMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ArchmageEntity>> ARCHMAGE = ENTITIES.register("hollow_archmage",
        () -> EntityType.Builder.of(ArchmageEntity::new, MobCategory.MONSTER).sized(0.7f, 2.4f).fireImmune().clientTrackingRange(10)
            .build(ArcanaMod.MODID + ":hollow_archmage"));

    private ModEntities() {}
}

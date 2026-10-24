package dev.telephoneorder;

import dev.telephoneorder.entity.GunProjectile;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static EntityType<GunProjectile> GUN_PROJECTILE;

    private ModEntities() {}

    public static void register() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ModItems.id("gun_projectile"));
        GUN_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<GunProjectile>of(GunProjectile::new, MobCategory.MISC).noLootTable().sized(0.3f, 0.3f)
                        .clientTrackingRange(10).updateInterval(1).noSave().build(key));
    }
}

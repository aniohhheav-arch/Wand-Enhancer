package dev.arcana.client;

import dev.arcana.ArcanaMod;
import dev.arcana.entity.ArchmageEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ArchmageGeoModel extends GeoModel<ArchmageEntity> {
    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, path);
    }

    @Override public ResourceLocation getModelResource(ArchmageEntity e) { return rl("geo/entity/hollow_archmage.geo.json"); }
    @Override public ResourceLocation getTextureResource(ArchmageEntity e) { return rl("textures/entity/hollow_archmage.png"); }
    @Override public ResourceLocation getAnimationResource(ArchmageEntity e) { return rl("animations/entity/hollow_archmage.animation.json"); }
}

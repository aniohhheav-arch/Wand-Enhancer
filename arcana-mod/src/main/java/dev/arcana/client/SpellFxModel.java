package dev.arcana.client;

import dev.arcana.ArcanaMod;
import dev.arcana.entity.SpellFxEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SpellFxModel extends GeoModel<SpellFxEntity> {
    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, path);
    }

    @Override public ResourceLocation getModelResource(SpellFxEntity e) { return rl("geo/entity/fx_" + e.kind().id + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(SpellFxEntity e) { return rl("textures/entity/fx_" + e.kind().id + ".png"); }
    @Override public ResourceLocation getAnimationResource(SpellFxEntity e) { return rl("animations/entity/fx_" + e.kind().id + ".animation.json"); }
}

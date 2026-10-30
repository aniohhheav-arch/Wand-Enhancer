package dev.arcana.client;

import dev.arcana.ArcanaMod;
import dev.arcana.item.WandItem;
import dev.arcana.magic.School;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Geometry per school; the Forbidden Wand re-skins itself to the selected ability. */
public class WandGeoModel extends GeoModel<WandItem> {
    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(WandItem wand) {
        return rl("geo/item/" + wand.school.id + "_wand.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WandItem wand) {
        return rl("textures/item/geo/" + wand.school.id + "_wand.png");
    }

    @Override
    public ResourceLocation getTextureResource(WandItem wand, GeoRenderer<WandItem> renderer) {
        if (wand.school == School.NONE && renderer instanceof GeoItemRenderer<WandItem> ir && ir.getCurrentItemStack() != null) {
            return rl("textures/item/geo/forbidden_look_" + WandItem.selectedOf(ir.getCurrentItemStack()).lookIndex() + ".png");
        }
        return getTextureResource(wand);
    }

    @Override
    public ResourceLocation getAnimationResource(WandItem wand) {
        return rl("animations/item/wand.animation.json");
    }
}

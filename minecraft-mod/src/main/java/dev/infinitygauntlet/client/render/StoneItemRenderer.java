package dev.infinitygauntlet.client.render;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.item.InfinityStoneItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Shared animated gem model for all six stones, textured per stone. */
public class StoneItemRenderer extends GeoItemRenderer<InfinityStoneItem> {
    public StoneItemRenderer() {
        super(new StoneModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static final class StoneModel extends GeoModel<InfinityStoneItem> {
        private static final ResourceLocation MODEL = InfinityGauntlet.id("geo/item/infinity_stone.geo.json");
        private static final ResourceLocation ANIMATION = InfinityGauntlet.id("animations/item/infinity_stone.animation.json");

        @Override
        public ResourceLocation getModelResource(InfinityStoneItem animatable) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(InfinityStoneItem animatable) {
            return InfinityGauntlet.id("textures/item/" + animatable.stone().getSerializedName() + "_stone_model.png");
        }

        @Override
        public ResourceLocation getAnimationResource(InfinityStoneItem animatable) {
            return ANIMATION;
        }
    }
}

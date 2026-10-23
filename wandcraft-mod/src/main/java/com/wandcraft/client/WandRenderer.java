package com.wandcraft.client;

import com.wandcraft.WandCraft;
import com.wandcraft.item.WandItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** GeckoLib renderer for the animated wand: floating crystal, orbiting shards, glow layer for the gem. */
public class WandRenderer extends GeoItemRenderer<WandItem> {
    public WandRenderer() {
        super(new DefaultedItemGeoModel<WandItem>(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "wand"))
                .withAltTexture(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "wand_geo")));
        addRenderLayer(new EmissiveLayer<>(this, w -> EmissiveLayer.glowOf(getTextureLocation(w))));
    }
}

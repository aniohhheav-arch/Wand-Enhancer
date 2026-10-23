package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Fullbright additive overlay, drawn like vanilla spider eyes. The glow texture is the model texture's path with a
 * {@code _glowing} suffix (same convention as GeckoLib's auto layer), but rendered with the standard eyes render type.
 */
public class EmissiveLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private final Function<T, ResourceLocation> glowTexture;

    public EmissiveLayer(GeoRenderer<T> renderer, Function<T, ResourceLocation> glowTexture) {
        super(renderer);
        this.glowTexture = glowTexture;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation tex = glowTexture.apply(animatable);
        if (tex == null) return;
        RenderType glow = RenderType.eyes(tex);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                15728640, packedOverlay, 0xFFFFFFFF);
    }

    public static ResourceLocation glowOf(ResourceLocation base) {
        String p = base.getPath();
        return base.withPath(p.substring(0, p.length() - 4) + "_glowing.png");
    }
}

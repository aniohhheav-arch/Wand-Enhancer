package com.infinitemultiverse.client.stand;

import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/** Translucent, spectral rendering with emissive eyes and emblem; fades in on summon and out on dismiss. */
public final class StandRenderer extends GeoEntityRenderer<StandEntity> {
    private static final float BASE_OPACITY = 0.88f;

    public StandRenderer(EntityRendererProvider.Context context) {
        super(context, new StandModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;
    }

    @Override
    public RenderType getRenderType(StandEntity stand, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(StandEntity stand, float partialTick, int packedLight) {
        return Color.ofRGBA(1f, 1f, 1f, BASE_OPACITY * stand.fade(partialTick));
    }
}

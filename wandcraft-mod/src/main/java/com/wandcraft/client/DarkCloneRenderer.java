package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wandcraft.WandCraft;
import com.wandcraft.entity.DarkClone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The Dark Clone: translucent black silhouette with glowing eyes and violet veins. */
public class DarkCloneRenderer extends GeoEntityRenderer<DarkClone> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "textures/entity/dark_clone.png");

    public DarkCloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<DarkClone>(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "dark_clone"), true) {
            @Override public ResourceLocation getTextureResource(DarkClone e) { return TEX; }
        });
        addRenderLayer(new EmissiveLayer<>(this, e -> EmissiveLayer.glowOf(TEX)));
        shadowRadius = 0.0f;
        withScale(1.0f);
    }

    @Override
    public RenderType getRenderType(DarkClone e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void render(DarkClone e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(e, yaw, pt, pose, buffers, Math.max(light & 0xFFFF, 0x60) | (light & 0xFFFF0000));
    }
}

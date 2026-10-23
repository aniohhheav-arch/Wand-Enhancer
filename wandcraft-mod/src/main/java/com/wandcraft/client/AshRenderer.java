package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wandcraft.WandCraft;
import com.wandcraft.entity.AshPile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Ash pile: dark mound lying on the terrain with faintly glowing embers. */
public class AshRenderer extends GeoEntityRenderer<AshPile> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "textures/entity/ash_pile.png");

    public AshRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<AshPile>(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "ash_pile"), false));
        addRenderLayer(new EmissiveLayer<>(this, e -> EmissiveLayer.glowOf(TEX)));
        shadowRadius = 0f;
    }

    @Override
    public RenderType getRenderType(AshPile e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    protected void applyRotations(AshPile e, PoseStack pose, float age, float bodyYaw, float pt, float nativeScale) {
        float s = e.scale();
        pose.scale(s, s, s);
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
    }
}

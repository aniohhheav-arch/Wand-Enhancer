package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wandcraft.entity.DebrisEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Renders a tumbling block copy. */
public class DebrisRenderer extends EntityRenderer<DebrisEntity> {
    public DebrisRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(DebrisEntity e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(DebrisEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float t = e.tickCount + pt;
        pose.translate(0, 0.25, 0);
        pose.mulPose(Axis.XP.rotationDegrees(t * e.spinX));
        pose.mulPose(Axis.ZP.rotationDegrees(t * e.spinZ));
        pose.scale(0.55f, 0.55f, 0.55f);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}

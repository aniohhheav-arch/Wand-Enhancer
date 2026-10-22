package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.entity.D4CPortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Renders D4C portals as glowing dimensional rings. */
public class D4CPortalRenderer extends EntityRenderer<D4CPortalEntity> {
    private static final ResourceLocation PORTAL_TEX = StickyFingersMod.id("textures/entity/d4c_portal.png");

    public D4CPortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(D4CPortalEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float age = entity.tickCount + partialTick;
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.12f);
        float alpha = Math.min(1.0f, age / 10f) * 0.75f;

        ps.scale(pulse, pulse, pulse);

        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(PORTAL_TEX));
        Matrix4f m = ps.last().pose();
        float hw = 0.75f;
        float hh = 1.25f;
        // Draw a simple quad facing the camera — must supply UV0, UV1(overlay), UV2(light), Normal
        vc.addVertex(m, -hw, 0,      0).setColor(0.6f, 0.4f, 1.0f, alpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        vc.addVertex(m, -hw, hh * 2, 0).setColor(0.6f, 0.4f, 1.0f, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        vc.addVertex(m,  hw, hh * 2, 0).setColor(0.6f, 0.4f, 1.0f, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        vc.addVertex(m,  hw, 0,      0).setColor(0.6f, 0.4f, 1.0f, alpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);

        ps.popPose();
        super.render(entity, yaw, partialTick, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(D4CPortalEntity entity) {
        return PORTAL_TEX;
    }
}

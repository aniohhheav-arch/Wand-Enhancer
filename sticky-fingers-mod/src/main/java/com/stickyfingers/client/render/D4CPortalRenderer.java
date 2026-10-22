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

/** Renders D4C portals as three animated concentric spinning rings + translucent oval face. */
public class D4CPortalRenderer extends EntityRenderer<D4CPortalEntity> {
    private static final ResourceLocation PORTAL_TEX = StickyFingersMod.id("textures/entity/d4c_portal.png");
    private static final int RING_SEGMENTS = 32;

    public D4CPortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(D4CPortalEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float age = entity.tickCount + partialTick;
        float appear = Math.min(1.0f, age / 12f);
        float pulse = 0.92f + 0.08f * Mth.sin(age * 0.14f);

        ps.scale(appear * pulse, appear * pulse, appear * pulse);
        // Face the player: rotate around Y so the portal faces the viewer
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(entity.getYRot() + 180f));
        ps.translate(0, 1.25, 0); // lift to stand-height center

        float alpha = 0.82f * appear;

        // ── Translucent oval face ──
        renderOval(ps, buf, light, alpha * 0.45f, 0.70f, 1.20f, 0);

        // ── Three spinning rings ──
        renderRing(ps, buf, age, light, 0.72f, 1.25f, 0.0f,  alpha, 0.60f, 0.95f, 1.0f); // outer teal
        renderRing(ps, buf, age, light, 0.56f, 0.98f, 1.2f,  alpha, 0.40f, 0.75f, 1.0f); // mid purple-blue
        renderRing(ps, buf, age, light, 0.40f, 0.72f, -1.8f, alpha, 0.80f, 0.95f, 1.0f); // inner white

        ps.popPose();
        super.render(entity, yaw, partialTick, ps, buf, light);
    }

    /** Draw a ring (ellipse outline) with given radii rotated at speed. */
    private static void renderRing(PoseStack ps, MultiBufferSource buf, float age, int light,
                                   float rx, float ry, float speed, float alpha, float r, float g, float b) {
        ps.pushPose();
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(age * speed * 3.0f));

        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        Matrix4f m = ps.last().pose();
        // lines render type: Position + Color + Normal
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            float a0 = (float) (i * 2 * Math.PI / RING_SEGMENTS);
            float a1 = (float) ((i + 1) * 2 * Math.PI / RING_SEGMENTS);
            float x0 = Mth.cos(a0) * rx;
            float y0 = Mth.sin(a0) * ry;
            float x1 = Mth.cos(a1) * rx;
            float y1 = Mth.sin(a1) * ry;
            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, alpha).setNormal(0, 0, 1);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha).setNormal(0, 0, 1);
        }
        ps.popPose();
    }

    /** Draw a filled translucent oval using NEW_ENTITY format. */
    private static void renderOval(PoseStack ps, MultiBufferSource buf, int light,
                                   float alpha, float rx, float ry, float rotDeg) {
        ps.pushPose();
        if (rotDeg != 0) ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotDeg));

        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(PORTAL_TEX));
        Matrix4f m = ps.last().pose();

        // Fan-triangulate: emit quads as pairs of triangles using the vertex format
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = (float) (i * 2 * Math.PI / RING_SEGMENTS);
            float a1 = (float) ((i + 1) * 2 * Math.PI / RING_SEGMENTS);
            float x0 = Mth.cos(a0) * rx, y0 = Mth.sin(a0) * ry;
            float x1 = Mth.cos(a1) * rx, y1 = Mth.sin(a1) * ry;
            float u0 = 0.5f + Mth.cos(a0) * 0.5f, v0 = 0.5f - Mth.sin(a0) * 0.5f;
            float u1 = 0.5f + Mth.cos(a1) * 0.5f, v1 = 0.5f - Mth.sin(a1) * 0.5f;
            // quad: center, p0, p1 — emit as two tris via 4-vertex quad
            vc.addVertex(m, 0,  0,  0).setColor(0.3f, 0.6f, 1.0f, alpha).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
            vc.addVertex(m, x0, y0, 0).setColor(0.4f, 0.7f, 1.0f, alpha * 0.7f).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
            vc.addVertex(m, x1, y1, 0).setColor(0.4f, 0.7f, 1.0f, alpha * 0.7f).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
            vc.addVertex(m, 0,  0,  0).setColor(0.3f, 0.6f, 1.0f, alpha).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        }
        ps.popPose();
    }

    // keep the field accessible for the static methods
    private static final ResourceLocation PORTAL_TEX_REF = PORTAL_TEX;

    @Override
    public ResourceLocation getTextureLocation(D4CPortalEntity entity) {
        return PORTAL_TEX;
    }
}

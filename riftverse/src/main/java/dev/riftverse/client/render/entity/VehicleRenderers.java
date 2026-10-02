package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.RvShaders;
import dev.riftverse.entity.vehicle.DeLoreanEntity;
import dev.riftverse.entity.vehicle.TardisEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Box-built models for the time machines. */
public final class VehicleRenderers {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private VehicleRenderers() {}

    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        FxDraw.box(vc, p, x0, y0, z0, x1, y1, z1, rgb, 1f, light, OverlayTexture.NO_OVERLAY);
    }

    /** Stainless wedge sports car with gull-wing doors, flux-capacitor glow, time-circuit strip and rear vents. */
    public static final class DeLorean extends EntityRenderer<DeLoreanEntity> {
        public DeLorean(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 1.2f;
        }

        @Override
        public void render(DeLoreanEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
            if (e.isInvisible()) return;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
            PoseStack.Pose p = pose.last();
            VertexConsumer s = buf.getBuffer(RvRenderTypes.solid());
            int steel = 0xB8BCC4, dark = 0x2A2C30, glass = 0x1A2430, red = 0xC02020;
            // chassis and wedge body (z+ is the front)
            box(s, p, -0.9f, 0.25f, -2.1f, 0.9f, 0.75f, 2.1f, steel, light);
            box(s, p, -0.85f, 0.75f, 1.0f, 0.85f, 0.85f, 2.05f, steel, light);
            box(s, p, -0.8f, 0.75f, -1.2f, 0.8f, 1.25f, 0.9f, steel, light);
            box(s, p, -0.75f, 0.8f, 0.9f, 0.75f, 1.2f, 1.05f, glass, light);
            box(s, p, -0.82f, 0.85f, -0.9f, -0.79f, 1.18f, 0.6f, glass, light);
            box(s, p, 0.79f, 0.85f, -0.9f, 0.82f, 1.18f, 0.6f, glass, light);
            // rear louvres, bumpers, lights
            for (int i = 0; i < 5; i++) box(s, p, -0.75f, 0.8f + i * 0.02f, -2.0f + i * 0.16f, 0.75f, 0.84f + i * 0.02f, -1.92f + i * 0.16f, dark, light);
            box(s, p, -0.92f, 0.25f, 2.05f, 0.92f, 0.4f, 2.2f, dark, light);
            box(s, p, -0.92f, 0.25f, -2.2f, 0.92f, 0.4f, -2.05f, dark, light);
            box(s, p, -0.85f, 0.5f, -2.12f, -0.4f, 0.62f, -2.08f, red, light);
            box(s, p, 0.4f, 0.5f, -2.12f, 0.85f, 0.62f, -2.08f, red, light);
            // reactor on the back
            box(s, p, -0.25f, 0.85f, -1.9f, 0.25f, 1.2f, -1.4f, 0xD0D0D0, light);
            // wheels
            for (float wx : new float[] {-0.95f, 0.75f}) {
                for (float wz : new float[] {-1.4f, 1.3f}) box(s, p, wx, 0f, wz - 0.32f, wx + 0.2f, 0.5f, wz + 0.32f, 0x101010, light);
            }
            // glowing bits
            VertexConsumer g = buf.getBuffer(RvRenderTypes.glow());
            float pulse = 0.6f + 0.4f * Mth.sin(RvShaders.time() * (e.armed() ? 14f : 3f));
            int flux = e.armed() ? 0xFFFFFF : 0x80C0FF;
            FxDraw.box(g, p, -0.05f, 0.95f, -1.05f, 0.05f, 1.15f, -0.95f, flux, pulse, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            FxDraw.box(g, p, -0.6f, 0.87f, 0.75f, 0.6f, 0.9f, 0.8f, e.armed() ? 0xFF4020 : 0x40FF60, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            FxDraw.box(g, p, -0.8f, 0.55f, 2.19f, -0.5f, 0.65f, 2.22f, 0xFFFFE0, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            FxDraw.box(g, p, 0.5f, 0.55f, 2.19f, 0.8f, 0.65f, 2.22f, 0xFFFFE0, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            if (e.armed() && e.mph() > 60) {
                float k = Mth.clamp((e.mph() - 60) / 28f, 0f, 1f);
                VertexConsumer en = buf.getBuffer(RvRenderTypes.ENERGY);
                net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(0, 6, 0);
                for (int i = 0; i < 6; i++) {
                    float a = RvShaders.time() * 20 + i * 1.1f;
                    FxDraw.beam(en, p, new net.minecraft.world.phys.Vec3(Mth.sin(a) * 1.1, 0.6 + Mth.cos(a * 1.3f) * 0.5, 2.1),
                            new net.minecraft.world.phys.Vec3(Mth.sin(a + 2) * 1.1, 0.6 + Mth.cos(a) * 0.5, -2.1), eye, 0.06f, 0x80D0FF, k);
                }
            }
            pose.popPose();
            super.render(e, yaw, partial, pose, buf, light);
        }

        @Override
        public ResourceLocation getTextureLocation(DeLoreanEntity e) {
            return WHITE;
        }
    }

    /** The blue police box; translucent and flickering while it (de)materialises. Consoles render as a glowing column desk. */
    public static final class Tardis extends EntityRenderer<TardisEntity> {
        public Tardis(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.8f;
        }

        @Override
        public void render(TardisEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            PoseStack.Pose p = pose.last();
            float t = RvShaders.time();
            if (e.console()) {
                VertexConsumer s = buf.getBuffer(RvRenderTypes.solid());
                for (int i = 0; i < 6; i++) {
                    pose.pushPose();
                    pose.mulPose(Axis.YP.rotationDegrees(i * 60));
                    box(s, pose.last(), -0.45f, 0f, 0.6f, 0.45f, 0.95f, 1.4f, i % 2 == 0 ? 0x7A5030 : 0x8A6A40, light);
                    box(s, pose.last(), -0.4f, 0.95f, 0.65f, 0.4f, 1.0f, 1.35f, 0x404850, light);
                    pose.popPose();
                }
                VertexConsumer g = buf.getBuffer(RvRenderTypes.glow());
                float bob = e.fade() > 0 ? Mth.sin(t * 4f) * 0.6f : 0f;
                FxDraw.box(g, p, -0.25f, 1.0f + bob * 0.3f, -0.25f, 0.25f, 3.2f + bob, 0.25f, 0x60C0FF, 0.6f + 0.4f * Mth.sin(t * 3f), FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
                return;
            }
            float alpha = 1f;
            if (e.fade() > 0) {
                int f = e.fade();
                float phase = f > 60 ? (f - 60) / 60f : 1f - f / 60f;  // out: 1->0, in: 0->1
                alpha = Mth.clamp(phase * (0.6f + 0.4f * Mth.sin(t * 6f)), 0f, 1f);
                if (alpha < 0.03f) {
                    pose.popPose();
                    return;
                }
            }
            VertexConsumer s = buf.getBuffer(alpha < 1f ? RvRenderTypes.translucent() : RvRenderTypes.solid());
            int blue = 0x1A3A8A, dark = 0x10265A, white = 0xE8F0FF;
            FxDraw.box(s, p, -0.7f, 0f, -0.7f, 0.7f, 0.12f, 0.7f, dark, alpha, light, OverlayTexture.NO_OVERLAY);
            FxDraw.box(s, p, -0.62f, 0.12f, -0.62f, 0.62f, 2.45f, 0.62f, blue, alpha, light, OverlayTexture.NO_OVERLAY);
            for (float c : new float[] {-0.66f, 0.56f}) {
                for (float d : new float[] {-0.66f, 0.56f}) FxDraw.box(s, p, c, 0.12f, d, c + 0.1f, 2.5f, d + 0.1f, dark, alpha, light, OverlayTexture.NO_OVERLAY);
            }
            FxDraw.box(s, p, -0.68f, 2.3f, -0.68f, 0.68f, 2.5f, 0.68f, dark, alpha, light, OverlayTexture.NO_OVERLAY);
            FxDraw.box(s, p, -0.58f, 2.5f, -0.58f, 0.58f, 2.6f, 0.58f, blue, alpha, light, OverlayTexture.NO_OVERLAY);
            // door panels and windows on all four faces
            for (int side = 0; side < 4; side++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(side * 90));
                PoseStack.Pose q = pose.last();
                for (int row = 0; row < 3; row++) {
                    float y = 0.3f + row * 0.55f;
                    FxDraw.box(s, q, -0.5f, y, 0.62f, -0.06f, y + 0.45f, 0.65f, dark, alpha, light, OverlayTexture.NO_OVERLAY);
                    FxDraw.box(s, q, 0.06f, y, 0.62f, 0.5f, y + 0.45f, 0.65f, dark, alpha, light, OverlayTexture.NO_OVERLAY);
                }
                FxDraw.box(s, q, -0.5f, 2.32f, 0.68f, 0.5f, 2.46f, 0.7f, 0x101010, alpha, light, OverlayTexture.NO_OVERLAY);
                VertexConsumer g = buf.getBuffer(RvRenderTypes.glow());
                FxDraw.box(g, q, -0.48f, 1.92f, 0.655f, -0.06f, 2.2f, 0.67f, white, alpha * 0.8f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                FxDraw.box(g, q, 0.06f, 1.92f, 0.655f, 0.48f, 2.2f, 0.67f, white, alpha * 0.8f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                FxDraw.box(g, q, -0.45f, 2.35f, 0.705f, 0.45f, 2.43f, 0.71f, 0xFFFFFF, alpha, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            VertexConsumer g = buf.getBuffer(RvRenderTypes.glow());
            float lamp = e.fade() > 0 ? 0.5f + 0.5f * Mth.sin(t * 8f) : 0.7f;
            FxDraw.box(g, p, -0.1f, 2.6f, -0.1f, 0.1f, 2.85f, 0.1f, 0xE0F0FF, lamp * alpha, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(e, yaw, partial, pose, buf, light);
        }

        @Override
        public ResourceLocation getTextureLocation(TardisEntity e) {
            return WHITE;
        }
    }
}

package dev.infinitygauntlet.client.time;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a translucent, emissive copy of a living entity's real model in a past pose: the Time Stone's afterimages,
 * echoes and rewind ghosts. Works for any entity rendered by a {@link LivingEntityRenderer}, players included.
 */
public final class Afterimages {
    /**
     * @param source the entity whose model and texture are used
     * @param dx     position relative to the current pose origin
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void draw(PoseStack pose, MultiBufferSource buffers, LivingEntity source, double dx, double dy, double dz, float bodyRot,
                            float headRot, float xRot, float limbPos, float limbSpeed, float age, float alpha, int rgb, float inflate) {
        if (alpha <= 0.01f) {
            return;
        }
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(source);
        if (!(renderer instanceof LivingEntityRenderer lr)) {
            return;
        }
        EntityModel model = lr.getModel();
        ResourceLocation texture = lr.getTextureLocation(source);
        pose.pushPose();
        pose.translate(dx, dy, dz);
        float scale = source.getScale() * inflate;
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyRot));
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        model.attackTime = 0;
        model.riding = false;
        model.young = source.isBaby();
        model.prepareMobModel(source, limbPos, limbSpeed, 0f);
        model.setupAnim(source, limbPos, limbSpeed, age, Mth.wrapDegrees(headRot - bodyRot), xRot);
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color((int) (a * 0.55f), rgb));
        // additive glow pass on top: makes the ghost read as energy rather than a tinted mob
        int glow = FastColor.ARGB32.color(255, (int) ((rgb >> 16 & 255) * alpha * 0.6f), (int) ((rgb >> 8 & 255) * alpha * 0.6f),
                (int) ((rgb & 255) * alpha * 0.6f));
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(texture)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow);
        pose.popPose();
    }

    /** Draws a ghost of {@code entity} from a recorded frame, positioned relative to the pose origin {@code originX..Z}. */
    public static void drawFrame(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, ClientHistory.Frame f, double originX,
                                 double originY, double originZ, float alpha, int rgb) {
        draw(pose, buffers, entity, f.x() - originX, f.y() - originY, f.z() - originZ, f.bodyRot(), f.headRot(), f.xRot(), f.limbPos(),
                f.limbSpeed(), f.age(), alpha, rgb, 1f);
    }

    private Afterimages() {
    }
}

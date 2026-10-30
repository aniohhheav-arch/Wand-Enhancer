package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.TimeFieldEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Time Slow: a turning clock dial on the ground with a gear rim and a faint rising wall of slowed air.
 * Temporal Bubble: a green lens sphere wrapped in tilted gear rings, with clock dials orbiting its equator.
 */
public class TimeFieldRenderer extends EffectEntityRenderer<TimeFieldEntity> {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float R = 0.12f, G = 0.86f, B = 0.35f;
    private static final float HR = 0.7f, HG = 1f, HB = 0.78f;

    public TimeFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(TimeFieldEntity field, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float size = field.size(pt);
        if (size <= 0.01f) {
            return;
        }
        float t = field.tickCount + pt;
        float r = field.visualRadius() * size;
        if (field.shape() == TimeFieldEntity.SHAPE_SPHERE) {
            drawBubble(field, pose, buffers, camLocal, r, t);
        } else {
            drawDisc(pose, buffers, r, t);
        }
    }

    private void drawDisc(PoseStack pose, MultiBufferSource buffers, float r, float t) {
        pose.pushPose();
        pose.translate(0, 0.04, 0);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(t * 0.01f));
        VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME)), X, Z, r * 2.05f, R, G, B, 0.8f);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, 0.01, 0);
        pose.mulPose(Axis.YP.rotation(-t * 0.025f));
        VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME)), X, Z, r * 1.1f, HR, HG, HB, 0.55f);
        pose.popPose();
        VertexConsumer gear = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING));
        VfxDraw.ring(pose.last(), gear, X, Z, r * 0.98f, r * 1.12f, 72, t * 0.004f, R, G, B, 0.9f);
        // wall of slowed air: two counter-scrolling translucent cylinders
        VertexConsumer wall = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        VfxDraw.cylinder(pose.last(), wall, r, (float) TimeFieldEntity.DISC_HEIGHT, 48, t * 0.003f, R, G, B, 0.35f, 0f);
        VfxDraw.cylinder(pose.last(), wall, r * 1.01f, (float) TimeFieldEntity.DISC_HEIGHT * 0.6f, 48, -t * 0.005f, HR, HG, HB, 0.25f, 0f);
        pose.popPose();
    }

    private void drawBubble(TimeFieldEntity field, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal, float r, float t) {
        Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
        VertexConsumer lens = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE));
        VfxDraw.sphere(pose.last(), lens, camLocal, r, 18, 32, 0, 0.04f, 0.2f, 0.09f, 0.32f, 0.07f);
        VertexConsumer rim = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE));
        VfxDraw.sphere(pose.last(), rim, camLocal, r * 1.01f, 18, 32, t * 0.004f, R, G, B, 0.55f, 0.03f);
        VertexConsumer gear = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GEAR_RING));
        for (int k = 0; k < 3; k++) {
            Vec3 axis = VfxDraw.rotate(new Vec3(0, 1, 0), new Vec3(1, 0, 0), 0.35 + k * 0.6);
            axis = VfxDraw.rotate(axis, new Vec3(0, 1, 0), t * 0.006 * (k % 2 == 0 ? 1 : -1) + k * 2.1);
            Vec3 ax = VfxDraw.perpendicular(axis);
            Vec3 ay = axis.cross(ax).normalize();
            VfxDraw.ring(pose.last(), gear, ax, ay, r * 1.02f, r * 1.1f, 72, t * 0.003f * (k + 1), k == 1 ? HR : R, k == 1 ? HG : G, k == 1 ? HB : B, 0.75f);
        }
        // clock dials orbiting the equator, each facing outward
        VertexConsumer dial = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_TIME));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + t * 0.006;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            pose.pushPose();
            pose.translate(out.x * r * 1.06, Math.sin(t * 0.02 + k) * r * 0.08, out.z * r * 1.06);
            VfxDraw.plane(pose.last(), dial, new Vec3(-out.z, 0, out.x), new Vec3(0, 1, 0), r * 0.42f, R, G, B, 0.7f);
            pose.popPose();
        }
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, r * 1.4f, 0, R, G, B, 0.12f);
    }
}

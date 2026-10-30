package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.SpatialRiftEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A whirlpool of distorted space torn into the ground: dark hole, counter-rotating swirls, spiralling smoke, arcs. */
public class SpatialRiftRenderer extends EffectEntityRenderer<SpatialRiftEntity> {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    public SpatialRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(SpatialRiftEntity rift, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = rift.tickCount + pt;
        float form = VfxDraw.smooth(t / SpatialRiftEntity.FORM_TICKS);
        float collapse = VfxDraw.smooth(rift.collapse(pt));
        float r = rift.visualRadius() * form * (1 - collapse * 0.9f);
        if (r <= 0.01f) {
            return;
        }
        long seed = rift.getId() * 4099L;
        Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
        pose.pushPose();
        pose.translate(0, 0.06, 0);

        // the hole: darkens the ground like a pit into somewhere else
        VertexConsumer dark = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.GLOW));
        VfxDraw.plane(pose.last(), dark, X, Z, r * 2.8f, 0.01f, 0.02f, 0.05f, 0.85f);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(t * 0.08f));
        VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
        VfxDraw.plane(pose.last(), swirl, X, Z, r * 2.3f, 0.16f, 0.43f, 1f, 0.85f);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, 0.01, 0);
        pose.mulPose(Axis.YP.rotation(-t * 0.13f));
        swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
        VfxDraw.plane(pose.last(), swirl, X, Z, r * 1.4f, 0.5f, 0.85f, 1f, 0.8f);
        pose.popPose();

        VertexConsumer ring = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        VfxDraw.ring(pose.last(), ring, X, Z, r * 0.9f, r * 1.15f, 48, t * 0.02f, 0.16f, 0.43f, 1f, 0.6f);
        VfxDraw.ring(pose.last(), ring, X, Z, r * 1.8f * (1 - (t * 0.03f) % 1f), r * 2.0f * (1 - (t * 0.03f) % 1f), 48, 0, 0.62f, 0.9f, 1f,
                0.4f * ((t * 0.03f) % 1f));

        // spiral of smoke being dragged in and down
        VertexConsumer smoke = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SMOKE_ATLAS));
        int puffs = 18;
        for (int i = 0; i < puffs; i++) {
            float life = (t * 0.015f + VfxDraw.hash(seed, i, 1)) % 1f;
            float ang = i * 2.4f + life * 5f;
            float d = r * 1.4f * (1 - life);
            float h = 0.15f + (1 - life) * 0.5f;
            VfxDraw.smokePuff(pose.last(), smoke, camRot, (float) Math.cos(ang) * d, h, (float) Math.sin(ang) * d, r * 0.55f * (1 - life * 0.5f),
                    ang, i & 3, 0.06f, 0.08f, 0.14f, 0.7f * life * (1 - life) * 4 * (1 - collapse));
        }

        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE));
        VfxDraw.billboard(pose.last(), glow, camRot, 0, 0.2f, 0, r * 0.7f * (1 + 0.15f * (float) Math.sin(t * 0.7)), 0, 0.62f, 0.9f, 1f, 0.9f);

        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        for (int k = 0; k < 4; k++) {
            int bucket = (int) ((t + k) / 3);
            if (VfxDraw.hash(seed, k, bucket) < 0.4f) {
                continue;
            }
            double a = VfxDraw.hash(seed, k, bucket + 1) * Math.PI * 2;
            Vec3 to = new Vec3(Math.cos(a) * r * 1.3, 0.05, Math.sin(a) * r * 1.3);
            VfxDraw.lightning(pose.last(), lines, new Vec3(0, 0.1, 0), to, camLocal, seed + bucket * 31L + k, 0.5f, 0.04f, 0.5f, 0.8f, 1f, 0.9f);
        }
        pose.popPose();
    }
}

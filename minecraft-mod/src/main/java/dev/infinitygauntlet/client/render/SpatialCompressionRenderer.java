package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.SpatialCompressionEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A shrinking, brightening sphere wrapped in nested lens shells that thicken toward the core, so the area around it
 * reads as space bending inward; radial streaks converge on it.
 */
public class SpatialCompressionRenderer extends EffectEntityRenderer<SpatialCompressionEntity> {
    public SpatialCompressionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(SpatialCompressionEntity sphere, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = sphere.tickCount + pt;
        float life = sphere.life(pt);
        float sr = sphere.sphereRadius(pt);
        float bright = 0.6f + 1.6f * life;
        Quaternionf camRot = entityRenderDispatcher.cameraOrientation();

        VertexConsumer lens = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE));
        float reach = sphere.visualRadius() * 3.2f;
        for (int k = 0; k < 3; k++) {
            float rr = sr + (reach - sr) * (0.25f + k * 0.3f) * (1 - life * 0.4f);
            VfxDraw.sphere(pose.last(), lens, camLocal, rr, 12, 20, 0, 0.05f, 0.12f, 0.3f, 0.2f - k * 0.05f, 0.0f);
        }
        VertexConsumer crackle = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CRACKLE));
        VfxDraw.sphere(pose.last(), crackle, camLocal, sr * 1.05f, 14, 24, t * 0.05f, 0.3f, 0.6f, 1f, 0.9f, 0.4f * bright);

        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, sr * 7f, 0, 0.16f, 0.43f, 1f, 0.45f * bright);
        VfxDraw.billboard(pose.last(), glow, camRot, 0, 0, 0, sr * 3.5f, 0, 0.4f, 0.75f, 1f, 0.5f * bright);
        VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
        VfxDraw.billboard(pose.last(), swirl, camRot, 0, 0, 0, sr * 2.4f, t * 0.12f * (1 + life * 3), 0.4f, 0.75f, 1f, 0.8f);
        VfxDraw.billboard(pose.last(), swirl, camRot, 0, 0, 0, sr * 1.6f, -t * 0.2f * (1 + life * 3), 0.62f, 0.9f, 1f, 0.8f);
        VertexConsumer core = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE));
        VfxDraw.billboard(pose.last(), core, camRot, 0, 0, 0, sr * 1.4f, 0, 0.85f, 0.97f, 1f, Math.min(1f, bright));

        // arcs crawling over the surface
        VertexConsumer arcs = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        long arcSeed = sphere.getId() * 131L + (long) (t / 2);
        for (int k = 0; k < 4; k++) {
            Vec3 a = VfxDraw.unit(arcSeed, k).scale(sr * 1.05);
            Vec3 b = VfxDraw.rotate(a, VfxDraw.unit(arcSeed, k + 20), 1.2);
            VfxDraw.lightning(pose.last(), arcs, a, b, camLocal, arcSeed + k, 0.5f, 0.03f, 0.55f, 0.85f, 1f, 0.9f);
        }

        // converging streaks
        VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
        long seed = sphere.getId() * 7L;
        for (int i = 0; i < 24; i++) {
            float phase = (t * 0.05f + VfxDraw.hash(seed, i, 1)) % 1f;
            Vec3 dir = VfxDraw.unit(seed, i);
            double outer = reach * 1.3 * (1 - phase);
            if (outer < sr) {
                continue;
            }
            Vec3 a = dir.scale(outer);
            Vec3 b = dir.scale(Math.max(sr, outer - reach * 0.35));
            VfxDraw.line(pose.last(), lines, a, b, camLocal, 0.04f, 0.4f, 0.75f, 1f, 0.6f * phase);
        }
    }
}

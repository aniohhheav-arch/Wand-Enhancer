package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.SpatialPrisonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Six portal rings spinning on different axes around the captive; strain makes them wobble, brighten and crackle. */
public class SpatialPrisonRenderer extends EffectEntityRenderer<SpatialPrisonEntity> {
    private static final Vec3[] AXES = {
            new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
            new Vec3(1, 1, 0).normalize(), new Vec3(0, 1, 1).normalize(), new Vec3(1, 0, -1).normalize()
    };

    public SpatialPrisonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void draw(SpatialPrisonEntity prison, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
        float t = prison.tickCount + pt;
        float appear = VfxDraw.smooth(t / 12f);
        float vanish = 1 - VfxDraw.smooth((t - (prison.maxLife() - 10)) / 10f);
        float scale = appear * (0.3f + 0.7f * vanish);
        float strain = prison.strain();
        float r = prison.visualRadius() * scale;
        if (r <= 0.01f) {
            return;
        }

        VertexConsumer lens = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE));
        VfxDraw.sphere(pose.last(), lens, camLocal, r * 0.95f, 12, 20, 0, 0.04f, 0.08f, 0.18f, 0.3f, 0.06f);

        VertexConsumer ring = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        for (int k = 0; k < 6; k++) {
            float speed = (0.03f + k * 0.011f) * (k % 2 == 0 ? 1 : -1) * (1 + strain * 3);
            Vec3 spin = VfxDraw.rotate(AXES[k], AXES[(k + 2) % 6], t * speed);
            Vec3 wobble = new Vec3(Math.sin(t * 1.3 + k), Math.cos(t * 1.7 + k), Math.sin(t * 2.1 + k * 2)).scale(strain * 0.25);
            Vec3 axis = spin.add(wobble).normalize();
            Vec3 ax = VfxDraw.perpendicular(axis);
            Vec3 ay = axis.cross(ax).normalize();
            float rr = r * (1.0f + k * 0.06f);
            float w = r * 0.07f;
            float white = Math.min(1, strain * 1.2f);
            VfxDraw.ring(pose.last(), ring, ax, ay, rr - w, rr + w, 48, t * speed * 6, 0.16f + 0.8f * white, 0.43f + 0.5f * white, 1f, 0.85f * vanish);
        }

        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        VfxDraw.billboard(pose.last(), glow, entityRenderDispatcher.cameraOrientation(), 0, 0, 0, r * 2.8f, 0, 0.16f, 0.43f, 1f, 0.25f + 0.3f * strain);

        if (strain > 0.15f) {
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            long seed = prison.getId() * 131L + (long) (t / 2);
            for (int k = 0; k < 3; k++) {
                Vec3 a = VfxDraw.unit(seed, k).scale(r);
                Vec3 b = VfxDraw.unit(seed, k + 10).scale(r);
                VfxDraw.lightning(pose.last(), lines, a, b, camLocal, seed + k, 0.5f, 0.04f, 0.6f, 0.9f, 1f, strain);
            }
        }
    }
}

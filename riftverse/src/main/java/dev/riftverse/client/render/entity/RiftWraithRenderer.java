package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.RiftWraithEntity;
import dev.riftverse.util.Hash;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Hooded spectre torn half out of reality. Its robe dissolves into rift-light tendrils, its hands gather a void orb
 * while charging, and it flickers out of phase when struck.
 */
public class RiftWraithRenderer extends ProceduralRenderer<RiftWraithEntity> {
    private static final int RING = 9;
    private static final int ROBE_TOP = 0x2A1450;
    private static final int ROBE_BOTTOM = 0x120826;
    private static final int RIFT = 0xFF2BD6;

    public RiftWraithRenderer(EntityRendererProvider.Context context) {
        super(context, 0f);
    }

    @Override
    protected void draw(RiftWraithEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        int frame = (int) age;
        boolean phasing = entity.hurtTime > 0;
        float hover = (float) Math.sin(age * 0.09f + entity.getId()) * 0.12f;
        ps.pushPose();
        if (phasing) {
            ps.translate((Hash.unit(entity.getId(), frame, 1) - 0.5f) * 0.3f, 0, (Hash.unit(entity.getId(), frame, 2) - 0.5f) * 0.3f);
        }
        ps.translate(0, hover, 0);
        PoseStack.Pose pose = ps.last();
        float robeAlpha = phasing ? 0.35f + 0.3f * Hash.unit(entity.getId(), frame, 3) : 0.92f;

        // robe: a flared loft whose hem ripples
        Vector3f[] spine = new Vector3f[RING];
        float[] rw = new float[RING];
        float[] rh = new float[RING];
        int[] top = new int[RING];
        int[] belly = new int[RING];
        for (int i = 0; i < RING; i++) {
            float t = i / (float) (RING - 1);
            float ripple = (float) Math.sin(age * 0.15f + t * 5f) * 0.08f * t;
            spine[i] = new Vector3f(ripple, 1.95f - t * 1.65f, -t * 0.25f + ripple);
            rw[i] = 0.18f + t * 0.3f + (float) Math.sin(age * 0.2f + t * 7f) * 0.03f * t;
            rh[i] = rw[i] * 0.8f;
            top[i] = ROBE_TOP;
            belly[i] = ROBE_BOTTOM;
        }
        FxDraw.loft(buffers.getBuffer(RvRenderTypes.translucent()), pose, spine, rw, rh, top, belly, 12, robeAlpha, light, overlay);

        // hood with a dark void face and burning eyes
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.translucent()), pose, 0, 2.05f, -0.02f, 0.25f, 0.28f, 0.26f, 12, ROBE_TOP, ROBE_BOTTOM, robeAlpha, light, overlay, -2f);
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, 2.0f, 0.1f, 0.17f, 0.2f, 0.16f, 10, 0x050208, 0x000000, 1f, light, overlay, -2f);
        for (int s = -1; s <= 1; s += 2) {
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), pose, s * 0.07f, 2.03f, 0.26f, 0.045f, 0.015f, 0.01f, RIFT, 1f, FxDraw.FULL_BRIGHT, overlay);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, s * 0.07f, 2.03f, 0.28f, 0.14f, RIFT, 0.8f);
        }

        // sleeves reaching forward; the charge gathers a void orb between the hands
        boolean charging = entity.isCharging();
        float reach = charging ? 1f : 0.35f + 0.1f * (float) Math.sin(age * 0.07f);
        Vec3 eye = cameraLocal(pose);
        Vector3f orb = new Vector3f(0, 1.6f, 0.55f + reach * 0.15f);
        for (int s = -1; s <= 1; s += 2) {
            Vector3f shoulder = new Vector3f(s * 0.22f, 1.85f, 0);
            Vector3f hand = new Vector3f(s * (0.32f - reach * 0.18f), 1.55f + reach * 0.05f, 0.25f + reach * 0.4f);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.translucent()), pose, shoulder, hand, 0.08f, 0.12f, ROBE_TOP, ROBE_BOTTOM, robeAlpha, light, overlay);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, hand.x, hand.y, hand.z + 0.05f, charging ? 0.25f : 0.12f, RIFT, 0.8f);
            if (charging) {
                FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(hand.x, hand.y, hand.z), new Vec3(orb.x, orb.y, orb.z), eye, 0.05f, RIFT, 0.8f);
            }
        }
        if (charging) {
            float swell = 0.3f + 0.08f * (float) Math.sin(age * 0.9f);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, orb.x, orb.y, orb.z, swell * 2f, RIFT, 0.9f);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, orb.x, orb.y, orb.z, swell * 0.6f, 0xFFFFFF, 1f);
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, orb, new Vector3f((float) Math.sin(age * 0.2f), 1, (float) Math.cos(age * 0.2f)), swell * 1.2f, 0.05f, 0x8F6BFF, 0.8f, 24);
        }

        // rift-light tendrils trailing from the hem
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6 + entity.getId();
            Vec3[] pts = new Vec3[6];
            for (int i = 0; i < pts.length; i++) {
                float t = i / (float) (pts.length - 1);
                double r = 0.4 + t * 0.15;
                double wave = Math.sin(age * 0.18 + k * 1.3 + t * 4) * 0.15 * t;
                pts[i] = new Vec3(Math.cos(a) * r + wave, 0.35 - t * 0.7, Math.sin(a) * r - 0.2 - t * 0.35);
            }
            FxDraw.ribbon(buffers.getBuffer(RvRenderTypes.ENERGY), pose, pts, eye, 0.08f, RIFT, 0x6A2BFF, 0.7f);
        }
        if (phasing) {
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, 1.2f, 0, 1.6f, 0x6A2BFF, 0.4f);
        }
        ps.popPose();
    }
}

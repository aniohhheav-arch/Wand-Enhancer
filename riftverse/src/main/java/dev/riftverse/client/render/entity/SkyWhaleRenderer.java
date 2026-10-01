package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.SkyWhaleEntity;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A gentle giant that swims through the air: lofted undulating body, wide flukes, bioluminescent flanks. */
public class SkyWhaleRenderer extends ProceduralRenderer<SkyWhaleEntity> {
    private static final int SPINE = 14;
    private static final float CENTER_Y = 1.5f;

    public SkyWhaleRenderer(EntityRendererProvider.Context context) {
        super(context, 2.2f);
    }

    @Override
    protected boolean pitches() {
        return true;
    }

    @Override
    protected void draw(SkyWhaleEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        float swim = age * 0.07f + entity.getId();
        Vector3f[] spine = new Vector3f[SPINE];
        float[] rw = new float[SPINE];
        float[] rh = new float[SPINE];
        int[] top = new int[SPINE];
        int[] belly = new int[SPINE];
        for (int i = 0; i < SPINE; i++) {
            float t = i / (float) (SPINE - 1);
            float wave = (float) Math.sin(swim - t * 3.4f) * 0.6f * t * t;
            spine[i] = new Vector3f(0, CENTER_Y + wave, 4.4f - t * 9.8f);
            float r = 1.55f * (float) Math.pow(Math.sin(Math.PI * Math.pow(t, 0.72)), 0.7) + 0.06f;
            rw[i] = r;
            rh[i] = r * 0.82f;
            top[i] = ColorUtil.lerp(0x27407E, 0x4A2E7C, t);
            belly[i] = ColorUtil.lerp(0xDDE8FF, 0xB9C7F0, t);
        }
        FxDraw.loft(buffers.getBuffer(RvRenderTypes.solid()), pose, spine, rw, rh, top, belly, 16, 1f, light, overlay);

        // flukes follow the tail's undulation
        Vector3f tail = spine[SPINE - 1];
        float tilt = (float) Math.cos(swim - 3.4f) * 0.7f;
        Vector3f root = new Vector3f(0, tail.y, tail.z + 0.5f);
        Vector3f notch = new Vector3f(0, tail.y + tilt * 0.5f, tail.z - 0.5f);
        for (int s = -1; s <= 1; s += 2) {
            Vector3f tip = new Vector3f(s * 2.1f, tail.y + tilt, tail.z - 1.2f);
            Vector3f back = new Vector3f(s * 1.2f, tail.y + tilt * 0.8f, tail.z - 1.5f);
            FxDraw.quad(buffers.getBuffer(RvRenderTypes.solid()), pose, root, tip, back, notch, 0x2E3C78, 1f, light, overlay);
        }
        // pectoral fins
        int fin = (int) (SPINE * 0.3f);
        float flap = (float) Math.sin(age * 0.06f + entity.getId()) * 0.5f;
        for (int s = -1; s <= 1; s += 2) {
            Vector3f c = spine[fin];
            Vector3f a = new Vector3f(s * rw[fin] * 0.85f, c.y - 0.45f, c.z + 0.5f);
            Vector3f b = new Vector3f(s * rw[fin] * 0.85f, c.y - 0.45f, c.z - 0.6f);
            Vector3f tipBack = new Vector3f(s * (rw[fin] + 2.3f), c.y - 0.9f + flap, c.z - 1.3f);
            Vector3f tipFront = new Vector3f(s * (rw[fin] + 2.1f), c.y - 0.8f + flap, c.z - 0.7f);
            FxDraw.quad(buffers.getBuffer(RvRenderTypes.solid()), pose, a, b, tipBack, tipFront, 0x34508E, 1f, light, overlay);
        }

        // bioluminescence: flank spots, a glowing lateral line and gentle eyes
        float glow = 0.6f + 0.4f * (float) Math.sin(age * 0.09f);
        Vec3 eye = cameraLocal(pose);
        for (int s = -1; s <= 1; s += 2) {
            Vec3[] line = new Vec3[SPINE - 4];
            for (int i = 2; i < SPINE - 2; i++) {
                Vector3f c = spine[i];
                line[i - 2] = new Vec3(s * rw[i] * 0.97f, c.y - rh[i] * 0.15f, c.z);
            }
            FxDraw.ribbon(buffers.getBuffer(RvRenderTypes.ENERGY), pose, line, eye, 0.12f, 0x7FF6FF, 0xB07CFF, 0.55f * glow);
            for (int i = 2; i < SPINE - 3; i++) {
                Vector3f c = spine[i];
                float phase = (float) Math.sin(age * 0.15f - i * 0.7f) * 0.5f + 0.5f;
                FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, s * rw[i] * 0.9f, c.y - rh[i] * 0.55f, c.z, 0.16f + 0.08f * phase, 0x8FF8FF, 0.5f + 0.5f * phase);
            }
            Vector3f c = spine[1];
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, s * rw[1] * 0.92f, c.y + 0.1f, c.z, 0.16f, 0xE0FFFF, 1f);
        }
        // dorsal shimmer
        for (int i = 3; i < SPINE - 3; i += 2) {
            Vector3f c = spine[i];
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, c.y + rh[i] * 0.95f, c.z, 0.12f, 0xC6A8FF, glow * 0.7f);
        }
    }
}

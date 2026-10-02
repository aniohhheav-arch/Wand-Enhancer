package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.render.FxDraw;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.entity.SpectralBeastEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A spectral wolf-lion of soul fire: translucent body, burning mane, a tail of embers and white-hot eyes. */
public class SpectralBeastRenderer extends ProceduralRenderer<SpectralBeastEntity> {
    private static final int BODY = 0xFF7A12;
    private static final int DEEP = 0x8A2A00;

    public SpectralBeastRenderer(EntityRendererProvider.Context context) {
        super(context, 0.6f);
    }

    @Override
    protected void draw(SpectralBeastEntity e, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose pose = ps.last();
        VertexConsumer body = buffers.getBuffer(MaRenderTypes.translucent());
        VertexConsumer glow = buffers.getBuffer(MaRenderTypes.glow());
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        VertexConsumer energy = buffers.getBuffer(MaRenderTypes.ENERGY);
        int full = FxDraw.FULL_BRIGHT;
        float walk = e.walkAnimation.position(partial) * 0.9f;
        float amt = Math.min(1f, e.walkAnimation.speed(partial) * 1.6f);
        float fade = Math.min(1f, e.life() / 20f);
        float breathe = (float) Math.sin(age * 0.12f) * 0.03f;
        // torso and chest
        FxDraw.ellipsoid(body, pose, 0, 0.85f + breathe, -0.1f, 0.34f, 0.32f, 0.62f, 12, BODY, DEEP, 0.55f * fade, full, OverlayTexture.NO_OVERLAY, -2f);
        FxDraw.ellipsoid(body, pose, 0, 0.95f + breathe, 0.35f, 0.4f, 0.4f, 0.36f, 12, BODY, DEEP, 0.6f * fade, full, OverlayTexture.NO_OVERLAY, -2f);
        // head with jaw
        float headBob = (float) Math.sin(walk * 2) * 0.03f * amt;
        FxDraw.ellipsoid(body, pose, 0, 1.15f + headBob, 0.82f, 0.24f, 0.22f, 0.28f, 10, BODY, DEEP, 0.7f * fade, full, OverlayTexture.NO_OVERLAY, -2f);
        FxDraw.ellipsoid(body, pose, 0, 1.06f + headBob, 1.08f, 0.12f, 0.1f, 0.16f, 8, BODY, DEEP, 0.7f * fade, full, OverlayTexture.NO_OVERLAY, -2f);
        // eyes
        for (int s = -1; s <= 1; s += 2) {
            FxDraw.cube(glow, pose, s * 0.1f, 1.2f + headBob, 1.02f, 0.035f, 0.025f, 0.02f, 0xFFF4D0, 1f, full, OverlayTexture.NO_OVERLAY);
            FxDraw.billboard(buffers.getBuffer(MaRenderTypes.softGlow()), pose, s * 0.1f, 1.2f + headBob, 1.05f, 0.12f, 0xFFD08A, 0.8f * fade);
        }
        // legs: two-segment limbs swinging in a trot
        float[][] hips = {{-0.2f, 0.4f}, {0.2f, 0.4f}, {-0.2f, -0.5f}, {0.2f, -0.5f}};
        for (int i = 0; i < 4; i++) {
            float phase = walk + (i == 0 || i == 3 ? 0 : (float) Math.PI);
            float swing = (float) Math.sin(phase) * 0.55f * amt;
            float lift = Math.max(0f, (float) Math.cos(phase)) * 0.12f * amt;
            Vector3f hip = new Vector3f(hips[i][0], 0.8f, hips[i][1]);
            Vector3f knee = new Vector3f(hips[i][0] * 1.1f, 0.42f + lift, hips[i][1] + swing * 0.35f);
            Vector3f foot = new Vector3f(hips[i][0] * 1.1f, 0.02f + lift, hips[i][1] + swing * 0.6f);
            FxDraw.limb(body, pose, hip, knee, 0.12f, 0.08f, BODY, DEEP, 0.6f * fade, full, OverlayTexture.NO_OVERLAY);
            FxDraw.limb(body, pose, knee, foot, 0.08f, 0.06f, DEEP, BODY, 0.6f * fade, full, OverlayTexture.NO_OVERLAY);
        }
        // mane of soul fire
        Vec3 eye = cameraLocal(pose);
        for (int i = 0; i < 9; i++) {
            double a = i / 9.0 * Math.PI * 2;
            Vec3 root = new Vec3(Math.cos(a) * 0.28, 1.05 + Math.sin(a) * 0.28, 0.6);
            Vec3 tip = root.add(Math.cos(a) * 0.25, 0.35 + Math.sin(age * 0.3 + i) * 0.08, -0.25);
            FxDraw.beam(energy, pose, root, tip, eye, 0.14f, i % 2 == 0 ? 0xFFB040 : BODY, 0.8f * fade);
        }
        // tail of embers trailing behind
        Vec3[] tail = new Vec3[7];
        for (int i = 0; i < tail.length; i++) {
            double t = i / (double) (tail.length - 1);
            tail[i] = new Vec3(Math.sin(age * 0.2 + t * 3) * 0.15 * t, 0.9 + t * 0.3 + Math.sin(age * 0.15 + t) * 0.1, -0.65 - t * 0.8);
        }
        FxDraw.ribbon(energy, pose, tail, eye, 0.22f, 0xFFD08A, BODY, 0.9f * fade);
        // a soul sigil beneath its paws
        MaDraw.rune(rune, pose, new Vector3f(0, 0.04f, 0), new Vector3f(0, 1, 0), 0.9f, age * 0.03f, MaDraw.SIGIL, e.getId() & 255, BODY, 0.45f * fade);
    }
}

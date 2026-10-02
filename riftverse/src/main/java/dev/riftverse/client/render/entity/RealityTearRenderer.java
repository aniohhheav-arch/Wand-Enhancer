package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.RvShaders;
import dev.riftverse.entity.RealityTearEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** A jagged vertical fracture: a black core seam inside white-hot edges, with violet tendrils clawing outwards. */
public class RealityTearRenderer extends EntityRenderer<RealityTearEntity> {
    public RealityTearRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(RealityTearEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(RealityTearEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = e.openness(partial);
        if (open <= 0.001f) return;
        float size = e.size();
        float t = RvShaders.time();
        Vec3 eye = FxDraw.eyeLocal(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), e.position());
        PoseStack.Pose p = pose.last();
        RandomSource r = RandomSource.create(e.getId() * 31L);
        int segs = 14;
        Vec3[] spine = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            float v = i / (float) segs - 0.5f;
            float jag = (r.nextFloat() - 0.5f) * size * 0.35f + Mth.sin(t * 9f + i * 1.7f) * 0.05f * size;
            spine[i] = new Vec3(jag, v * size * 2.4f * open, (r.nextFloat() - 0.5f) * size * 0.15f);
        }
        float flicker = 0.8f + 0.2f * Mth.sin(t * 23f);
        for (int i = 0; i < segs; i++) {
            float mid = 1f - Math.abs(i / (float) segs - 0.5f) * 2f;
            float w = (0.15f + mid * 0.6f) * size * 0.35f * open;
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), p, spine[i], spine[i + 1], eye, w * 2.6f, 0x8A30FF, 0.45f * flicker);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), p, spine[i], spine[i + 1], eye, w * 1.2f, 0xFFFFFF, 0.9f * flicker);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.VOID_RIBBON), p, spine[i], spine[i + 1], eye, w * 0.7f, 0x000000, 0.95f);
        }
        for (int k = 0; k < 18; k++) {
            Vec3 from = spine[1 + r.nextInt(segs - 1)];
            double ang = r.nextDouble() * Math.PI * 2 + t * (r.nextBoolean() ? 0.6 : -0.6);
            double len = size * (0.4 + r.nextDouble() * 0.9) * open;
            Vec3 a = from.add(Math.cos(ang) * len * 0.5, (r.nextDouble() - 0.5) * len * 0.4, Math.sin(ang) * len * 0.5);
            Vec3 b = a.add(Math.cos(ang + 0.6) * len * 0.5, (r.nextDouble() - 0.5) * len * 0.4, Math.sin(ang + 0.6) * len * 0.5);
            float al = 0.35f + 0.35f * Mth.sin(t * 7f + k);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), p, from, a, eye, 0.08f * size * 0.25f, k % 3 == 0 ? 0xFFFFFF : 0xB050FF, al);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), p, a, b, eye, 0.05f * size * 0.25f, 0x6020C0, al * 0.7f);
        }
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), p, 0, 0, 0, size * 2.2f * open, 0x7020D0, 0.35f * flicker);
    }

    @Override
    public ResourceLocation getTextureLocation(RealityTearEntity e) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}

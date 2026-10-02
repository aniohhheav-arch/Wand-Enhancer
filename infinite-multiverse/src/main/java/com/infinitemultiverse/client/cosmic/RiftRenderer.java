package com.infinitemultiverse.client.cosmic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;

import com.infinitemultiverse.client.cinematic.FxDraw;
import com.infinitemultiverse.cosmic.rift.RiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** A dimensional rift: a jagged black seam rimmed with violet light, crackling arcs and a swirling halo. */
public final class RiftRenderer extends EntityRenderer<RiftEntity> {
    public RiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RiftEntity rift, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 at = rift.getPosition(pt);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 look = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
        float time = rift.tickCount + pt;
        float open = Mth.clamp(time / 25f, 0, 1) * Mth.clamp((rift.lifetime() - time) / 25f, 0, 1) * (1 - rift.hits() * 0.2f);
        if (open <= 0) {
            return;
        }
        Vec3 core = at.add(0, 1.5, 0);
        Vec3 side = cam.subtract(core).cross(new Vec3(0, 1, 0)).normalize();
        RandomSource r = RandomSource.create(rift.getUUID().getLeastSignificantBits());
        int n = 14;
        Vec3[] spine = new Vec3[n + 1];
        double[] width = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            spine[i] = core.add(0, (t - 0.5) * 3.0 * open, 0).add(side.scale((r.nextDouble() - 0.5) * 0.5 * Math.sin(Math.PI * t)));
            width[i] = Math.sin(Math.PI * t) * (0.35 + 0.1 * Math.sin(time * 0.3 + i)) * open;
        }
        FxDraw ink = new FxDraw(pose, buffers.getBuffer(RenderType.debugQuads()), true, at, look);
        ink.ink();
        for (int i = 0; i < n; i++) {
            ink.quad(spine[i].subtract(side.scale(width[i])), spine[i].add(side.scale(width[i])), spine[i + 1].add(side.scale(width[i + 1])),
                    spine[i + 1].subtract(side.scale(width[i + 1])), argb(0x000000, 0.97f));
        }
        FxDraw glow = new FxDraw(pose, buffers.getBuffer(RenderType.lightning()), false, at, look);
        glow.glow();
        for (int i = 0; i < n; i++) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 a = spine[i].add(side.scale(width[i] * s)), b = spine[i + 1].add(side.scale(width[i + 1] * s));
                Vec3 ao = spine[i].add(side.scale((width[i] + 0.25) * s)), bo = spine[i + 1].add(side.scale((width[i + 1] + 0.25) * s));
                glow.quad(a, ao, bo, b, argb(0xE0B0FF, open), argb(0xB040FF, 0), argb(0xB040FF, 0), argb(0xE0B0FF, open));
            }
        }
        for (int i = 0; i < 3; i++) {
            Vec3 axis = new Vec3(Math.sin(time * 0.02 + i * 2), 0.3, Math.cos(time * 0.02 + i * 2)).normalize();
            Vec3 u = FxDraw.perp(axis), w = axis.cross(u).normalize();
            for (int k = 0; k < 24; k++) {
                double a0 = Math.PI * 2 * k / 24 + time * 0.05, a1 = Math.PI * 2 * (k + 1) / 24 + time * 0.05;
                glow.line(FxDraw.onCircle(core, u, w, 2.2 * open, a0), FxDraw.onCircle(core, u, w, 2.2 * open, a1), axis, 0.05, 0xB040FF, 0.4f * open);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RiftEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}

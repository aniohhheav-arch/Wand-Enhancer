package com.infinitemultiverse.client.cosmic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;

import com.infinitemultiverse.client.cinematic.FxDraw;
import com.infinitemultiverse.cosmic.portal.PortalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Portal: a glowing oval rim with a swirling event surface (tinted solid until linked). */
public final class PortalRenderer extends EntityRenderer<PortalEntity> {
    public PortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PortalEntity portal, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 at = portal.getPosition(pt);
        Vec3 look = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
        int color = portal.color() == PortalEntity.BLUE ? 0x3DA0FF : 0xFF8A1A;
        Vec3 n = PortalEntity.vec(portal.facing()), u = PortalEntity.vec(portal.up()), r = PortalEntity.right(portal.facing(), portal.up());
        double time = portal.tickCount + pt;
        // Vertices are expressed relative to the entity (the pose is already at the entity), so "camera" = entity position.
        FxDraw ink = new FxDraw(pose, buffers.getBuffer(RenderType.debugQuads()), true, at, look);
        surface(ink.ink(), at, n, u, r, color, portal.linked(), time);
        FxDraw glow = new FxDraw(pose, buffers.getBuffer(RenderType.lightning()), false, at, look);
        rim(glow.glow(), at, n, u, r, color, time);
        swirl(glow, at, n, u, r, color, portal.linked(), time);
    }

    private static Vec3 oval(Vec3 c, Vec3 u, Vec3 r, double s, double a) {
        return c.add(r.scale(Math.cos(a) * PortalEntity.HALF_W * s)).add(u.scale(Math.sin(a) * PortalEntity.HALF_H * s));
    }

    private static void surface(FxDraw d, Vec3 c, Vec3 n, Vec3 u, Vec3 r, int color, boolean linked, double time) {
        int seg = 40;
        int inner = linked ? argb(0x05080F, 0.92f) : argb(color, 0.9f);
        int outer = linked ? argb(FxDraw.lerp(0x05080F, color, 0.5f), 0.92f) : argb(color, 0.9f);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            d.quad(c, c, oval(c, u, r, 1, a0), oval(c, u, r, 1, a1), inner, inner, outer, outer);
        }
    }

    private static void rim(FxDraw d, Vec3 c, Vec3 n, Vec3 u, Vec3 r, int color, double time) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float pulse = 0.75f + 0.25f * (float) Math.sin(time * 0.2 + i * 0.5);
            Vec3 i0 = oval(c, u, r, 0.9, a0), i1 = oval(c, u, r, 0.9, a1), m0 = oval(c, u, r, 1.0, a0), m1 = oval(c, u, r, 1.0, a1);
            Vec3 o0 = oval(c, u, r, 1.18, a0).add(n.scale(0.01)), o1 = oval(c, u, r, 1.18, a1).add(n.scale(0.01));
            d.quad(i0, m0, m1, i1, argb(color, 0), argb(0xFFFFFF, pulse), argb(0xFFFFFF, pulse), argb(color, 0));
            d.quad(m0, o0, o1, m1, argb(color, pulse), argb(color, 0), argb(color, 0), argb(color, pulse));
        }
    }

    private static void swirl(FxDraw d, Vec3 c, Vec3 n, Vec3 u, Vec3 r, int color, boolean linked, double time) {
        int arms = 5;
        for (int k = 0; k < arms; k++) {
            Vec3 prev = null;
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                double a = k * Math.PI * 2 / arms + t * 3 + time * (linked ? 0.08 : 0.03);
                Vec3 p = oval(c, u, r, 0.85 * (1 - t), a).add(n.scale(0.015));
                if (prev != null) {
                    d.line(prev, p, n, 0.05, color, (float) (0.5 * (1 - t)));
                }
                prev = p;
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PortalEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public boolean shouldRender(PortalEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }
}

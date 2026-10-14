package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stickyfingers.StickyFingersMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Procedural zipper mesh. A zipper is two fabric tapes with interlocking metal teeth along a line;
 * a slider runs along it and the tapes part behind the slider into a lens-shaped opening that shows
 * a glowing "zipper space" interior. Everything is drawn from one 64x64 atlas.
 *
 * Atlas layout (pixels): tape 0..16 x 0..8, tooth 16..20 x 0..4, tooth-dark 20..24 x 0..4,
 * slider 24..32 x 0..8, glow 32..48 x 0..4, interior 0..32 x 16..48.
 */
public final class ZipperDraw {
    public static final ResourceLocation TEXTURE = StickyFingersMod.id("textures/entity/zipper.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float PX = 1f / 64f;

    public static RenderType renderType() {
        return RenderType.entityTranslucent(TEXTURE);
    }

    public static RenderType glowType() {
        return RenderType.entityTranslucentEmissive(TEXTURE);
    }

    /**
     * @param c      centre (relative to pose origin)
     * @param n      surface normal (unit)
     * @param d      zipper direction (unit, perpendicular to n)
     * @param open   0 closed .. 1 fully open
     * @param appear 0..1 fade/stitch-in factor
     */
    public static void draw(PoseStack ps, MultiBufferSource buffers, Vec3 c, Vec3 n, Vec3 d, float length, float width,
                            float open, float appear, int light, float alpha, float time) {
        draw(ps, buffers, c, n, d, length, width, open, appear, light, alpha, 1f, time);
    }

    /** @param interior opacity of the zipper-space interior; passages that cut real blocks use a thin veil so you can see through. */
    public static void draw(PoseStack ps, MultiBufferSource buffers, Vec3 c, Vec3 n, Vec3 d, float length, float width,
                            float open, float appear, int light, float alpha, float interior, float time) {
        if (appear <= 0.01f || alpha <= 0.01f) return;
        PoseStack.Pose pose = ps.last();
        Vec3 s = n.cross(d).normalize();
        float L = length * Mth.clamp(appear * 1.15f, 0f, 1f);
        int segs = Mth.clamp((int) (L * 10), 6, 80);
        float tapeW = Math.max(0.06f, Math.min(0.14f, width * 0.12f));
        float half = width * 0.5f;
        float slider = -L / 2 + L * Mth.clamp(open * 1.08f, 0f, 1f);
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);

        // --- opening interior (emissive), drawn first and slightly recessed
        if (open > 0.01f) {
            VertexConsumer glow = buffers.getBuffer(glowType());
            for (int i = 0; i < segs; i++) {
                float t0 = -L / 2 + L * i / segs, t1 = -L / 2 + L * (i + 1) / segs;
                float g0 = gap(t0, L, slider, half, open), g1 = gap(t1, L, slider, half, open);
                if (g0 < 0.002f && g1 < 0.002f) continue;
                Vec3 l0 = at(c, d, s, t0, g0), l1 = at(c, d, s, t1, g1);
                Vec3 r0 = at(c, d, s, t0, -g0), r1 = at(c, d, s, t1, -g1);
                Vec3 back = n.scale(-0.004);
                float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
                float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.15f + i);
                int col = argb((int) (a * interior), shimmer, shimmer, 1f);
                quad(glow, pose, l0.add(back), l1.add(back), r1.add(back), r0.add(back),
                        u(0 + u0 * 32), v(16), u(0 + u1 * 32), v(48), col, FULL_BRIGHT, n);
                // cyan rim light along the parted edges
                int rim = argb((int) (a * Mth.clamp(open * 1.5f, 0, 1)), 0.55f, 0.85f, 1f);
                Vec3 rl0 = at(c, d, s, t0, g0 - 0.025f), rl1 = at(c, d, s, t1, g1 - 0.025f);
                Vec3 rr0 = at(c, d, s, t0, -g0 + 0.025f), rr1 = at(c, d, s, t1, -g1 + 0.025f);
                Vec3 up = n.scale(0.002);
                quad(glow, pose, l0.add(up), l1.add(up), rl1.add(up), rl0.add(up), u(32), v(0), u(48), v(4), rim, FULL_BRIGHT, n);
                quad(glow, pose, rr0.add(up), rr1.add(up), r1.add(up), r0.add(up), u(32), v(0), u(48), v(4), rim, FULL_BRIGHT, n);
            }
        }

        // --- tapes (fetch the lit buffer only after the emissive pass; switching buffers ends a batch)
        VertexConsumer vc = buffers.getBuffer(renderType());
        Vec3 lift = n.scale(0.004);
        for (int i = 0; i < segs; i++) {
            float t0 = -L / 2 + L * i / segs, t1 = -L / 2 + L * (i + 1) / segs;
            float g0 = gap(t0, L, slider, half, open), g1 = gap(t1, L, slider, half, open);
            int col = argb(a, 1f, 1f, 1f);
            float uu0 = (i % 4) * 4, uu1 = uu0 + 4;
            quad(vc, pose, at(c, d, s, t0, g0).add(lift), at(c, d, s, t1, g1).add(lift),
                    at(c, d, s, t1, g1 + tapeW).add(lift), at(c, d, s, t0, g0 + tapeW).add(lift),
                    u(uu0), v(0), u(uu1), v(8), col, light, n);
            quad(vc, pose, at(c, d, s, t0, -g0 - tapeW).add(lift), at(c, d, s, t1, -g1 - tapeW).add(lift),
                    at(c, d, s, t1, -g1).add(lift), at(c, d, s, t0, -g0).add(lift),
                    u(uu0), v(0), u(uu1), v(8), col, light, n);
        }

        // --- interlocking teeth (alternate sides, offset half a pitch)
        float pitch = 0.09f;
        float tw = 0.055f, th = 0.05f;
        Vec3 toothLift = n.scale(0.008);
        int tcol = argb(a, 1f, 1f, 1f);
        for (float t = -L / 2 + pitch * 0.5f; t < L / 2 - 0.02f; t += pitch) {
            for (int side = 0; side < 2; side++) {
                float tt = t + (side == 1 ? pitch * 0.5f : 0);
                if (tt > L / 2) continue;
                float g = gap(tt, L, slider, half, open);
                float sign = side == 0 ? 1f : -1f;
                // closed teeth reach across the seam; opened teeth sit on their tape edge
                float inner = g < 0.01f ? -th * 0.45f : 0f;
                Vec3 base0 = at(c, d, s, tt - tw / 2, sign * (g + inner));
                Vec3 base1 = at(c, d, s, tt + tw / 2, sign * (g + inner));
                Vec3 tip1 = at(c, d, s, tt + tw / 2, sign * (g + inner + th));
                Vec3 tip0 = at(c, d, s, tt - tw / 2, sign * (g + inner + th));
                boolean dark = g > 0.01f;
                float uu = dark ? 20 : 16;
                quad(vc, pose, base0.add(toothLift), base1.add(toothLift), tip1.add(toothLift), tip0.add(toothLift),
                        u(uu), v(0), u(uu + 4), v(4), tcol, light, n);
            }
        }

        // --- slider body + pull tab
        if (L > 0.2f) {
            float st = Mth.clamp(slider, -L / 2 + 0.06f, L / 2 - 0.06f);
            float sl = 0.1f, sw = Math.max(0.09f, tapeW * 1.1f);
            Vec3 sLift = n.scale(0.03);
            Vec3 p0 = at(c, d, s, st - sl, -sw).add(sLift), p1 = at(c, d, s, st + sl * 0.6f, -sw).add(sLift);
            Vec3 p2 = at(c, d, s, st + sl * 0.6f, sw).add(sLift), p3 = at(c, d, s, st - sl, sw).add(sLift);
            quad(vc, pose, p0, p1, p2, p3, u(24), v(0), u(32), v(8), tcol, light, n);
            // sides of the slider for depth
            Vec3 down = n.scale(-0.025);
            quad(vc, pose, p0, p1, p1.add(down), p0.add(down), u(24), v(6), u(32), v(8), tcol, light, s.reverse());
            quad(vc, pose, p3, p2, p2.add(down), p3.add(down), u(24), v(6), u(32), v(8), tcol, light, s);
            // swinging pull tab
            float swing = Mth.sin(time * 0.2f) * 0.02f;
            Vec3 tabBase = at(c, d, s, st - sl, 0).add(n.scale(0.035));
            Vec3 tabDir = d.scale(-0.16).add(s.scale(swing));
            Vec3 ts = s.scale(0.035);
            quad(vc, pose, tabBase.subtract(ts), tabBase.add(ts), tabBase.add(tabDir).add(ts.scale(0.8)), tabBase.add(tabDir).subtract(ts.scale(0.8)),
                    u(24), v(0), u(28), v(8), tcol, light, n);
        }
    }

    /** Lens-shaped gap: fully parted behind the slider, closing towards both ends. */
    private static float gap(float t, float L, float slider, float half, float open) {
        if (open <= 0.001f || t > slider) return 0f;
        float behind = Mth.clamp((slider - t) / Math.max(0.15f, L * 0.25f), 0f, 1f);
        float x = (t + L / 2) / L;
        float lens = (float) Math.pow(Math.max(0, Mth.sin(x * Mth.PI)), 0.55);
        return half * open * lens * smooth(behind);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static Vec3 at(Vec3 c, Vec3 d, Vec3 s, float t, float side) {
        return c.add(d.scale(t)).add(s.scale(side));
    }

    private static float u(float px) { return px * PX; }
    private static float v(float px) { return px * PX; }

    private static int argb(int a, float r, float g, float b) {
        return (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float u0, float v0, float u1, float v1, int color, int light, Vec3 n) {
        vertex(vc, pose, a, u0, v0, color, light, n);
        vertex(vc, pose, b, u1, v0, color, light, n);
        vertex(vc, pose, c, u1, v1, color, light, n);
        vertex(vc, pose, d, u0, v1, color, light, n);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int light, Vec3 n) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    private ZipperDraw() {}
}

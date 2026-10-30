package dev.infinitygauntlet.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import static dev.infinitygauntlet.client.vfx.VfxDraw.hash;
import static dev.infinitygauntlet.client.vfx.VfxDraw.smooth;

/**
 * Draws the Space Stone's dimensional rupture: an unstable sphere of billowing dark smoke around an electric core.
 * <p>
 * Layers, back to front: refraction haze → rear smoke shell → additive core (glow, two counter-rotating swirls, hot
 * centre) → front smoke shell and outward tendrils → lightning arcs → rim light and flashes. The smoke puffs are
 * sorted by the translucent render type; the core is drawn between the two halves so front smoke occludes it while
 * rear smoke does not. Opening and closing are driven by {@code open}/{@code close} in [0, 1] and follow the
 * spark → distortion → rupture → expansion → arcs sequence (and its inverse collapse).
 */
public final class RuptureDraw {
    public record Palette(float[] smokeDark, float[] smokeLit, float[] core, float[] hot, float[] arc) {
        public static Palette of(int color, int highlight) {
            float r = VfxDraw.r(color), g = VfxDraw.g(color), b = VfxDraw.b(color);
            float hr = VfxDraw.r(highlight), hg = VfxDraw.g(highlight), hb = VfxDraw.b(highlight);
            return new Palette(
                    new float[]{0.07f + r * 0.04f, 0.075f + g * 0.04f, 0.10f + b * 0.06f},
                    new float[]{r * 0.45f + 0.04f, g * 0.45f + 0.05f, b * 0.6f + 0.08f},
                    new float[]{r, g, b},
                    new float[]{hr, hg, hb},
                    new float[]{(r + hr) * 0.5f, (g + hg) * 0.5f, (b + hb) * 0.5f});
        }
    }

    public static final Palette SPACE = Palette.of(0x2A6EFF, 0x9FE8FF);

    /**
     * @param camLocal camera position relative to the rupture centre (the current pose origin)
     * @param t        animation time in ticks (with partial tick)
     * @param energy   extra intensity multiplier (gateway / straining prisons)
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 camLocal, float radius, float open, float close, float t, long seed,
                            Palette pal, float energy, boolean gateway) {
        float density = ClientConfig.quality().density;
        Quaternionf camRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vec3 view = camLocal.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : camLocal.normalize();
        PoseStack.Pose p = pose.last();

        float spark = smooth(open / 0.15f);
        float form = smooth((open - 0.12f) / 0.25f);
        float expand = smooth((open - 0.3f) / 0.4f);
        float arcs = smooth((open - 0.6f) / 0.35f);
        float collapse = smooth(close / 0.8f);
        float shrink = smooth((close - 0.55f) / 0.4f);

        float smokeScale = expand * (1 - collapse);
        float coreScale = (0.18f + 0.82f * form) * (1 - shrink);
        float flicker = 0.88f + 0.12f * Mth.sin(t * 1.7f + seed % 13) * Mth.sin(t * 0.63f);
        float coreBright = (0.35f + 0.65f * form) * (1 + 1.6f * smooth(close / 0.6f)) * flicker * energy;
        float smokeAlpha = Math.min(1f, expand * 1.4f) * (1 - collapse * 0.4f) * (1 - shrink);

        // 1. refraction haze: darkens and tints what is behind the rupture, slightly larger than the smoke
        VertexConsumer haze = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.GLOW));
        float hazeSize = radius * 3.6f * Math.max(smokeScale, form * 0.45f);
        VfxDraw.billboard(p, haze, camRot, 0, 0, 0, hazeSize, t * 0.01f, 0.02f, 0.04f, 0.09f, 0.45f * Math.max(smokeAlpha, form * 0.5f));

        int puffs = Math.max(12, (int) (56 * density * (gateway ? 1.6f : 1f)));
        int tendrils = Math.max(4, puffs / 3);

        // 2. rear smoke
        drawSmoke(p, buffers, camRot, view, radius, smokeScale, smokeAlpha, coreBright, t, seed, puffs, tendrils, pal, false, collapse);

        // 3. core
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
        float[] c = pal.core;
        float[] h = pal.hot;
        VfxDraw.billboard(p, glow, camRot, 0, 0, 0, radius * 3.4f * coreScale, 0, c[0], c[1], c[2], 0.42f * coreBright);
        VfxDraw.billboard(p, glow, camRot, 0, 0, 0, radius * 1.6f * coreScale, 0, (c[0] + h[0]) / 2, (c[1] + h[1]) / 2, (c[2] + h[2]) / 2,
                0.5f * coreBright);
        if (open < 0.3f) {
            // the initial spark: a tiny flickering star that births the rupture
            float s = spark * (1 - form) * (0.7f + 0.3f * hash(seed, (int) t, 3));
            VfxDraw.billboard(p, glow, camRot, 0, 0, 0, radius * 0.9f * s, 0, h[0], h[1], h[2], s);
        }
        float pulse = flashPulse(t, seed);
        if (pulse > 0) {
            VfxDraw.billboard(p, glow, camRot, 0, 0, 0, radius * 3.4f * smokeScale, 0, h[0], h[1], h[2], 0.28f * pulse * energy);
        }
        VertexConsumer swirl = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.SWIRL));
        VfxDraw.billboard(p, swirl, camRot, 0, 0, 0, radius * 2.0f * coreScale, t * 0.045f, c[0], c[1], c[2], 0.75f * coreBright);
        VfxDraw.billboard(p, swirl, camRot, 0, 0, 0, radius * 1.35f * coreScale, -t * 0.08f + 1.3f, (c[0] + h[0]) / 2, (c[1] + h[1]) / 2,
                (c[2] + h[2]) / 2, 0.7f * coreBright);
        VertexConsumer core = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.CORE));
        float breathe = 1 + 0.08f * Mth.sin(t * 0.9f);
        VfxDraw.billboard(p, core, camRot, 0, 0, 0, radius * 1.2f * coreScale * breathe, t * 0.02f, h[0], h[1], h[2], 0.85f * coreBright);
        VfxDraw.billboard(p, core, camRot, 0, 0, 0, radius * 0.5f * coreScale * breathe, 0, 1f, 1f, 1f, Math.min(1f, 0.9f * coreBright));

        // 4. front smoke + tendrils
        drawSmoke(p, buffers, camRot, view, radius, smokeScale, smokeAlpha, coreBright, t, seed, puffs, tendrils, pal, true, collapse);

        // 5. rim light: bright fringe around the silhouette, reads as refraction at the edge of the tear
        VertexConsumer rim = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING));
        Vec3 ax = VfxDraw.perpendicular(view);
        Vec3 ay = view.cross(ax).normalize();
        VfxDraw.ring(p, rim, ax, ay, radius * 0.85f * smokeScale, radius * 1.35f * smokeScale, 40, t * 0.004f,
                c[0], c[1], c[2], 0.16f * smokeAlpha * energy);
        if (open < 0.6f) {
            // space rippling outward while the rupture forms
            float q = (open % 0.2f) / 0.2f;
            VfxDraw.ring(p, rim, ax, ay, radius * (0.2f + 2.2f * q), radius * (0.45f + 2.5f * q), 40, 0, h[0], h[1], h[2], 0.35f * (1 - q) * spark);
        }
        if (gateway) {
            drawGatewayRings(p, rim, radius, t, smokeScale, c, h, energy);
        }

        // 6. lightning arcs across the sphere
        float arcAlpha = arcs * (1 - collapse) * energy;
        if (arcAlpha > 0.01f) {
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            int count = (int) ((gateway ? 9 : 4) + 4 * density);
            float[] a = pal.arc;
            for (int k = 0; k < count; k++) {
                int bucket = (int) ((t + k * 1.37f) / 3f);
                if (hash(seed, k, bucket) < 0.35f) {
                    continue; // not every arc fires every bucket: gives the crackling cadence
                }
                long s = seed * 31 + k * 977L + bucket * 7919L;
                Vec3 from = VfxDraw.unit(s, 1).scale(radius * (0.65 + 0.35 * hash(s, 2, 0)) * smokeScale);
                boolean outward = hash(s, 5, 0) > 0.75f;
                Vec3 to = outward
                        ? from.normalize().scale(radius * (1.4 + 0.5 * hash(s, 6, 0)) * smokeScale)
                        : VfxDraw.rotate(from, VfxDraw.unit(s, 3), 0.7 + hash(s, 4, 0) * 1.2).scale(0.9 + 0.2 * hash(s, 7, 0));
                VfxDraw.lightning(p, lines, from, to, camLocal, s, 0.55f, radius * 0.03f, a[0], a[1], a[2], arcAlpha * (0.5f + 0.5f * hash(s, 8, 0)));
            }
        }

        // 7. collapse flash
        if (close > 0.8f) {
            float f = 1 - Math.abs(close - 0.92f) / 0.12f;
            if (f > 0) {
                VertexConsumer flash = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW));
                VfxDraw.billboard(p, flash, camRot, 0, 0, 0, radius * 5f * f, 0, h[0], h[1], h[2], f);
                VfxDraw.billboard(p, flash, camRot, 0, 0, 0, radius * 1.4f * f, 0, 1, 1, 1, f);
            }
        }
    }

    private static void drawSmoke(PoseStack.Pose p, MultiBufferSource buffers, Quaternionf camRot, Vec3 view, float radius, float smokeScale,
                                  float smokeAlpha, float coreBright, float t, long seed, int puffs, int tendrils, Palette pal, boolean front,
                                  float collapse) {
        if (smokeScale <= 0.01f || smokeAlpha <= 0.01f) {
            return;
        }
        float[] dark = pal.smokeDark;
        float[] lit = pal.smokeLit;
        VertexConsumer smoke = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SMOKE_ATLAS));
        // shell puffs: orbit the core, inner ones faster (the swirl), breathing in and out
        for (int i = 0; i < puffs; i++) {
            float h1 = hash(seed, i, 21), h2 = hash(seed, i, 22), h3 = hash(seed, i, 23);
            Vec3 base = fibonacci(i, puffs);
            Vec3 axis = VfxDraw.unit(seed + 5, i);
            float rf = 0.5f + 0.55f * h1;
            float speed = (0.006f + 0.012f * h2) * (rf < 0.75f ? 1.8f : 1f);
            Vec3 dir = VfxDraw.rotate(base, axis, t * speed + h3 * 6.283);
            float breathe = 1 + 0.08f * Mth.sin(t * 0.09f + h3 * 9);
            Vec3 pos = dir.scale(radius * rf * smokeScale * breathe);
            if (dir.dot(view) >= 0 != front) {
                continue;
            }
            float size = radius * (0.85f + 0.6f * h2) * (1.2f - 0.35f * rf) * Math.max(0.35f, smokeScale);
            float glowF = Mth.clamp((1.05f - rf) / 0.5f, 0, 1) * Math.min(1.2f, coreBright) * 0.85f;
            // puffs in front of the core catch the light shining through
            glowF += Math.max(0, (float) dir.dot(view)) * 0.15f * coreBright;
            float r = Mth.lerp(glowF, dark[0], lit[0]), g = Mth.lerp(glowF, dark[1], lit[1]), b = Mth.lerp(glowF, dark[2], lit[2]);
            VfxDraw.smokePuff(p, smoke, camRot, (float) pos.x, (float) pos.y, (float) pos.z, size, t * 0.01f * (h1 - 0.5f) * 4 + h3 * 6,
                    i & 3, r, g, b, 0.82f * smokeAlpha);
        }
        // tendrils: billow outward, grow and fade; during collapse they are dragged back in
        for (int i = 0; i < tendrils; i++) {
            float h1 = hash(seed, i, 31), h2 = hash(seed, i, 32), h3 = hash(seed, i, 33);
            float life = (t * 0.011f * (0.7f + 0.6f * h1) + h3) % 1f;
            if (collapse > 0) {
                life *= 1 - collapse;
            }
            Vec3 dir = VfxDraw.rotate(VfxDraw.unit(seed + 9, i), new Vec3(0, 1, 0), t * 0.004f);
            if (dir.dot(view) >= 0 != front) {
                continue;
            }
            Vec3 pos = dir.scale(radius * (0.85f + 0.8f * life) * smokeScale);
            float size = radius * (0.5f + 0.7f * life) * (0.8f + 0.4f * h2) * smokeScale;
            float alpha = Math.min(1f, life * 5f) * (1 - life) * 0.75f * smokeAlpha;
            VfxDraw.smokePuff(p, smoke, camRot, (float) pos.x, (float) pos.y, (float) pos.z, size, h2 * 6 + t * 0.006f,
                    (i + 1) & 3, dark[0] * 0.8f, dark[1] * 0.8f, dark[2] * 0.85f, alpha);
        }
    }

    private static void drawGatewayRings(PoseStack.Pose p, VertexConsumer vc, float radius, float t, float scale, float[] c, float[] h, float energy) {
        for (int k = 0; k < 3; k++) {
            float speed = (k + 1) * 0.012f * (k % 2 == 0 ? 1 : -1);
            Vec3 axis = VfxDraw.rotate(new Vec3(0, 1, 0), new Vec3(1, 0, 0), 0.25 + k * 0.35);
            axis = VfxDraw.rotate(axis, new Vec3(0, 1, 0), t * speed);
            Vec3 ax = VfxDraw.perpendicular(axis);
            Vec3 ay = axis.cross(ax).normalize();
            float rr = radius * (1.25f + k * 0.22f) * scale;
            VfxDraw.ring(p, vc, ax, ay, rr - radius * 0.06f, rr + radius * 0.06f, 64, t * speed * 4, k == 1 ? h[0] : c[0], k == 1 ? h[1] : c[1],
                    k == 1 ? h[2] : c[2], 0.6f * scale * energy);
        }
    }

    private static Vec3 fibonacci(int i, int n) {
        double y = 1 - (i + 0.5) * 2.0 / n;
        double r = Math.sqrt(1 - y * y);
        double phi = i * 2.399963229728653;
        return new Vec3(Math.cos(phi) * r, y, Math.sin(phi) * r);
    }

    /** Occasional bright flashes that light up the surrounding smoke from inside. */
    private static float flashPulse(float t, long seed) {
        int bucket = (int) (t / 9f);
        if (hash(seed, bucket, 41) < 0.8f) {
            return 0;
        }
        float local = (t / 9f) - bucket;
        return Math.max(0, 1 - local * 2.2f);
    }

    private RuptureDraw() {
    }
}

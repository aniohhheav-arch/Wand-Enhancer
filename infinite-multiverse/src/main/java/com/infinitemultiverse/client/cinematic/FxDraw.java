package com.infinitemultiverse.client.cinematic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Procedural effect geometry drawn in world space. Two passes: {@link #glow} is additive (light, energy, fire),
 * {@link #ink} is alpha-blended (darkness, smoke, shadow). Every primitive is double-sided and uses per-vertex
 * colour fades instead of textures, so soft edges come from interpolated alpha.
 */
public final class FxDraw {
    private final Matrix4f matrix;
    private final VertexConsumer out;
    private final boolean inkPass;
    private final Vec3 cam;
    private final Vec3 camLook;
    private boolean drawing;

    /** Scenes are rendered twice, once per pass; primitives only emit in the pass matching the current mode. */
    public FxDraw(PoseStack pose, VertexConsumer out, boolean inkPass, Vec3 cam, Vec3 camLook) {
        this.matrix = pose.last().pose();
        this.out = out;
        this.inkPass = inkPass;
        this.cam = cam;
        this.camLook = camLook;
        this.drawing = !inkPass;
    }

    /** Switch to additive light. */
    public FxDraw glow() {
        drawing = !inkPass;
        return this;
    }

    /** Switch to alpha-blended darkness. */
    public FxDraw ink() {
        drawing = inkPass;
        return this;
    }

    public boolean isInkPass() {
        return inkPass;
    }

    public Vec3 camera() {
        return cam;
    }

    // ------------------------------------------------------------------ colours

    public static int argb(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ raw

    private void v(Vec3 p, int argb) {
        if (!drawing) {
            return;
        }
        out.addVertex(matrix, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        if (!drawing) {
            return;
        }
        v(a, ca); v(b, cb); v(c, cc); v(d, cd);
        v(d, cd); v(c, cc); v(b, cb); v(a, ca);
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        quad(a, b, c, d, argb, argb, argb, argb);
    }

    public void tri(Vec3 a, Vec3 b, Vec3 c, int ca, int cb, int cc) {
        quad(a, b, c, c, ca, cb, cc, cc);
    }

    // ------------------------------------------------------------------ frames

    public static Vec3 perp(Vec3 n) {
        Vec3 up = Math.abs(n.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return n.cross(up).normalize();
    }

    public static Vec3 onCircle(Vec3 center, Vec3 u, Vec3 w, double r, double angle) {
        return center.add(u.scale(Math.cos(angle) * r)).add(w.scale(Math.sin(angle) * r));
    }

    // ------------------------------------------------------------------ primitives

    /** Soft glowing disc facing the camera: bright core, transparent rim. */
    public void flare(Vec3 c, double r, int rgb, float alpha) {
        Vec3 n = cam.subtract(c).normalize();
        disc(c, n, 0, r, argb(rgb, alpha), argb(rgb, 0), 20);
    }

    /** Filled annulus (r0..r1) around normal n, colour fading from inner to outer. */
    public void disc(Vec3 c, Vec3 n, double r0, double r1, int inner, int outer, int seg) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            quad(onCircle(c, u, w, r0, a0), onCircle(c, u, w, r1, a0), onCircle(c, u, w, r1, a1), onCircle(c, u, w, r0, a1), inner, outer, outer, inner);
        }
    }

    /** Thin ring band with soft both-side falloff, optionally only an arc. */
    public void ring(Vec3 c, Vec3 n, double r, double width, int rgb, float alpha, int seg, double a0, double sweep) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        int core = argb(rgb, alpha), edge = argb(rgb, 0);
        for (int i = 0; i < seg; i++) {
            double t0 = a0 + sweep * i / seg, t1 = a0 + sweep * (i + 1) / seg;
            Vec3 i0 = onCircle(c, u, w, r - width, t0), i1 = onCircle(c, u, w, r - width, t1);
            Vec3 m0 = onCircle(c, u, w, r, t0), m1 = onCircle(c, u, w, r, t1);
            Vec3 o0 = onCircle(c, u, w, r + width, t0), o1 = onCircle(c, u, w, r + width, t1);
            quad(i0, m0, m1, i1, edge, core, core, edge);
            quad(m0, o0, o1, m1, core, edge, edge, core);
        }
    }

    public void ring(Vec3 c, Vec3 n, double r, double width, int rgb, float alpha) {
        ring(c, n, r, width, rgb, alpha, Math.max(24, (int) (r * 10)), 0, Math.PI * 2);
    }

    /** UV sphere. {@code rimBoost} brightens the silhouette (fresnel-like) for energy shells. */
    public void sphere(Vec3 c, double r, int rgb, float alpha, float rimAlpha, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * i / lat - Math.PI / 2, p1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double t0 = Math.PI * 2 * j / lon, t1 = Math.PI * 2 * (j + 1) / lon;
                Vec3 a = sp(c, r, p0, t0), b = sp(c, r, p1, t0), d = sp(c, r, p0, t1), e = sp(c, r, p1, t1);
                quad(a, b, e, d, shade(c, a, rgb, alpha, rimAlpha), shade(c, b, rgb, alpha, rimAlpha), shade(c, e, rgb, alpha, rimAlpha), shade(c, d, rgb, alpha, rimAlpha));
            }
        }
    }

    private int shade(Vec3 c, Vec3 p, int rgb, float alpha, float rim) {
        Vec3 n = p.subtract(c).normalize();
        Vec3 view = cam.subtract(p).normalize();
        float facing = (float) Math.abs(n.dot(view));
        return argb(rgb, Mth.lerp(1 - facing, alpha, rim));
    }

    private static Vec3 sp(Vec3 c, double r, double phi, double theta) {
        return c.add(Math.cos(phi) * Math.cos(theta) * r, Math.sin(phi) * r, Math.cos(phi) * Math.sin(theta) * r);
    }

    /** Glowing orb: hot core, coloured body and a soft halo. */
    public void orb(Vec3 c, double r, int core, int body, float alpha) {
        glow();
        sphere(c, r * 0.55, core, alpha, alpha * 0.6f, 10, 16);
        sphere(c, r, body, alpha * 0.35f, alpha * 0.9f, 12, 20);
        flare(c, r * 2.6, body, alpha * 0.45f);
        flare(c, r * 1.2, core, alpha * 0.5f);
    }

    /** Camera-facing ribbon from a to b (fades along its length from ca to cb). */
    public void ribbon(Vec3 a, Vec3 b, double width, int ca, int cb) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(dir.scale(0.5));
        Vec3 side = dir.cross(cam.subtract(mid)).normalize().scale(width * 0.5);
        if (side.lengthSqr() < 1.0E-8) {
            side = perp(dir.normalize()).scale(width * 0.5);
        }
        int ea = ca & 0x00FFFFFF, eb = cb & 0x00FFFFFF;
        quad(a.subtract(side), a, b, b.subtract(side), ea, ca, cb, eb);
        quad(a, a.add(side), b.add(side), b, ca, ea, eb, cb);
    }

    /** A glowing beam: white-hot core with layered colour sheaths. */
    public void beam(Vec3 a, Vec3 b, double radius, int rgb, float alpha) {
        glow();
        ribbon(a, b, radius * 3.5, argb(rgb, alpha * 0.35f), argb(rgb, alpha * 0.25f));
        ribbon(a, b, radius * 1.8, argb(rgb, alpha * 0.7f), argb(rgb, alpha * 0.6f));
        ribbon(a, b, radius * 0.7, argb(0xFFFFFF, alpha), argb(0xFFFFFF, alpha * 0.9f));
        tube(a, b, radius * 0.9, rgb, alpha * 0.35f, 10);
    }

    /** Open cylinder with fresnel shading. */
    public void tube(Vec3 a, Vec3 b, double r, int rgb, float alpha, int sides) {
        Vec3 n = b.subtract(a).normalize();
        Vec3 u = perp(n), w = n.cross(u).normalize();
        for (int i = 0; i < sides; i++) {
            double t0 = Math.PI * 2 * i / sides, t1 = Math.PI * 2 * (i + 1) / sides;
            Vec3 a0 = onCircle(a, u, w, r, t0), a1 = onCircle(a, u, w, r, t1), b0 = onCircle(b, u, w, r, t0), b1 = onCircle(b, u, w, r, t1);
            int c0 = shade(a, a0.add(n.scale(-0)), rgb, alpha * 0.4f, alpha);
            quad(a0, b0, b1, a1, c0, c0, c0, c0);
        }
    }

    /** Branching, jagged lightning between a and b; deterministic per seed so it flickers when the seed changes. */
    public void bolt(Vec3 a, Vec3 b, long seed, double jitter, double width, int rgb, float alpha, int depth) {
        RandomSource r = RandomSource.create(seed);
        boltRec(a, b, r, jitter, width, rgb, alpha, depth);
    }

    private void boltRec(Vec3 a, Vec3 b, RandomSource r, double jitter, double width, int rgb, float alpha, int depth) {
        double len = a.distanceTo(b);
        int steps = Math.max(3, (int) (len * 2.2));
        Vec3 d = b.subtract(a);
        Vec3 u = perp(d.normalize()), w = d.normalize().cross(u);
        Vec3 prev = a;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double env = Math.sin(Math.PI * t);
            Vec3 p = i == steps ? b : a.add(d.scale(t)).add(u.scale((r.nextDouble() - 0.5) * jitter * env)).add(w.scale((r.nextDouble() - 0.5) * jitter * env));
            ribbon(prev, p, width * 4, argb(rgb, alpha * 0.35f), argb(rgb, alpha * 0.35f));
            ribbon(prev, p, width, argb(0xFFFFFF, alpha), argb(0xFFFFFF, alpha));
            if (depth > 0 && r.nextFloat() < 0.18f) {
                Vec3 off = d.normalize().scale(len * 0.25).add(u.scale((r.nextDouble() - 0.5) * len * 0.5)).add(w.scale((r.nextDouble() - 0.5) * len * 0.5));
                boltRec(p, p.add(off), r, jitter * 0.6, width * 0.6, rgb, alpha * 0.7f, depth - 1);
            }
            prev = p;
        }
    }

    /** Helix of {@code strands} ribbons wound around the a→b axis. */
    public void helix(Vec3 a, Vec3 b, double radius, double turns, int strands, double phase, double width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        Vec3 n = d.normalize(), u = perp(n), w = n.cross(u).normalize();
        int steps = Math.max(12, (int) (d.length() * 4));
        for (int s = 0; s < strands; s++) {
            double off = phase + Math.PI * 2 * s / strands;
            Vec3 prev = null;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3 p = onCircle(a.add(d.scale(t)), u, w, radius, off + t * turns * Math.PI * 2);
                if (prev != null) {
                    ribbon(prev, p, width, argb(rgb, alpha), argb(rgb, alpha));
                }
                prev = p;
            }
        }
    }

    /** Regular polygon outline (for rune circles / mandalas). */
    public void polygon(Vec3 c, Vec3 n, double r, int sides, double rot, double width, int rgb, float alpha) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        for (int i = 0; i < sides; i++) {
            Vec3 p0 = onCircle(c, u, w, r, rot + Math.PI * 2 * i / sides), p1 = onCircle(c, u, w, r, rot + Math.PI * 2 * (i + 1) / sides);
            line(p0, p1, n, width, rgb, alpha);
        }
    }

    /** Flat line lying in the plane with normal n (not camera facing). */
    public void line(Vec3 a, Vec3 b, Vec3 n, double width, int rgb, float alpha) {
        Vec3 side = b.subtract(a).cross(n).normalize().scale(width * 0.5);
        int core = argb(rgb, alpha), edge = argb(rgb, 0);
        quad(a.subtract(side), a, b, b.subtract(side), edge, core, core, edge);
        quad(a, a.add(side), b.add(side), b, core, edge, edge, core);
    }

    /** A star of {@code points} with inner radius ratio, drawn as outline in plane n. */
    public void star(Vec3 c, Vec3 n, double r, double inner, int points, double rot, double width, int rgb, float alpha) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        Vec3 prev = null, first = null;
        for (int i = 0; i <= points * 2; i++) {
            double rr = i % 2 == 0 ? r : r * inner;
            Vec3 p = onCircle(c, u, w, rr, rot + Math.PI * i / points);
            if (prev != null) {
                line(prev, p, n, width, rgb, alpha);
            }
            prev = p;
        }
    }

    /** Tapered crescent blade (Dismantle, slashes) centred at c, opening along dir, lying in plane with normal n. */
    public void crescent(Vec3 c, Vec3 dir, Vec3 n, double r, double thickness, double sweep, int rgb, float alpha) {
        Vec3 u = dir.normalize(), w = n.cross(u).normalize();
        int seg = 24;
        for (int i = 0; i < seg; i++) {
            double t0 = -sweep / 2 + sweep * i / seg, t1 = -sweep / 2 + sweep * (i + 1) / seg;
            double th0 = thickness * Math.sin(Math.PI * i / seg), th1 = thickness * Math.sin(Math.PI * (i + 1) / seg);
            Vec3 o0 = onCircle(c, u, w, r, t0), o1 = onCircle(c, u, w, r, t1);
            Vec3 i0 = onCircle(c, u, w, r - th0, t0), i1 = onCircle(c, u, w, r - th1, t1);
            Vec3 g0 = onCircle(c, u, w, r - th0 * 3, t0), g1 = onCircle(c, u, w, r - th1 * 3, t1);
            quad(i0, o0, o1, i1, argb(0xFFFFFF, alpha), argb(rgb, alpha), argb(rgb, alpha), argb(0xFFFFFF, alpha));
            quad(g0, i0, i1, g1, argb(rgb, 0), argb(rgb, alpha * 0.6f), argb(rgb, alpha * 0.6f), argb(rgb, 0));
        }
    }

    /** Vertical flame tongue rising from base (camera facing), flickering by phase. */
    public void flame(Vec3 base, double height, double width, double phase, int hot, int cool, float alpha) {
        Vec3 prev = base;
        int steps = 6;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = base.add(Math.sin(phase + t * 5) * width * 0.4 * t, height * t, Math.cos(phase * 1.3 + t * 4) * width * 0.4 * t);
            double wdt = width * (1 - t * 0.85);
            ribbon(prev, p, wdt, argb(lerp(hot, cool, (float) t - 0.17f), alpha * (float) (1 - t * 0.5)), argb(lerp(hot, cool, (float) t), alpha * (float) (1 - t)));
            prev = p;
        }
    }

    public Vec3 cameraLook() {
        return camLook;
    }
}

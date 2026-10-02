package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Geometry for the rune shader. A pattern is selected per quad through its UVs (see rune.fsh), so every mandala,
 * clock and chain batches into one draw.
 */
public final class MaDraw {
    public static final int RIM = 0;
    public static final int SHIELD = 1;
    public static final int SIGIL = 2;
    public static final int CLOCK = 3;
    public static final int BAND = 4;
    public static final int RIPPLE = 5;
    public static final int LATTICE = 6;
    public static final int VORTEX = 7;
    public static final int HALO = 8;
    public static final int STAR = 9;

    private MaDraw() {}

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a, float u, float v) {
        vc.addVertex(m, x, y, z).setColor(ColorUtil.r(rgb), ColorUtil.g(rgb), ColorUtil.b(rgb), Math.max(0f, Math.min(1f, a))).setUv(u, v);
    }

    /** A rune disc of {@code radius} centred at {@code c}, lying in the plane with normal {@code n}; {@code spin} rotates it. */
    public static void rune(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f n, float radius, float spin, int pattern, int seed, int rgb, float alpha) {
        if (alpha <= 0.003f || radius <= 0.001f) return;
        Vector3f nn = new Vector3f(n).normalize();
        Vector3f ref = Math.abs(nn.y) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(ref).cross(nn).normalize();
        Vector3f w = new Vector3f(nn).cross(u).normalize();
        if (spin != 0) {
            float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
            Vector3f u2 = new Vector3f(u).mul(cs).add(new Vector3f(w).mul(sn));
            Vector3f w2 = new Vector3f(w).mul(cs).sub(new Vector3f(u).mul(sn));
            u = u2;
            w = w2;
        }
        quad(vc, pose, c, u.mul(radius), w.mul(radius), pattern, seed, rgb, alpha);
    }

    /** Camera-facing rune disc. */
    public static void runeBillboard(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, float radius, float spin, int pattern, int seed, int rgb, float alpha) {
        Quaternionf cam = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Quaternionf inv = new Quaternionf(pose.pose().getNormalizedRotation(new Quaternionf())).conjugate().mul(cam);
        float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        Vector3f u = new Vector3f(cs * radius, sn * radius, 0).rotate(inv);
        Vector3f w = new Vector3f(-sn * radius, cs * radius, 0).rotate(inv);
        quad(vc, pose, c, u, w, pattern, seed, rgb, alpha);
    }

    /** A quad spanned by half-axes {@code hu} and {@code hv} around {@code c}. */
    public static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f hu, Vector3f hv, int pattern, int seed, int rgb, float alpha) {
        Matrix4f m = pose.pose();
        float su = (seed & 1023) * 2f, sv = pattern * 2f;
        v(vc, m, c.x - hu.x - hv.x, c.y - hu.y - hv.y, c.z - hu.z - hv.z, rgb, alpha, su, sv);
        v(vc, m, c.x + hu.x - hv.x, c.y + hu.y - hv.y, c.z + hu.z - hv.z, rgb, alpha, su + 1f, sv);
        v(vc, m, c.x + hu.x + hv.x, c.y + hu.y + hv.y, c.z + hu.z + hv.z, rgb, alpha, su + 1f, sv + 1f);
        v(vc, m, c.x - hu.x + hv.x, c.y - hu.y + hv.y, c.z - hu.z + hv.z, rgb, alpha, su, sv + 1f);
    }

    /** A rune band (pattern BAND) following a circle: binding chains, clock rims, prison straps. */
    public static void bandRing(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f n, float radius, float width, int seed, int rgb, float alpha, int segments) {
        Vector3f nn = new Vector3f(n).normalize();
        Vector3f ref = Math.abs(nn.y) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(ref).cross(nn).normalize();
        Vector3f w = new Vector3f(nn).cross(u).normalize();
        Matrix4f m = pose.pose();
        float su = (seed & 1023) * 2f, sv = BAND * 2f;
        float in = radius - width * 0.5f, out = radius + width * 0.5f;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float uu0 = (float) i / segments * 0.999f, uu1 = (float) (i + 1) / segments * 0.999f;
            Vector3f d0 = new Vector3f(u).mul(c0).add(new Vector3f(w).mul(s0));
            Vector3f d1 = new Vector3f(u).mul(c1).add(new Vector3f(w).mul(s1));
            v(vc, m, c.x + d0.x * in, c.y + d0.y * in, c.z + d0.z * in, rgb, alpha, su + uu0, sv);
            v(vc, m, c.x + d0.x * out, c.y + d0.y * out, c.z + d0.z * out, rgb, alpha, su + uu0, sv + 1f);
            v(vc, m, c.x + d1.x * out, c.y + d1.y * out, c.z + d1.z * out, rgb, alpha, su + uu1, sv + 1f);
            v(vc, m, c.x + d1.x * in, c.y + d1.y * in, c.z + d1.z * in, rgb, alpha, su + uu1, sv);
        }
    }

    /** A partial band ring covering {@code fraction} of the circle (duration indicators). */
    public static void bandArc(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f n, float radius, float width, float fraction, int seed, int rgb, float alpha) {
        int segs = Math.max(1, (int) Math.ceil(48 * Math.max(0f, Math.min(1f, fraction))));
        Vector3f nn = new Vector3f(n).normalize();
        Vector3f ref = Math.abs(nn.y) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(ref).cross(nn).normalize();
        Vector3f w = new Vector3f(nn).cross(u).normalize();
        Matrix4f m = pose.pose();
        float su = (seed & 1023) * 2f, sv = BAND * 2f;
        float in = radius - width * 0.5f, out = radius + width * 0.5f;
        double total = Math.PI * 2 * fraction;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI / 2 + total * i / segs, a1 = Math.PI / 2 + total * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            Vector3f d0 = new Vector3f(u).mul(c0).add(new Vector3f(w).mul(s0));
            Vector3f d1 = new Vector3f(u).mul(c1).add(new Vector3f(w).mul(s1));
            float uu0 = (float) i / 48 * 0.999f, uu1 = (float) (i + 1) / 48 * 0.999f;
            v(vc, m, c.x + d0.x * in, c.y + d0.y * in, c.z + d0.z * in, rgb, alpha, su + uu0, sv);
            v(vc, m, c.x + d0.x * out, c.y + d0.y * out, c.z + d0.z * out, rgb, alpha, su + uu0, sv + 1f);
            v(vc, m, c.x + d1.x * out, c.y + d1.y * out, c.z + d1.z * out, rgb, alpha, su + uu1, sv + 1f);
            v(vc, m, c.x + d1.x * in, c.y + d1.y * in, c.z + d1.z * in, rgb, alpha, su + uu1, sv);
        }
    }

    /** A camera-facing rune band between two points (a single chain link strip). */
    public static void bandLine(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 eye, float width, int seed, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) return;
        Vec3 side = dir.cross(a.add(b).scale(0.5).subtract(eye));
        if (side.lengthSqr() < 1e-8) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width * 0.5);
        Matrix4f m = pose.pose();
        float su = (seed & 1023) * 2f, sv = BAND * 2f;
        float len = (float) Math.min(0.999, dir.length() / 6.0);
        v(vc, m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), rgb, alpha, su, sv);
        v(vc, m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), rgb, alpha, su, sv + 1f);
        v(vc, m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), rgb, alpha, su + len, sv + 1f);
        v(vc, m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), rgb, alpha, su + len, sv);
    }

    /** Lattice cube (spatial prison). */
    public static void latticeCube(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, float half, int seed, int rgb, float alpha) {
        Vector3f x = new Vector3f(half, 0, 0), y = new Vector3f(0, half, 0), z = new Vector3f(0, 0, half);
        quad(vc, pose, new Vector3f(c).add(x), new Vector3f(z), new Vector3f(y), LATTICE, seed, rgb, alpha);
        quad(vc, pose, new Vector3f(c).sub(x), new Vector3f(z), new Vector3f(y), LATTICE, seed + 1, rgb, alpha);
        quad(vc, pose, new Vector3f(c).add(z), new Vector3f(x), new Vector3f(y), LATTICE, seed + 2, rgb, alpha);
        quad(vc, pose, new Vector3f(c).sub(z), new Vector3f(x), new Vector3f(y), LATTICE, seed + 3, rgb, alpha);
        quad(vc, pose, new Vector3f(c).add(y), new Vector3f(x), new Vector3f(z), LATTICE, seed + 4, rgb, alpha);
        quad(vc, pose, new Vector3f(c).sub(y), new Vector3f(x), new Vector3f(z), LATTICE, seed + 5, rgb, alpha);
    }

    public static Vector3f vec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }
}

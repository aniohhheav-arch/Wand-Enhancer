package dev.riftverse.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immediate-mode geometry helpers. All coordinates are in the current pose's local space. Entity-format vertices
 * (solid/translucent/glow types) need light + overlay + normal; energy vertices only position, colour and UV.
 */
public final class FxDraw {
    public static final int FULL_BRIGHT = 0xF000F0;

    private FxDraw() {}

    // ------------------------------------------------------------------ entity-format primitives

    public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int rgb, float alpha, float u, float v,
                              int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(ColorUtil.r(rgb), ColorUtil.g(rgb), ColorUtil.b(rgb), alpha).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int rgb, float alpha, int light, int overlay) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
        if (n.lengthSquared() < 1e-12f) n.set(0, 1, 0);
        n.normalize();
        vertex(vc, pose, a.x, a.y, a.z, rgb, alpha, 0, 0, light, overlay, n.x, n.y, n.z);
        vertex(vc, pose, b.x, b.y, b.z, rgb, alpha, 0, 1, light, overlay, n.x, n.y, n.z);
        vertex(vc, pose, c.x, c.y, c.z, rgb, alpha, 1, 1, light, overlay, n.x, n.y, n.z);
        vertex(vc, pose, d.x, d.y, d.z, rgb, alpha, 1, 0, light, overlay, n.x, n.y, n.z);
    }

    public static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                           int rgb, float alpha, int light, int overlay) {
        Vector3f p000 = new Vector3f(x0, y0, z0), p100 = new Vector3f(x1, y0, z0), p110 = new Vector3f(x1, y1, z0), p010 = new Vector3f(x0, y1, z0);
        Vector3f p001 = new Vector3f(x0, y0, z1), p101 = new Vector3f(x1, y0, z1), p111 = new Vector3f(x1, y1, z1), p011 = new Vector3f(x0, y1, z1);
        int top = ColorUtil.scale(rgb, 1.0f), side = ColorUtil.scale(rgb, 0.85f), bottom = ColorUtil.scale(rgb, 0.6f);
        quad(vc, pose, p010, p011, p111, p110, top, alpha, light, overlay);
        quad(vc, pose, p000, p100, p101, p001, bottom, alpha, light, overlay);
        quad(vc, pose, p000, p010, p110, p100, side, alpha, light, overlay);
        quad(vc, pose, p101, p111, p011, p001, side, alpha, light, overlay);
        quad(vc, pose, p001, p011, p010, p000, side, alpha, light, overlay);
        quad(vc, pose, p100, p110, p111, p101, side, alpha, light, overlay);
    }

    /** Centered box. */
    public static void cube(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, float hx, float hy, float hz, int rgb, float alpha, int light, int overlay) {
        box(vc, pose, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, rgb, alpha, light, overlay);
    }

    /** Elongated octahedron: the basic crystal shard. */
    public static void crystal(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, float radius, float height, int rgb, float alpha, int light, int overlay) {
        Vector3f top = new Vector3f(cx, cy + height, cz);
        Vector3f bottom = new Vector3f(cx, cy - height, cz);
        Vector3f[] ring = new Vector3f[6];
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            ring[i] = new Vector3f(cx + (float) Math.cos(a) * radius, cy, cz + (float) Math.sin(a) * radius);
        }
        for (int i = 0; i < 6; i++) {
            Vector3f a = ring[i], b = ring[(i + 1) % 6];
            int shade = ColorUtil.scale(rgb, 0.75f + 0.25f * (float) Math.cos(i));
            quad(vc, pose, a, top, top, b, shade, alpha, light, overlay);
            quad(vc, pose, b, bottom, bottom, a, ColorUtil.scale(shade, 0.8f), alpha, light, overlay);
        }
    }

    /** Ellipsoid built from latitude/longitude quads. */
    public static void ellipsoid(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, float rx, float ry, float rz,
                                 int segments, int rgbTop, int rgbBottom, float alpha, int light, int overlay, float minLat) {
        int rings = Math.max(3, segments / 2);
        for (int i = 0; i < rings; i++) {
            float lat0 = (float) (-Math.PI / 2 + Math.PI * i / rings);
            float lat1 = (float) (-Math.PI / 2 + Math.PI * (i + 1) / rings);
            if (lat1 < minLat) continue;
            int col = ColorUtil.lerp(rgbBottom, rgbTop, (i + 0.5f) / rings);
            for (int j = 0; j < segments; j++) {
                float lon0 = (float) (Math.PI * 2 * j / segments);
                float lon1 = (float) (Math.PI * 2 * (j + 1) / segments);
                Vector3f a = sph(cx, cy, cz, rx, ry, rz, lat0, lon0);
                Vector3f b = sph(cx, cy, cz, rx, ry, rz, lat1, lon0);
                Vector3f c = sph(cx, cy, cz, rx, ry, rz, lat1, lon1);
                Vector3f d = sph(cx, cy, cz, rx, ry, rz, lat0, lon1);
                float nlat = (lat0 + lat1) * 0.5f, nlon = (lon0 + lon1) * 0.5f;
                float nx = (float) (Math.cos(nlat) * Math.cos(nlon)), ny = (float) Math.sin(nlat), nz = (float) (Math.cos(nlat) * Math.sin(nlon));
                vertex(vc, pose, a.x, a.y, a.z, col, alpha, 0, 0, light, overlay, nx, ny, nz);
                vertex(vc, pose, b.x, b.y, b.z, col, alpha, 0, 1, light, overlay, nx, ny, nz);
                vertex(vc, pose, c.x, c.y, c.z, col, alpha, 1, 1, light, overlay, nx, ny, nz);
                vertex(vc, pose, d.x, d.y, d.z, col, alpha, 1, 0, light, overlay, nx, ny, nz);
            }
        }
    }

    private static Vector3f sph(float cx, float cy, float cz, float rx, float ry, float rz, float lat, float lon) {
        return new Vector3f(cx + rx * (float) (Math.cos(lat) * Math.cos(lon)), cy + ry * (float) Math.sin(lat), cz + rz * (float) (Math.cos(lat) * Math.sin(lon)));
    }

    /**
     * Lofted tube through a chain of rings: centers[i] with radii (rw, rh) and colours. Each ring is oriented
     * perpendicular to the local spine direction. Used for whales, serpents and robes.
     */
    public static void loft(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] centers, float[] rw, float[] rh, int[] top, int[] belly,
                            int segments, float alpha, int light, int overlay) {
        int n = centers.length;
        Vector3f[][] rings = new Vector3f[n][segments];
        Vector3f[][] normals = new Vector3f[n][segments];
        for (int i = 0; i < n; i++) {
            Vector3f fwd = new Vector3f(centers[Math.min(n - 1, i + 1)]).sub(centers[Math.max(0, i - 1)]);
            if (fwd.lengthSquared() < 1e-8f) fwd.set(0, 0, 1);
            fwd.normalize();
            Vector3f up = Math.abs(fwd.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f right = new Vector3f(fwd).cross(up).normalize();
            Vector3f realUp = new Vector3f(right).cross(fwd).normalize();
            for (int j = 0; j < segments; j++) {
                double a = Math.PI * 2 * j / segments;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                Vector3f nrm = new Vector3f(right).mul(c).add(new Vector3f(realUp).mul(s));
                normals[i][j] = nrm;
                rings[i][j] = new Vector3f(centers[i]).add(new Vector3f(right).mul(c * rw[i])).add(new Vector3f(realUp).mul(s * rh[i]));
            }
        }
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < segments; j++) {
                int k = (j + 1) % segments;
                float sideA = (float) Math.sin(Math.PI * 2 * (j + 0.5) / segments);
                int col0 = ColorUtil.lerp(belly[i], top[i], sideA * 0.5f + 0.5f);
                int col1 = ColorUtil.lerp(belly[i + 1], top[i + 1], sideA * 0.5f + 0.5f);
                vertexN(vc, pose, rings[i][j], normals[i][j], col0, alpha, light, overlay, 0, 0);
                vertexN(vc, pose, rings[i + 1][j], normals[i + 1][j], col1, alpha, light, overlay, 0, 1);
                vertexN(vc, pose, rings[i + 1][k], normals[i + 1][k], col1, alpha, light, overlay, 1, 1);
                vertexN(vc, pose, rings[i][k], normals[i][k], col0, alpha, light, overlay, 1, 0);
            }
        }
    }

    /** Tapered tube between two points: legs, arms, necks, stalks. */
    public static void limb(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, float ra, float rb, int rgbA, int rgbB, float alpha, int light, int overlay) {
        loft(vc, pose, new Vector3f[]{a, b}, new float[]{ra, rb}, new float[]{ra, rb}, new int[]{rgbA, rgbB}, new int[]{ColorUtil.scale(rgbA, 0.7f), ColorUtil.scale(rgbB, 0.7f)},
                6, alpha, light, overlay);
    }

    private static void vertexN(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, Vector3f n, int rgb, float alpha, int light, int overlay, float u, float v) {
        vertex(vc, pose, p.x, p.y, p.z, rgb, alpha, u, v, light, overlay, n.x, n.y, n.z);
    }

    // ------------------------------------------------------------------ billboards

    /** Camera-facing quad (entity format) centred at a local point. */
    public static void billboard(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float size, int rgb, float alpha) {
        Quaternionf cam = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Quaternionf inv = new Quaternionf(pose.pose().getNormalizedRotation(new Quaternionf())).conjugate().mul(cam);
        Vector3f r = new Vector3f(size, 0, 0).rotate(inv);
        Vector3f u = new Vector3f(0, size, 0).rotate(inv);
        int c = ColorUtil.scale(rgb, Math.max(0f, Math.min(1f, alpha)));
        vertex(vc, pose, x - r.x - u.x, y - r.y - u.y, z - r.z - u.z, c, 1f, 0, 1, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, 1, 0);
        vertex(vc, pose, x + r.x - u.x, y + r.y - u.y, z + r.z - u.z, c, 1f, 1, 1, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, 1, 0);
        vertex(vc, pose, x + r.x + u.x, y + r.y + u.y, z + r.z + u.z, c, 1f, 1, 0, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, 1, 0);
        vertex(vc, pose, x - r.x + u.x, y - r.y + u.y, z - r.z + u.z, c, 1f, 0, 0, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, 1, 0);
    }

    // ------------------------------------------------------------------ energy-format primitives

    private static void evertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float alpha, float u, float v) {
        vc.addVertex(m, x, y, z).setColor(ColorUtil.r(rgb), ColorUtil.g(rgb), ColorUtil.b(rgb), alpha).setUv(u, v);
    }

    /** Camera-facing energy ribbon between two local points. {@code eye} is the camera position in the same local space. */
    public static void beam(VertexConsumer vc, PoseStack.Pose pose, Vec3 from, Vec3 to, Vec3 eye, float width, int rgb, float alpha) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-4) return;
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 side = dir.cross(mid.subtract(eye));
        if (side.lengthSqr() < 1e-8) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-8) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width * 0.5);
        Matrix4f m = pose.pose();
        float u1 = (float) len;
        evertex(vc, m, (float) (from.x - side.x), (float) (from.y - side.y), (float) (from.z - side.z), rgb, alpha, 0, 0);
        evertex(vc, m, (float) (from.x + side.x), (float) (from.y + side.y), (float) (from.z + side.z), rgb, alpha, 0, 1);
        evertex(vc, m, (float) (to.x + side.x), (float) (to.y + side.y), (float) (to.z + side.z), rgb, alpha, u1, 1);
        evertex(vc, m, (float) (to.x - side.x), (float) (to.y - side.y), (float) (to.z - side.z), rgb, alpha, u1, 0);
    }

    /** Flat energy ring in the plane perpendicular to {@code normal}. */
    public static void ring(VertexConsumer vc, PoseStack.Pose pose, Vector3f center, Vector3f normal, float radius, float width, int rgb, float alpha, int segments) {
        Vector3f n = new Vector3f(normal).normalize();
        Vector3f a = Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(a).cross(n).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        Matrix4f m = pose.pose();
        float inner = radius - width * 0.5f, outer = radius + width * 0.5f;
        for (int i = 0; i < segments; i++) {
            double t0 = Math.PI * 2 * i / segments, t1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float l0 = (float) (radius * t0), l1 = (float) (radius * t1);
            evertex(vc, m, center.x + (u.x * c0 + v.x * s0) * inner, center.y + (u.y * c0 + v.y * s0) * inner, center.z + (u.z * c0 + v.z * s0) * inner, rgb, alpha, l0, 0);
            evertex(vc, m, center.x + (u.x * c0 + v.x * s0) * outer, center.y + (u.y * c0 + v.y * s0) * outer, center.z + (u.z * c0 + v.z * s0) * outer, rgb, alpha, l0, 1);
            evertex(vc, m, center.x + (u.x * c1 + v.x * s1) * outer, center.y + (u.y * c1 + v.y * s1) * outer, center.z + (u.z * c1 + v.z * s1) * outer, rgb, alpha, l1, 1);
            evertex(vc, m, center.x + (u.x * c1 + v.x * s1) * inner, center.y + (u.y * c1 + v.y * s1) * inner, center.z + (u.z * c1 + v.z * s1) * inner, rgb, alpha, l1, 0);
        }
    }

    /** Wavy ribbon (tentacle, cape strand) through a series of local points. */
    public static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3[] points, Vec3 eye, float width, int rgbStart, int rgbEnd, float alpha) {
        for (int i = 0; i < points.length - 1; i++) {
            float t = (float) i / (points.length - 1);
            float w = width * (1f - t * 0.85f);
            beam(vc, pose, points[i], points[i + 1], eye, w, ColorUtil.lerp(rgbStart, rgbEnd, t), alpha * (1f - t * 0.6f));
        }
    }

    /** Camera position expressed in the local space of {@code pose} (renderers pass the entity's render origin). */
    public static Vec3 eyeLocal(Vec3 cameraWorld, Vec3 originWorld) {
        return cameraWorld.subtract(originWorld);
    }
}

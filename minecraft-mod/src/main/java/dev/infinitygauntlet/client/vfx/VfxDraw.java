package dev.infinitygauntlet.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Low-level geometry builders for the VFX engine. All positions are relative to the current pose; textured methods
 * emit POSITION_TEX_COLOR vertices, line methods POSITION_COLOR.
 */
public final class VfxDraw {
    /** Deterministic hash → [0,1); used instead of Random so every client draws identical shapes. */
    public static float hash(long seed, int a, int b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    public static float r(int rgb) {
        return (rgb >> 16 & 255) / 255f;
    }

    public static float g(int rgb) {
        return (rgb >> 8 & 255) / 255f;
    }

    public static float b(int rgb) {
        return (rgb & 255) / 255f;
    }

    /** Camera-facing square of side {@code size} centred at (x, y, z), rotated by {@code roll} radians in screen space. */
    public static void billboard(PoseStack.Pose pose, VertexConsumer vc, Quaternionf camRot, float x, float y, float z, float size, float roll,
                                 float r, float g, float b, float a) {
        billboard(pose, vc, camRot, x, y, z, size, roll, 0, 0, 1, 1, r, g, b, a);
    }

    /** Smoke puff from the 2×2 smoke atlas; {@code variant} selects the cell. */
    public static void smokePuff(PoseStack.Pose pose, VertexConsumer vc, Quaternionf camRot, float x, float y, float z, float size, float roll,
                                 int variant, float r, float g, float b, float a) {
        float u = (variant & 1) * 0.5f;
        float v = (variant >> 1 & 1) * 0.5f;
        billboard(pose, vc, camRot, x, y, z, size, roll, u, v, u + 0.5f, v + 0.5f, r, g, b, a);
    }

    public static void billboard(PoseStack.Pose pose, VertexConsumer vc, Quaternionf camRot, float x, float y, float z, float size, float roll,
                                 float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        if (a <= 0.003f || size <= 0) {
            return;
        }
        float h = size / 2;
        float c = Mth.cos(roll) * h;
        float s = Mth.sin(roll) * h;
        Matrix4f m = pose.pose();
        Vector3f v = new Vector3f();
        quadVertex(vc, m, camRot, v, x, y, z, -c + s, -s - c, u0, v1, r, g, b, a);
        quadVertex(vc, m, camRot, v, x, y, z, -c - s, -s + c, u0, v0, r, g, b, a);
        quadVertex(vc, m, camRot, v, x, y, z, c - s, s + c, u1, v0, r, g, b, a);
        quadVertex(vc, m, camRot, v, x, y, z, c + s, s - c, u1, v1, r, g, b, a);
    }

    private static void quadVertex(VertexConsumer vc, Matrix4f m, Quaternionf rot, Vector3f tmp, float x, float y, float z, float lx, float ly,
                                   float u, float vv, float r, float g, float b, float a) {
        camRotTransform(rot, tmp.set(lx, ly, 0));
        vc.addVertex(m, x + tmp.x, y + tmp.y, z + tmp.z).setUv(u, vv).setColor(r, g, b, a);
    }

    private static void camRotTransform(Quaternionf rot, Vector3f v) {
        rot.transform(v);
    }

    /**
     * Flat annulus centred at the origin of {@code pose}, lying in the plane spanned by unit vectors {@code ax} and
     * {@code ay}. The ring texture's V runs from the inner edge (0) to the outer edge (1); U wraps around.
     */
    public static void ring(PoseStack.Pose pose, VertexConsumer vc, Vec3 ax, Vec3 ay, float inner, float outer, int segments, float uOffset,
                            float r, float g, float b, float a) {
        if (a <= 0.003f || outer <= 0) {
            return;
        }
        Matrix4f m = pose.pose();
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2 / segments);
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            float u0 = (float) i / segments * 4 + uOffset, u1 = (float) (i + 1) / segments * 4 + uOffset;
            vertex(vc, m, ax, ay, c0 * inner, s0 * inner, u0, 0, r, g, b, a);
            vertex(vc, m, ax, ay, c0 * outer, s0 * outer, u0, 1, r, g, b, a);
            vertex(vc, m, ax, ay, c1 * outer, s1 * outer, u1, 1, r, g, b, a);
            vertex(vc, m, ax, ay, c1 * inner, s1 * inner, u1, 0, r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 ax, Vec3 ay, float px, float py, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, (float) (ax.x * px + ay.x * py), (float) (ax.y * px + ay.y * py), (float) (ax.z * px + ay.z * py))
                .setUv(u, v).setColor(r, g, b, a);
    }

    /** Textured square in the plane of {@code ax}/{@code ay} (ground glyphs, decals). */
    public static void plane(PoseStack.Pose pose, VertexConsumer vc, Vec3 ax, Vec3 ay, float size, float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        float h = size / 2;
        vertex(vc, m, ax, ay, -h, -h, 0, 0, r, g, b, a);
        vertex(vc, m, ax, ay, -h, h, 0, 1, r, g, b, a);
        vertex(vc, m, ax, ay, h, h, 1, 1, r, g, b, a);
        vertex(vc, m, ax, ay, h, -h, 1, 0, r, g, b, a);
    }

    /**
     * UV sphere with fresnel alpha: {@code edge} is the alpha at the silhouette, {@code center} the alpha facing the
     * camera, which gives soft bubble shells and refraction-like lenses.
     */
    public static void sphere(PoseStack.Pose pose, VertexConsumer vc, Vec3 camLocal, float radius, int lat, int lon, float vScroll,
                              float r, float g, float b, float edge, float center) {
        if (radius <= 0 || Math.max(edge, center) <= 0.003f) {
            return;
        }
        Matrix4f m = pose.pose();
        for (int i = 0; i < lat; i++) {
            float th0 = (float) (Math.PI * i / lat);
            float th1 = (float) (Math.PI * (i + 1) / lat);
            for (int j = 0; j < lon; j++) {
                float ph0 = (float) (2 * Math.PI * j / lon);
                float ph1 = (float) (2 * Math.PI * (j + 1) / lon);
                sphereVertex(vc, m, camLocal, radius, th0, ph0, (float) j / lon, (float) i / lat + vScroll, r, g, b, edge, center);
                sphereVertex(vc, m, camLocal, radius, th1, ph0, (float) j / lon, (float) (i + 1) / lat + vScroll, r, g, b, edge, center);
                sphereVertex(vc, m, camLocal, radius, th1, ph1, (float) (j + 1) / lon, (float) (i + 1) / lat + vScroll, r, g, b, edge, center);
                sphereVertex(vc, m, camLocal, radius, th0, ph1, (float) (j + 1) / lon, (float) i / lat + vScroll, r, g, b, edge, center);
            }
        }
    }

    private static void sphereVertex(VertexConsumer vc, Matrix4f m, Vec3 cam, float radius, float th, float ph, float u, float v,
                                     float r, float g, float b, float edge, float center) {
        float nx = Mth.sin(th) * Mth.cos(ph);
        float ny = Mth.cos(th);
        float nz = Mth.sin(th) * Mth.sin(ph);
        double dx = cam.x - nx * radius, dy = cam.y - ny * radius, dz = cam.z - nz * radius;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float facing = len < 1.0E-4 ? 1 : (float) Math.abs((nx * dx + ny * dy + nz * dz) / len);
        float rim = 1 - facing;
        float a = center + (edge - center) * rim * rim;
        vc.addVertex(m, nx * radius, ny * radius, nz * radius).setUv(u, v).setColor(r, g, b, a);
    }

    /** Camera-facing textured ribbon between two points (beams). V scrolls along the beam. */
    public static void beam(PoseStack.Pose pose, VertexConsumer vc, Vec3 from, Vec3 to, Vec3 camLocal, float width, float vScroll,
                            float r, float g, float b, float a) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-4 || a <= 0.003f) {
            return;
        }
        Vec3 mid = from.add(dir.scale(0.5));
        Vec3 side = dir.cross(camLocal.subtract(mid));
        if (side.lengthSqr() < 1.0E-8) {
            side = dir.cross(new Vec3(0, 1, 0));
        }
        side = side.normalize().scale(width / 2);
        Matrix4f m = pose.pose();
        float v1 = vScroll + (float) len / Math.max(0.1f, width * 4);
        vc.addVertex(m, (float) (from.x - side.x), (float) (from.y - side.y), (float) (from.z - side.z)).setUv(0, vScroll).setColor(r, g, b, a);
        vc.addVertex(m, (float) (from.x + side.x), (float) (from.y + side.y), (float) (from.z + side.z)).setUv(1, vScroll).setColor(r, g, b, a);
        vc.addVertex(m, (float) (to.x + side.x), (float) (to.y + side.y), (float) (to.z + side.z)).setUv(1, v1).setColor(r, g, b, a);
        vc.addVertex(m, (float) (to.x - side.x), (float) (to.y - side.y), (float) (to.z - side.z)).setUv(0, v1).setColor(r, g, b, a);
    }

    /** Untextured camera-facing ribbon segment (POSITION_COLOR). */
    public static void line(PoseStack.Pose pose, VertexConsumer vc, Vec3 from, Vec3 to, Vec3 camLocal, float width, float r, float g, float b, float a) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 side = dir.cross(camLocal.subtract(from));
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width / 2);
        Matrix4f m = pose.pose();
        vc.addVertex(m, (float) (from.x - side.x), (float) (from.y - side.y), (float) (from.z - side.z)).setColor(r, g, b, a);
        vc.addVertex(m, (float) (from.x + side.x), (float) (from.y + side.y), (float) (from.z + side.z)).setColor(r, g, b, a);
        vc.addVertex(m, (float) (to.x + side.x), (float) (to.y + side.y), (float) (to.z + side.z)).setColor(r, g, b, a);
        vc.addVertex(m, (float) (to.x - side.x), (float) (to.y - side.y), (float) (to.z - side.z)).setColor(r, g, b, a);
    }

    /**
     * Jagged lightning between two points: recursive midpoint displacement seeded by {@code seed}, drawn as a dim wide
     * glow pass plus a thin bright core, with occasional forks.
     */
    public static void lightning(PoseStack.Pose pose, VertexConsumer vc, Vec3 from, Vec3 to, Vec3 camLocal, long seed, float jitter, float width,
                                 float r, float g, float b, float a) {
        int segments = 10;
        Vec3[] pts = new Vec3[segments + 1];
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        Vec3 n1 = perpendicular(dir);
        Vec3 n2 = dir.cross(n1).normalize();
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float env = Mth.sin(t * Mth.PI);
            float o1 = (hash(seed, i, 1) - 0.5f) * 2 * jitter * env * (float) len * 0.25f;
            float o2 = (hash(seed, i, 2) - 0.5f) * 2 * jitter * env * (float) len * 0.25f;
            pts[i] = from.add(dir.scale(t)).add(n1.scale(o1)).add(n2.scale(o2));
        }
        for (int i = 0; i < segments; i++) {
            line(pose, vc, pts[i], pts[i + 1], camLocal, width * 3.5f, r, g, b, a * 0.22f);
            line(pose, vc, pts[i], pts[i + 1], camLocal, width, Math.min(1, r + 0.5f), Math.min(1, g + 0.5f), Math.min(1, b + 0.5f), a);
            if (hash(seed, i, 7) > 0.82f && i > 1) {
                Vec3 fork = pts[i].add(n1.scale((hash(seed, i, 8) - 0.5) * len * 0.3)).add(dir.scale(0.08)).add(n2.scale((hash(seed, i, 9) - 0.5) * len * 0.3));
                line(pose, vc, pts[i], fork, camLocal, width * 0.6f, r, g, b, a * 0.7f);
            }
        }
    }

    public static Vec3 perpendicular(Vec3 v) {
        Vec3 axis = Math.abs(v.y) < 0.9 * v.length() ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return v.cross(axis).normalize();
    }

    /** Rotates {@code v} around unit {@code axis} by {@code angle} radians (Rodrigues). */
    public static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }

    /** Uniformly distributed unit vector from two hashes. */
    public static Vec3 unit(long seed, int i) {
        double z = hash(seed, i, 11) * 2 - 1;
        double phi = hash(seed, i, 12) * Math.PI * 2;
        double rr = Math.sqrt(1 - z * z);
        return new Vec3(rr * Math.cos(phi), z, rr * Math.sin(phi));
    }

    private VfxDraw() {
    }
}

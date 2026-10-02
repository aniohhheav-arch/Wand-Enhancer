package dev.mysticarts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mysticarts.MaClientConfig;
import dev.mysticarts.util.ColorUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Screen-space refraction over the finished world: portal interiors, shock ripples, heat haze, temporal ripples and
 * gravitational pinches. Renderers queue instances during the frame; {@link #render} draws them after the level.
 */
public final class WarpFx {
    public static final int PORTAL = 0;
    public static final int RIPPLE = 1;
    public static final int HAZE = 2;
    public static final int TEMPORAL = 3;
    public static final int PINCH = 4;

    /** A disc with in-plane half-axes {@code u} and {@code v} (world units), or a sphere when {@code v} is null. */
    private record Item(Vec3 center, Vec3 u, Vec3 v, int mode, float strength, float progress, int color) {}

    private static final List<Item> ITEMS = new ArrayList<>();

    private WarpFx() {}

    public static void disc(Vec3 center, Vec3 u, Vec3 v, int mode, float strength, float progress, int color) {
        if (ITEMS.size() < 24) ITEMS.add(new Item(center, u, v, mode, strength, progress, color));
    }

    public static void sphere(Vec3 center, float radius, int mode, float strength, float progress, int color) {
        if (ITEMS.size() < 24) ITEMS.add(new Item(center, new Vec3(radius, 0, 0), null, mode, strength, progress, color));
    }

    public static void clear() {
        ITEMS.clear();
    }

    public static void render(Vec3 camera, Matrix4f view, Matrix4f projection) {
        ShaderInstance sh = MaShaders.warp;
        if (ITEMS.isEmpty() || sh == null || !MaClientConfig.distortion()) {
            ITEMS.clear();
            return;
        }
        Matrix4f viewProj = new Matrix4f(projection).mul(view);
        ITEMS.sort(Comparator.comparingDouble((Item i) -> i.center.distanceToSqr(camera)).reversed());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        for (Item it : ITEMS) draw(sh, it, camera, viewProj);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        ITEMS.clear();
    }

    private static float[] project(Matrix4f viewProj, Vec3 p) {
        Vector4f v = new Vector4f((float) p.x, (float) p.y, (float) p.z, 1f);
        viewProj.transform(v);
        if (v.w <= 0.05f) return null;
        return new float[] {v.x / v.w * 0.5f + 0.5f, v.y / v.w * 0.5f + 0.5f, v.z / v.w * 0.5f + 0.5f};
    }

    private static void draw(ShaderInstance sh, Item it, Vec3 camera, Matrix4f viewProj) {
        Vec3 c = it.center.subtract(camera);
        float[] pc = project(viewProj, c);
        if (pc == null) return;
        float ux, uy, vx, vy;
        if (it.v == null) {
            // sphere: project a camera-facing radius
            double r = it.u.x;
            double dist = Math.max(0.1, c.length());
            Vec3 across = new Vec3(-c.z, 0, c.x);
            across = across.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : across.normalize();
            float[] side = project(viewProj, c.add(across.scale(r)));
            float[] top = project(viewProj, c.add(c.normalize().cross(across).normalize().scale(r)));
            if (side == null || top == null) return;
            ux = side[0] - pc[0];
            uy = side[1] - pc[1];
            vx = top[0] - pc[0];
            vy = top[1] - pc[1];
            if (dist < r * 1.05) return;
        } else {
            float[] pu = project(viewProj, c.add(it.u));
            float[] pv = project(viewProj, c.add(it.v));
            if (pu == null || pv == null) return;
            ux = pu[0] - pc[0];
            uy = pu[1] - pc[1];
            vx = pv[0] - pc[0];
            vy = pv[1] - pc[1];
        }
        float extent = (float) Math.max(Math.hypot(ux, uy), Math.hypot(vx, vy)) * 1.3f;
        float x0 = (pc[0] - extent) * 2 - 1, x1 = (pc[0] + extent) * 2 - 1, y0 = (pc[1] - extent) * 2 - 1, y1 = (pc[1] + extent) * 2 - 1;
        if (x1 < -1 || x0 > 1 || y1 < -1 || y0 > 1) return;
        SceneCapture.capture();
        SceneCapture.bind();
        sh.safeGetUniform("ScreenSize").set((float) SceneCapture.width(), (float) SceneCapture.height());
        sh.safeGetUniform("WarpCenter").set(pc[0], pc[1]);
        sh.safeGetUniform("WarpU").set(ux, uy);
        sh.safeGetUniform("WarpV").set(vx, vy);
        sh.safeGetUniform("WarpParams").set((float) it.mode, it.strength, it.progress, it.mode == PORTAL ? pc[2] : 0f);
        sh.safeGetUniform("WarpColor").set(ColorUtil.r(it.color), ColorUtil.g(it.color), ColorUtil.b(it.color));
        Fullscreen.drawRect(sh, Math.max(-1, x0), Math.max(-1, y0), Math.min(1, x1), Math.min(1, y1));
    }
}

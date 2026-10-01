package dev.riftverse.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftverse.util.ColorUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Screen-space pass for everything that bends light: rifts, portals, gates and black holes. Renderers queue instances
 * during the world pass; after the level is drawn each one re-captures the scene and draws over its screen bounds.
 */
public final class SpatialFx {
    public static final int SHAPE_TEAR = 0;
    public static final int SHAPE_ELLIPSE = 1;
    public static final int SHAPE_RECT = 2;

    public static final int STYLE_PORTAL = 9;
    public static final int STYLE_GATE = 10;

    private record Rift(Vec3 center, Vec3 normal, Vec3 up, float halfW, float halfH, int colorA, int colorB, int style, float open, int shape,
                        float intensity, int destSky, float window) {}

    private record Hole(Vec3 center, float radius, Vector3f diskNormal, int style, float bloom, int hot, int cool) {}

    private record Item(double distSq, Object fx) {}

    private static final List<Rift> RIFTS = new ArrayList<>();
    private static final List<Hole> HOLES = new ArrayList<>();

    private SpatialFx() {}

    public static void rift(Vec3 center, Vec3 normal, Vec3 up, float halfW, float halfH, int colorA, int colorB, int style, float open, int shape,
                            float intensity, int destSky, float window) {
        if (RIFTS.size() < 24) RIFTS.add(new Rift(center, normal, up, halfW, halfH, colorA, colorB, style, open, shape, intensity, destSky, window));
    }

    public static void hole(Vec3 center, float radius, Vector3f diskNormal, int style, float bloom, int hot, int cool) {
        if (HOLES.size() < 8) HOLES.add(new Hole(center, radius, diskNormal, style, bloom, hot, cool));
    }

    public static boolean hasWork() {
        return !RIFTS.isEmpty() || !HOLES.isEmpty();
    }

    public static void clear() {
        RIFTS.clear();
        HOLES.clear();
    }

    public static void render(Vec3 camera, Matrix4f view, Matrix4f projection) {
        if (!hasWork()) return;
        ShaderInstance riftShader = RvShaders.rift;
        ShaderInstance holeShader = RvShaders.blackhole;
        Matrix4f viewProj = new Matrix4f(projection).mul(view);
        Matrix4f inv = new Matrix4f(viewProj).invert();

        List<Item> items = new ArrayList<>();
        for (Rift r : RIFTS) items.add(new Item(r.center.distanceToSqr(camera), r));
        for (Hole h : HOLES) items.add(new Item(h.center.distanceToSqr(camera), h));
        items.sort(Comparator.comparingDouble(Item::distSq).reversed());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        for (Item item : items) {
            if (item.fx instanceof Rift r && riftShader != null) drawRift(riftShader, r, camera, viewProj, inv);
            else if (item.fx instanceof Hole h && holeShader != null) drawHole(holeShader, h, camera, viewProj, inv);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        clear();
    }

    private static void color(ShaderInstance sh, String name, int rgb) {
        sh.safeGetUniform(name).set(ColorUtil.r(rgb), ColorUtil.g(rgb), ColorUtil.b(rgb));
    }

    private static void drawRift(ShaderInstance sh, Rift r, Vec3 camera, Matrix4f viewProj, Matrix4f inv) {
        Vec3 c = r.center.subtract(camera);
        float extent = Math.max(r.halfW, r.halfH) * (r.shape == SHAPE_RECT ? 1.6f : 3.4f);
        float[] b = Fullscreen.sphereBounds(viewProj, (float) c.x, (float) c.y, (float) c.z, extent);
        if (b == null) return;
        SceneCapture.capture();
        SceneCapture.bind();
        sh.safeGetUniform("InvViewProj").set(inv);
        sh.safeGetUniform("ViewProj").set(viewProj);
        sh.safeGetUniform("Center").set((float) c.x, (float) c.y, (float) c.z);
        sh.safeGetUniform("Normal").set((float) r.normal.x, (float) r.normal.y, (float) r.normal.z);
        sh.safeGetUniform("UpDir").set((float) r.up.x, (float) r.up.y, (float) r.up.z);
        sh.safeGetUniform("HalfSize").set(r.halfW, r.halfH);
        color(sh, "ColorA", r.colorA);
        color(sh, "ColorB", r.colorB);
        sh.safeGetUniform("RiftParams").set((float) r.style, r.open, (float) r.shape, r.intensity);
        sh.safeGetUniform("DestSky").set(ColorUtil.r(r.destSky), ColorUtil.g(r.destSky), ColorUtil.b(r.destSky), r.window);
        Fullscreen.drawRect(sh, b[0], b[1], b[2], b[3]);
    }

    private static void drawHole(ShaderInstance sh, Hole h, Vec3 camera, Matrix4f viewProj, Matrix4f inv) {
        Vec3 c = h.center.subtract(camera);
        float rs = h.radius * Math.max(0.05f, h.bloom);
        float[] b = Fullscreen.sphereBounds(viewProj, (float) c.x, (float) c.y, (float) c.z, rs * 13.2f);
        if (b == null) return;
        SceneCapture.capture();
        SceneCapture.bind();
        sh.safeGetUniform("InvViewProj").set(inv);
        sh.safeGetUniform("ViewProj").set(viewProj);
        sh.safeGetUniform("Center").set((float) c.x, (float) c.y, (float) c.z);
        sh.safeGetUniform("DiskNormal").set(h.diskNormal.x(), h.diskNormal.y(), h.diskNormal.z());
        color(sh, "ColorHot", h.hot);
        color(sh, "ColorCool", h.cool);
        sh.safeGetUniform("HoleParams").set(h.radius, h.bloom, (float) h.style, h.style == 2 ? 0.6f : 1.25f);
        Fullscreen.drawRect(sh, b[0], b[1], b[2], b[3]);
    }
}

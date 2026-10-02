package dev.mysticarts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Draws screen-aligned quads straight in normalised device coordinates. */
public final class Fullscreen {
    private Fullscreen() {}

    public static void draw(ShaderInstance shader) {
        drawRect(shader, -1f, -1f, 1f, 1f);
    }

    public static void drawRect(ShaderInstance shader, float x0, float y0, float x1, float y1) {
        RenderSystem.setShader(() -> shader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.addVertex(x0, y0, 0f);
        bb.addVertex(x1, y0, 0f);
        bb.addVertex(x1, y1, 0f);
        bb.addVertex(x0, y1, 0f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /**
     * Screen-space bounds (NDC) of a sphere, as {x0, y0, x1, y1}, or null when it is entirely off-screen. Returns the
     * full screen when the camera is inside or close to the sphere.
     */
    public static float[] sphereBounds(Matrix4f viewProj, float cx, float cy, float cz, float radius) {
        float distSq = cx * cx + cy * cy + cz * cz;
        if (distSq < radius * radius * 1.2f) return new float[] {-1f, -1f, 1f, 1f};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        Vector4f v = new Vector4f();
        for (int i = 0; i < 8; i++) {
            float x = cx + ((i & 1) == 0 ? -radius : radius);
            float y = cy + ((i & 2) == 0 ? -radius : radius);
            float z = cz + ((i & 4) == 0 ? -radius : radius);
            v.set(x, y, z, 1f);
            viewProj.transform(v);
            if (v.w <= 0.01f) return new float[] {-1f, -1f, 1f, 1f};
            float nx = v.x / v.w;
            float ny = v.y / v.w;
            minX = Math.min(minX, nx);
            minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx);
            maxY = Math.max(maxY, ny);
        }
        if (maxX < -1f || minX > 1f || maxY < -1f || minY > 1f) return null;
        return new float[] {Math.max(-1f, minX), Math.max(-1f, minY), Math.min(1f, maxX), Math.min(1f, maxY)};
    }
}

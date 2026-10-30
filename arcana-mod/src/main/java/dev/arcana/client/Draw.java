package dev.arcana.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.arcana.ArcanaMod;
import dev.arcana.magic.Spell;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Small GUI drawing helpers shared by the HUD and screens. */
public final class Draw {
    public static ResourceLocation icon(Spell s) {
        return ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, "textures/gui/spell/" + s.id + ".png");
    }

    public static void icon(GuiGraphics g, Spell s, int x, int y, int size) {
        g.blit(icon(s), x, y, size, size, 0, 0, 32, 32, 32, 32);
    }

    public static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /** Annular wedge between two angles (radians, 0 = up, clockwise). */
    public static void wedge(GuiGraphics g, float cx, float cy, float inner, float outer, float a0, float a1, int argbInner, int argbOuter) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(2, (int) ((a1 - a0) / 0.06f));
        for (int i = 0; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps;
            float s = (float) Math.sin(a), c = (float) -Math.cos(a);
            b.addVertex(m, cx + s * outer, cy + c * outer, 0).setColor(argbOuter);
            b.addVertex(m, cx + s * inner, cy + c * inner, 0).setColor(argbInner);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private Draw() {}
}

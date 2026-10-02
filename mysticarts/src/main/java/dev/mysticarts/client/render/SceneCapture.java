package dev.mysticarts.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Snapshot of the rendered world (colour + depth) so shaders can refract, lens and distort it. */
public final class SceneCapture {
    private static TextureTarget target;

    private SceneCapture() {}

    public static void capture() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, target.width, target.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        target.copyDepthFrom(main);
        main.bindWrite(false);
    }

    public static void bind() {
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShaderTexture(1, target.getDepthTextureId());
    }

    public static int width() {
        return Minecraft.getInstance().getMainRenderTarget().width;
    }

    public static int height() {
        return Minecraft.getInstance().getMainRenderTarget().height;
    }
}

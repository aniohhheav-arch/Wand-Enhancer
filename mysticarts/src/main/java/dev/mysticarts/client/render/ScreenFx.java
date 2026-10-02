package dev.mysticarts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Full-screen lens over the finished world image: chromatic aberration, warp, zoom blur, swirl, ripples, colour
 * grading, vignette, flash, the mirror-dimension kaleidoscope and the time-stop grade. Values are accumulated each frame
 * from active spells, then consumed by {@link #render()}.
 */
public final class ScreenFx {
    public static float aberration;
    public static float warp;
    public static float zoomBlur;
    public static float vignette;
    public static float swirl;
    public static float glitch;
    public static float wave;
    public static float grain;
    public static float flash;
    public static float saturation = 1f;
    public static float centerX = 0.5f;
    public static float centerY = 0.5f;
    public static int tint = 0xFFFFFF;
    public static float tintStrength;
    public static int flashColor = 0xFFFFFF;
    public static int vignetteColor = 0x000000;
    /** Mirror-dimension kaleidoscope, iridescent hue drift, time-stop grade and the Snap's white-out. */
    public static float kaleido;
    public static float hue;
    public static float frozen;
    public static float whiteout;

    private ScreenFx() {}

    public static void reset() {
        aberration = warp = zoomBlur = vignette = swirl = glitch = wave = grain = flash = 0f;
        kaleido = hue = frozen = whiteout = 0f;
        tintStrength = 0f;
        saturation = 1f;
        centerX = centerY = 0.5f;
        tint = 0xFFFFFF;
        flashColor = 0xFFFFFF;
        vignetteColor = 0x000000;
    }

    public static boolean active() {
        return aberration > 0.001f || Math.abs(warp) > 0.001f || zoomBlur > 0.001f || vignette > 0.001f || Math.abs(swirl) > 0.001f
                || glitch > 0.001f || wave > 0.001f || grain > 0.001f || flash > 0.001f || tintStrength > 0.001f || Math.abs(saturation - 1f) > 0.01f
                || kaleido > 0.001f || hue > 0.001f || frozen > 0.001f || whiteout > 0.001f;
    }

    public static void render() {
        ShaderInstance sh = MaShaders.screenfx;
        if (sh == null || !active()) return;
        SceneCapture.capture();
        SceneCapture.bind();
        sh.safeGetUniform("FxA").set(aberration, warp, zoomBlur, vignette);
        sh.safeGetUniform("FxB").set(swirl, glitch, wave, grain);
        sh.safeGetUniform("FxC").set(centerX, centerY, flash, saturation);
        sh.safeGetUniform("FxD").set(kaleido, hue, frozen, whiteout);
        sh.safeGetUniform("FxTint").set(ColorUtil.r(tint), ColorUtil.g(tint), ColorUtil.b(tint), tintStrength);
        sh.safeGetUniform("FlashColor").set(ColorUtil.r(flashColor), ColorUtil.g(flashColor), ColorUtil.b(flashColor));
        sh.safeGetUniform("VignetteColor").set(ColorUtil.r(vignetteColor), ColorUtil.g(vignetteColor), ColorUtil.b(vignetteColor));
        sh.safeGetUniform("ScreenSize").set((float) SceneCapture.width(), (float) SceneCapture.height());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        Fullscreen.draw(sh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}

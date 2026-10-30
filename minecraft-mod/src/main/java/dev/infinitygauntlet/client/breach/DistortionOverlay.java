package dev.infinitygauntlet.client.breach;

import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.config.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Shader-pack-safe version of the reality distortion, drawn as a GUI layer: red/cyan tear bands, corrupted
 * blocks, grain lines, exposure flickers and a vignette. Used when the post shader cannot run.
 */
public final class DistortionOverlay {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!RealityDistortion.useOverlay()) {
            return;
        }
        float quality = Math.min(1f, ClientConfig.quality().density);
        float i = RealityDistortion.intensity() * quality, s = RealityDistortion.spike() * quality;
        if (i + s < 0.02f) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        long t = (long) (RealityDistortion.clock() * 9);
        // vignette
        int edge = (int) (Mth.clamp(0.35f * i + 0.4f * s, 0, 0.7f) * 255);
        for (int k = 0; k < 6; k++) {
            int a = edge * (6 - k) / 12;
            int inset = k * Math.max(4, w / 60);
            g.fill(inset, inset, w - inset, inset + Math.max(4, w / 60), a << 24);
            g.fill(inset, h - inset - Math.max(4, w / 60), w - inset, h - inset, a << 24);
        }
        // chromatic tear bands
        int bands = (int) (2 + 10 * s + 3 * i);
        for (int k = 0; k < bands; k++) {
            if (VfxDraw.hash(t, k, 1) > 0.25f + 0.6f * s + 0.2f * i) {
                continue;
            }
            int y = (int) (VfxDraw.hash(t, k, 2) * h);
            int bh = 1 + (int) (VfxDraw.hash(t, k, 3) * (4 + 18 * s));
            int shift = (int) ((VfxDraw.hash(t, k, 4) - 0.5f) * 40 * (s + 0.3f * i));
            int alpha = (int) (60 + 90 * s);
            g.fill(Math.max(0, shift), y, w + Math.min(0, shift), y + bh, alpha << 24 | 0xFF1E3C);
            g.fill(Math.max(0, -shift), y + 1, w + Math.min(0, -shift), y + bh + 1, alpha << 24 | 0x1EE6FF);
        }
        // corrupted blocks
        if (s > 0.15f) {
            for (int k = 0; k < 14; k++) {
                if (VfxDraw.hash(t / 2, k, 5) > s * 0.9f) {
                    continue;
                }
                int bw = w / 18, bh = h / 10;
                int x = (int) (VfxDraw.hash(t / 2, k, 6) * 18) * bw, y = (int) (VfxDraw.hash(t / 2, k, 7) * 10) * bh;
                g.fill(x, y, x + bw, y + bh, 0xD0000000 | (VfxDraw.hash(t / 2, k, 8) > 0.5f ? 0x0B0B12 : 0xF4F0FF));
            }
        }
        // exposure flicker
        if (s > 0.3f && VfxDraw.hash(t, 9, 9) > 0.5f) {
            g.fill(0, 0, w, h, (int) (40 * s) << 24 | 0x400030);
        }
    }

    private DistortionOverlay() {
    }
}

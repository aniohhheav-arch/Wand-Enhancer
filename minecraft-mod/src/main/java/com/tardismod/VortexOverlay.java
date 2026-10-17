package com.tardismod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * The time vortex, drawn full-screen on the client while the TARDIS is in flight: a deep-indigo gradient, rotating
 * square "tunnel" rings rushing toward the viewer, radial speed streaks and the occasional lightning flash.
 * (A 2D overlay built from GUI primitives — not a world-space 3D shader.)
 */
public final class VortexOverlay implements HudElement {
    private static long start; private static int totalMs;

    public static void trigger(int ticks) { start = System.currentTimeMillis(); totalMs = ticks * 50; }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (totalMs <= 0) return;
        long el = System.currentTimeMillis() - start;
        if (el > totalMs + 900) { totalMs = 0; return; }
        float t = el / 1000f;
        float fade = Math.min(1f, el / 800f) * (el > totalMs ? Math.max(0f, 1f - (el - totalMs) / 900f) : 1f);
        int a = (int) (235 * fade), w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        float reach = (float) Math.hypot(w, h) * 0.75f;
        g.fillGradient(0, 0, w, h, argb(a, 6, 2, 38), argb(a, 18, 46, 150));
        Matrix3x2fStack pose = g.pose();
        final int rings = 14;
        for (int i = 0; i < rings; i++) {
            float p = ((i + t * 1.5f) % rings) / rings;                 // 0 = far, 1 = at the camera
            int rr = (int) (Math.pow(p, 2.3) * reach) + 3, th = Math.max(1, (int) (rr * 0.035f));
            float glow = (float) Math.sin(p * Math.PI);
            int col = argb((int) (a * glow * 0.85f), 90 + (int) (120 * p), 150 + (int) (90 * p), 255);
            pose.pushMatrix(); pose.translate(cx, cy); pose.rotate(t * 0.3f * (i % 2 == 0 ? 1 : -1) + p * 1.4f);
            g.fill(-rr, -rr, rr, -rr + th, col); g.fill(-rr, rr - th, rr, rr, col); g.fill(-rr, -rr, -rr + th, rr, col); g.fill(rr - th, -rr, rr, rr, col);
            pose.popMatrix();
        }
        for (int k = 0; k < 56; k++) {
            float d = (k * 0.381966f + t * (0.9f + (k % 5) * 0.12f)) % 1f, r0 = d * d * reach, len = 4 + r0 * 0.3f;
            int col = argb((int) (a * Math.sin(d * Math.PI) * 0.8f), 170, 220, 255);
            pose.pushMatrix(); pose.translate(cx, cy); pose.rotate(k * 6.2832f / 56f + t * 0.15f);
            g.fill((int) r0, -1, (int) (r0 + len), 1, col);
            pose.popMatrix();
        }
        if ((int) (t * 4) % 13 == 0 && el < totalMs) g.fill(0, 0, w, h, argb((int) (60 * fade), 200, 225, 255)); // lightning flicker
    }
}

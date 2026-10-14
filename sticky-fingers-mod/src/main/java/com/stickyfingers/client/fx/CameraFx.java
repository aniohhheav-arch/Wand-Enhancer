package com.stickyfingers.client.fx;

import com.mojang.math.Axis;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.network.ScreenFxPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * All client-side cinematic effects: camera shake, FOV punches, the zipper "screen unzip" transition,
 * flashes, slow-motion letterboxing, speed lines and vignettes.
 */
public final class CameraFx {
    private static final RandomSource RNG = RandomSource.create();
    private static final float[] intensity = new float[ScreenFx.VALUES.length];
    private static final int[] duration = new int[ScreenFx.VALUES.length];
    private static final int[] remaining = new int[ScreenFx.VALUES.length];
    private static float shakeYaw, shakePitch, shakeRoll;

    public static void trigger(ScreenFxPayload p) {
        if (p.fx() < 0 || p.fx() >= ScreenFx.VALUES.length) return;
        int i = p.fx();
        // stronger effects override weaker ones, equal ones refresh
        if (remaining[i] <= 0 || p.intensity() >= intensity[i] * progress(i)) {
            intensity[i] = p.intensity();
            duration[i] = Math.max(1, p.duration());
            remaining[i] = duration[i];
        }
    }

    public static void tick() {
        for (int i = 0; i < remaining.length; i++) if (remaining[i] > 0) remaining[i]--;
        float shake = active(ScreenFx.SHAKE) ? intensity[ScreenFx.SHAKE.ordinal()] * progress(ScreenFx.SHAKE.ordinal()) : 0;
        shake *= (float) (double) SFConfig.safe(SFConfig.CAMERA_SHAKE::get, 1.0);
        shakeYaw = (RNG.nextFloat() - 0.5f) * 3f * shake;
        shakePitch = (RNG.nextFloat() - 0.5f) * 3f * shake;
        shakeRoll = (RNG.nextFloat() - 0.5f) * 4f * shake;
    }

    private static boolean active(ScreenFx fx) {
        return remaining[fx.ordinal()] > 0;
    }

    /** 1 at trigger time, 0 when finished. */
    private static float progress(int i) {
        return duration[i] == 0 ? 0 : remaining[i] / (float) duration[i];
    }

    private static float progress(ScreenFx fx, float partial) {
        int i = fx.ordinal();
        return duration[i] == 0 ? 0 : Mth.clamp((remaining[i] - partial) / duration[i], 0, 1);
    }

    public static void applyAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!active(ScreenFx.SHAKE)) return;
        e.setYaw(e.getYaw() + shakeYaw);
        e.setPitch(e.getPitch() + shakePitch);
        e.setRoll(e.getRoll() + shakeRoll);
    }

    public static float fovMultiplier(float partial) {
        float m = 1f;
        if (active(ScreenFx.FOV_PUNCH)) {
            float p = progress(ScreenFx.FOV_PUNCH, partial);
            // quick zoom-out then settle back
            m *= 1f + intensity[ScreenFx.FOV_PUNCH.ordinal()] * 0.12f * Mth.sin(p * Mth.PI);
        }
        if (active(ScreenFx.SLOWMO)) {
            float p = progress(ScreenFx.SLOWMO, partial);
            m *= 1f - 0.18f * Mth.sin(p * Mth.PI);
        }
        if (active(ScreenFx.SPEED_LINES)) m *= 1.05f;
        return m;
    }

    // ------------------------------------------------------------------ overlay

    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        if (!SFConfig.safe(SFConfig.SCREEN_EFFECTS::get, true)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();

        if (active(ScreenFx.VIGNETTE)) {
            float a = Math.min(1f, progress(ScreenFx.VIGNETTE, partial) * 4f) * intensity[ScreenFx.VIGNETTE.ordinal()];
            vignette(g, w, h, a * 0.75f, 0x1B0B3A);
        }
        if (active(ScreenFx.SPEED_LINES)) {
            float a = intensity[ScreenFx.SPEED_LINES.ordinal()] * Math.min(1f, progress(ScreenFx.SPEED_LINES, partial) * 3f);
            speedLines(g, w, h, a, mc.player.tickCount + partial);
        }
        if (active(ScreenFx.SLOWMO)) {
            float p = progress(ScreenFx.SLOWMO, partial);
            float a = Mth.sin(p * Mth.PI);
            int bar = (int) (h * 0.11f * Math.min(1f, a * 2f));
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            g.fill(0, bar, w, h - bar, argb(a * 0.28f, 0x3A5BD6));
            vignette(g, w, h, a * 0.9f, 0x0A0420);
        }
        if (active(ScreenFx.ZIP_TRANSITION)) {
            zipTransition(g, w, h, 1f - progress(ScreenFx.ZIP_TRANSITION, partial));
        }
        if (active(ScreenFx.FLASH)) {
            float p = progress(ScreenFx.FLASH, partial);
            g.fill(0, 0, w, h, argb(p * p * intensity[ScreenFx.FLASH.ordinal()], 0xE8F4FF));
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void vignette(GuiGraphics g, int w, int h, float alpha, int rgb) {
        int edge = Math.max(w, h) / 4;
        int solid = argb(alpha, rgb);
        int clear = argb(0, rgb);
        g.fillGradient(0, 0, w, edge, solid, clear);
        g.fillGradient(0, h - edge, w, h, clear, solid);
        // horizontal gradients: draw rotated vertical gradients
        g.pose().pushPose();
        g.pose().translate(0, h, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
        g.fillGradient(0, 0, h, edge / 2, solid, clear);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w, 0, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        g.fillGradient(0, 0, h, edge / 2, solid, clear);
        g.pose().popPose();
    }

    private static void speedLines(GuiGraphics g, int w, int h, float alpha, float time) {
        RandomSource r = RandomSource.create((long) (time * 0.5f));
        int cx = w / 2, cy = h / 2;
        float maxR = (float) Math.hypot(w, h) / 2f;
        for (int i = 0; i < 48; i++) {
            float ang = r.nextFloat() * 360f;
            float inner = maxR * (0.45f + r.nextFloat() * 0.3f);
            float len = maxR * (0.15f + r.nextFloat() * 0.4f);
            int col = r.nextInt(3) == 0 ? 0xE8A93B : (r.nextBoolean() ? 0xFFFFFF : 0x8FD3FF);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
            g.fill((int) inner, 0, (int) (inner + len), 1 + r.nextInt(2), argb(alpha * (0.25f + r.nextFloat() * 0.4f), col));
            g.pose().popPose();
        }
    }

    /** The screen itself is zipped shut and unzipped again, with golden teeth along the seam. */
    private static void zipTransition(GuiGraphics g, int w, int h, float t) {
        float closed = Mth.sin(Mth.clamp(t, 0, 1) * Mth.PI);
        closed = Math.min(1f, closed * 1.25f);
        int half = (int) (h / 2f * closed);
        if (half <= 0) return;
        int dark = 0xF0120A26;
        g.fill(0, 0, w, half, dark);
        g.fill(0, h - half, w, h, dark);
        // tape
        int tape = Math.max(2, h / 60);
        g.fill(0, half - tape, w, half, 0xFF2B3FA8);
        g.fill(0, h - half, w, h - half + tape, 0xFF2B3FA8);
        // interlocking teeth + slider following the closing direction
        int tooth = Math.max(3, h / 70);
        int slider = (int) (w * Mth.clamp(t * 1.4f, 0, 1));
        for (int x = 0; x < w; x += tooth * 2) {
            int col = x < slider ? 0xFFE8A93B : 0xFFB57A1E;
            g.fill(x, half - 1, x + tooth, half + tooth - 1, col);
            g.fill(x + tooth, h - half - tooth + 1, x + tooth * 2, h - half + 1, col);
        }
        if (closed > 0.98f) {
            int sy = h / 2;
            g.fill(slider - tooth * 2, sy - tooth * 2, slider + tooth * 2, sy + tooth * 2, 0xFFDDE3F0);
            g.fill(slider - tooth, sy + tooth * 2, slider + tooth, sy + tooth * 5, 0xFFAEB7CF);
        }
    }

    private CameraFx() {}
}

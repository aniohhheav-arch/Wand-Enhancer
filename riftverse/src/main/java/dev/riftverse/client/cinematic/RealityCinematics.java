package dev.riftverse.client.cinematic;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Plays the reality cinematics sent by the server: a sweeping orbit around the focus point, letterboxing, a colour wash
 * specific to each sequence (dissolving static for erasure, scan lines for reconstruction...) and camera shake. It runs
 * alongside the travel director and yields to it whenever a journey is in progress.
 */
public final class RealityCinematics {
    private static final int BLEND = 22;

    @Nullable
    private static CinematicType type;
    private static int tick;
    private static int duration;
    private static Vec3 focus = Vec3.ZERO;
    private static int colorA = 0xFFFFFF;
    private static int colorB = 0xFFFFFF;
    private static float startYaw;
    private static long noiseSeed;

    private RealityCinematics() {}

    public static void play(int typeId, int ticks, Vec3 focusPos, int a, int b, String title, String subtitle) {
        CinematicType t = CinematicType.byOrdinal(typeId);
        type = t;
        tick = 0;
        duration = Math.max(10, ticks > 0 ? ticks : t.defaultTicks);
        focus = focusPos;
        colorA = a;
        colorB = b;
        LocalPlayer player = Minecraft.getInstance().player;
        startYaw = player == null ? 0f : player.getYRot();
        noiseSeed = System.nanoTime();
        if (!title.isEmpty()) CinematicDirector.announce(title, subtitle, a);
        if (t.shake > 0) CinematicDirector.shake(t.shake, Math.min(duration, 40), t == CinematicType.ERASURE ? 0f : 0.35f, b);
    }

    public static void stop() {
        type = null;
        tick = 0;
    }

    public static boolean active() {
        return type != null;
    }

    @Nullable
    public static CinematicType current() {
        return type;
    }

    private static float t(float partial) {
        return tick + partial;
    }

    /** 0..1 in, hold, 0..1 out. */
    private static float envelope(float partial) {
        if (type == null) return 0f;
        float t = t(partial);
        float in = CameraRig.easeInOut(t / BLEND);
        float out = 1f - CameraRig.easeInOut((t - (duration - BLEND)) / BLEND);
        return Mth.clamp(Math.min(in, out), 0f, 1f);
    }

    private static float progress(float partial) {
        return type == null ? 0f : Mth.clamp(t(partial) / duration, 0f, 1f);
    }

    /** 0..1 how strongly a nearby inhaling Cosmic Deity is warping this player's view. */
    private static float deityPull;
    private static int deityTicks;

    private static void sampleDeity() {
        Minecraft mc = Minecraft.getInstance();
        float best = 0f;
        if (mc.level != null && mc.player != null) {
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof dev.riftverse.entity.boss.CosmicDeityEntity deity)) continue;
                double d = deity.mouth().distanceTo(mc.player.getEyePosition());
                float k = deity.inhale() * (float) Math.max(0, 1 - d / 110);
                best = Math.max(best, k);
            }
        }
        deityPull = deityPull + (best - deityPull) * 0.2f;
        if (deityPull > 0.05f) {
            deityTicks++;
            if (deityTicks % 6 == 0) CinematicDirector.shake(0.15f + 0.6f * deityPull, 8, 0f, 0xC070FF);
        } else {
            deityTicks = 0;
        }
    }

    public static void tick() {
        sampleDeity();
        if (type == null) return;
        tick++;
        CinematicType t = type;
        if (t == CinematicType.COLLAPSE || t == CinematicType.ERASURE) {
            if (tick % 20 == 0) CinematicDirector.shake(t.shake * (0.5f + progress(0f)), 14, 0f, colorA);
        } else if (t == CinematicType.CONVERGENCE && tick % 30 == 0) {
            CinematicDirector.shake(0.2f, 10, 0.25f, (tick / 30) % 2 == 0 ? colorA : colorB);
        }
        if (t == CinematicType.ERASURE && tick == duration - 12) CinematicDirector.shake(1.0f, 20, 1.0f, 0xFFFFFF);
        if ((t == CinematicType.REBUILD || t == CinematicType.RESTORE || t == CinematicType.BIRTH) && tick == duration / 2) {
            CinematicDirector.shake(0.4f, 16, 0.85f, colorB);
        }
        if (tick >= duration) stop();
    }

    private static boolean cameraEnabled() {
        return RiftverseConfig.get(RiftverseConfig.CINEMATIC_CAMERA, true);
    }

    /** Sets the camera rig for this frame. Returns false if this cinematic does not drive the camera. */
    public static boolean updateRig(float partial) {
        CinematicType t = type;
        if (t == null || !t.camera || !cameraEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        Vec3 eye = player.getEyePosition(partial);
        float p = progress(partial);
        float k = envelope(partial);
        double angle = Math.toRadians(startYaw + 90.0) + p * Math.PI * (t == CinematicType.ERASURE ? 0.9 : 1.3);
        float radius = t.orbitRadius * (t == CinematicType.REBUILD ? 1.3f - 0.5f * p : 0.8f + 0.4f * p);
        float height = t == CinematicType.REBUILD || t == CinematicType.RESTORE ? t.rise * (1.2f - p) + 3f : 2f + t.rise * CameraRig.easeInOut(p);
        Vec3 target = focus;
        Vec3 shot = target.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
        switch (t) {
            // each End Protocol has its own camera language
            case PROTOCOL_ORBITAL -> {
                // satellite view from above the platform, then a low ground shot looking up at the barrage
                Vec3 ground = eye.add(Math.cos(angle) * 9, 0.5, Math.sin(angle) * 9);
                Vec3 sky = focus.add(Math.cos(angle) * 6, 22, Math.sin(angle) * 6);
                shot = p < 0.35f ? sky : ground;
                target = p < 0.35f ? eye : focus;
            }
            case PROTOCOL_SINGULARITY -> {
                double spiral = radius * (1.1 - 0.75 * p);
                shot = focus.add(Math.cos(angle * 2) * spiral, -6 + 4 * Math.sin(p * Math.PI), Math.sin(angle * 2) * spiral);
            }
            case PROTOCOL_DEVOURER -> {
                shot = eye.add(Math.cos(angle * 0.5) * 7, -0.8, Math.sin(angle * 0.5) * 7);
            }
            case PROTOCOL_DISASSEMBLY -> {
                shot = eye.add(Math.cos(angle * 0.4) * (6 + 10 * p), 3 + 20 * CameraRig.easeInOut(p), Math.sin(angle * 0.4) * (6 + 10 * p));
                target = eye;
            }
            case PROTOCOL_BLACK_HOLE -> {
                double fast = angle * (1.5 + 2.5 * p);
                shot = focus.add(Math.cos(fast) * radius * (1.2 - 0.6 * p), 4, Math.sin(fast) * radius * (1.2 - 0.6 * p));
            }
            case PROTOCOL_TIMELINE -> {
                double back = Math.toRadians(startYaw + 90.0) - p * Math.PI * 2.2;
                shot = eye.add(Math.cos(back) * (4 + 12 * p), 2 + 8 * p, Math.sin(back) * (4 + 12 * p));
                target = eye;
            }
            default -> {}
        }
        Vec3 pos = eye.lerp(shot, k);
        Vec3 look = target.lerp(eye.add(player.getLookAngle()), 1f - k);
        float yaw = CameraRig.lerpAngle(k, player.getViewYRot(partial), CameraRig.yawTo(pos, look));
        float pitch = Mth.lerp(k, player.getViewXRot(partial), CameraRig.pitchTo(pos, look));
        float roll = t == CinematicType.PROTOCOL_BLACK_HOLE || t == CinematicType.PROTOCOL_SINGULARITY ? (float) Math.sin(t(partial) * 0.05) * 18f * k * p
                : t == CinematicType.ANOMALY || t == CinematicType.CONVERGENCE ? (float) Math.sin(t(partial) * 0.07) * 10f * k : 0f;
        CameraRig.set(new CameraRig.Pose(pos, yaw, pitch, roll, k > 0.1f));
        return true;
    }

    public static boolean locksInput() {
        CinematicType t = type;
        return t != null && t.camera && cameraEnabled();
    }

    public static float letterbox(float partial) {
        CinematicType t = type;
        return t != null && t.letterbox ? envelope(partial) : 0f;
    }

    public static float fovOffset(float partial) {
        float warp = deityPull > 0.01f ? deityPull * (24f + 10f * (float) Math.sin((deityTicks + partial) * 0.35)) : 0f;
        CinematicType t = type;
        if (t == null) return warp;
        float k = envelope(partial);
        return switch (t) {
            case ERASURE -> 22f * k * progress(partial);
            case PROTOCOL_SINGULARITY, PROTOCOL_BLACK_HOLE -> 30f * k * CameraRig.easeIn(progress(partial));
            case PROTOCOL_DEVOURER -> 14f * k;
            case PROTOCOL_ORBITAL -> -10f * k;
            case COLLAPSE, SINGULARITY -> 12f * k;
            case REBUILD, RESTORE, BIRTH -> -8f * k;
            default -> 0f;
        };
    }

    /** Full-screen colour work drawn under the letterbox and titles. */
    public static void renderOverlay(GuiGraphics g, float partial) {
        if (deityPull > 0.02f) {
            // the world is being drawn into a mouth: the edges darken, rings rush inward, colour bleeds violet
            int w = g.guiWidth();
            int h = g.guiHeight();
            float time = deityTicks + partial;
            vignette(g, w, h, 0x0A0018, deityPull * 0.75f);
            tunnelRings(g, w, h, time * 3f, 0xC070FF, deityPull, 0.4f);
            wash(g, w, h, 0x6020A0, deityPull * 0.18f * (0.6f + 0.4f * (float) Math.sin(time * 0.4)));
            if (RiftverseConfig.get(RiftverseConfig.HEAVY_EFFECTS, true)) staticNoise(g, w, h, time, deityPull * 0.5f, deityPull);
        }
        CinematicType t = type;
        if (t == null) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float k = envelope(partial);
        float p = progress(partial);
        float time = t(partial);
        boolean heavy = RiftverseConfig.get(RiftverseConfig.HEAVY_EFFECTS, true);
        switch (t) {
            case ERASURE -> {
                // the world drains to black from the edges while static eats the picture
                float drain = k * (0.15f + 0.75f * CameraRig.easeIn(p));
                vignette(g, w, h, 0x000000, drain);
                wash(g, w, h, colorA, t.wash * k * (0.4f + 0.6f * (float) Math.abs(Math.sin(time * 0.21))));
                if (heavy) staticNoise(g, w, h, time, 0.25f + 0.75f * p, k);
            }
            case REBUILD, RESTORE -> {
                vignette(g, w, h, colorB, k * 0.35f);
                wash(g, w, h, colorA, t.wash * k * (1f - p));
                scanLines(g, w, h, time, colorB, k, heavy ? 6 : 2);
            }
            case SCAN -> scanLines(g, w, h, time * 3f, colorA, k, 1);
            case COLLAPSE -> {
                wash(g, w, h, colorA, t.wash * k * (0.5f + 0.5f * (float) Math.sin(time * 0.6)));
                vignette(g, w, h, 0x200008, k * 0.6f);
                if (heavy) staticNoise(g, w, h, time, 0.35f, k);
            }
            case CONVERGENCE -> {
                int c = ColorUtil.lerp(colorA, colorB, 0.5f + 0.5f * (float) Math.sin(time * 0.08));
                wash(g, w, h, c, t.wash * k * (0.6f + 0.4f * (float) Math.sin(time * 0.17)));
                vignette(g, w, h, c, k * 0.4f);
            }
            case ANNOUNCE -> {}
            case PROTOCOL_ORBITAL -> {
                // targeting HUD: reticle, bracket boxes and a lock-on readout, then the barrage whiteout
                reticle(g, w, h, time, colorA, k);
                if (p > 0.35f) wash(g, w, h, colorB, k * 0.25f * (float) Math.max(0, Math.sin(time * 0.9)));
                if (p > 0.78f) wash(g, w, h, 0xFFFFFF, CameraRig.easeIn((p - 0.78f) / 0.07f) * k);
            }
            case PROTOCOL_SINGULARITY -> {
                // the picture is dragged toward a single point, darkening from the edges inward
                vignette(g, w, h, 0x000000, k * (0.3f + 0.65f * p));
                tunnelRings(g, w, h, time, colorA, k, 1f - p);
                if (p > 0.8f) wash(g, w, h, 0xFFFFFF, CameraRig.easeIn((p - 0.8f) / 0.05f) * k);
            }
            case PROTOCOL_DEVOURER -> {
                int c = ColorUtil.lerp(colorA, colorB, 0.5f + 0.5f * (float) Math.sin(time * 0.05));
                wash(g, w, h, c, k * 0.18f);
                vignette(g, w, h, 0x10001A, k * (0.4f + 0.4f * p));
                tunnelRings(g, w, h, -time, c, k * 0.8f, 0.5f);
                if (p > 0.8f) wash(g, w, h, colorB, CameraRig.easeIn((p - 0.8f) / 0.05f) * k);
            }
            case PROTOCOL_DISASSEMBLY -> {
                grid(g, w, h, time, colorA, k * (0.25f + 0.6f * p));
                if (heavy) staticNoise(g, w, h, time, 0.2f + 0.6f * p, k);
                if (p > 0.8f) wash(g, w, h, 0x000000, CameraRig.easeIn((p - 0.8f) / 0.1f) * k);
            }
            case PROTOCOL_BLACK_HOLE -> {
                vignette(g, w, h, colorA, k * (0.2f + 0.3f * p));
                vignette(g, w, h, 0x000000, k * (0.2f + 0.6f * p));
                tunnelRings(g, w, h, time * (1 + 3 * p), colorA, k, 0.7f);
                if (p > 0.85f) wash(g, w, h, 0xFFF0C8, CameraRig.easeIn((p - 0.85f) / 0.05f) * k);
            }
            case PROTOCOL_TIMELINE -> {
                if (p < 0.5f) {
                    // rewinding eras: sepia, frost, verdant, dawn washes flicker past with rewind streaks
                    int[] eras = {0xC27B4A, 0xA8E8FF, 0x5CFF9D, 0xFFC24D, 0x8F6BFF, 0xFF5A1F};
                    int era = eras[(int) (time / 20f) % eras.length];
                    wash(g, w, h, era, k * 0.28f);
                    rewindStreaks(g, w, h, time, k);
                } else {
                    wash(g, w, h, 0x001018, k * 0.55f * Math.min(1f, (p - 0.5f) * 4f));
                    grid(g, w, h, time * 0.3f, colorA, k * 0.7f);
                    if (p > 0.78f) wash(g, w, h, 0xFFFFFF, CameraRig.easeIn((p - 0.78f) / 0.1f) * k);
                }
            }
            default -> {
                wash(g, w, h, colorA, t.wash * k);
                vignette(g, w, h, 0x000000, k * 0.45f);
            }
        }
    }

    private static void wash(GuiGraphics g, int w, int h, int color, float alpha) {
        if (alpha <= 0.004f) return;
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
        g.fill(0, 0, w, h, (a << 24) | (color & 0xFFFFFF));
    }

    private static void vignette(GuiGraphics g, int w, int h, int color, float strength) {
        if (strength <= 0.004f) return;
        int a = (int) (Mth.clamp(strength, 0f, 1f) * 255);
        int solid = (a << 24) | (color & 0xFFFFFF);
        int clear = color & 0xFFFFFF;
        int band = (int) (h * 0.32f);
        g.fillGradient(0, 0, w, band, solid, clear);
        g.fillGradient(0, h - band, w, h, clear, solid);
        int side = (int) (w * 0.18f);
        for (int i = 0; i < side; i += 4) {
            float f = 1f - i / (float) side;
            int ai = (int) (a * f * f);
            g.fill(i, 0, i + 4, h, (ai << 24) | (color & 0xFFFFFF));
            g.fill(w - i - 4, 0, w - i, h, (ai << 24) | (color & 0xFFFFFF));
        }
    }

    private static void staticNoise(GuiGraphics g, int w, int h, float time, float density, float k) {
        int frame = (int) time;
        int lines = (int) (40 * density);
        for (int i = 0; i < lines; i++) {
            long r = mix(noiseSeed + frame * 7919L + i * 104729L);
            int y = (int) Long.remainderUnsigned(r, Math.max(1, h));
            int x = (int) Long.remainderUnsigned(r >>> 17, Math.max(1, w));
            int len = 8 + (int) Long.remainderUnsigned(r >>> 33, Math.max(9, w / 3));
            int thick = 1 + (int) Long.remainderUnsigned(r >>> 45, 3);
            int a = (int) (k * (60 + Long.remainderUnsigned(r >>> 51, 140)));
            int c = (r & 1) == 0 ? 0xFFFFFF : (colorA & 0xFFFFFF);
            g.fill(x, y, Math.min(w, x + len), y + thick, (Math.min(255, a) << 24) | c);
        }
    }

    private static void scanLines(GuiGraphics g, int w, int h, float time, int color, float k, int count) {
        if (k <= 0.01f) return;
        for (int i = 0; i < count; i++) {
            float phase = ((time * 0.012f) + i / (float) count) % 1f;
            int y = (int) (phase * h);
            int a = (int) (k * 150);
            g.fill(0, y, w, y + 2, (a << 24) | (color & 0xFFFFFF));
            g.fillGradient(0, Math.max(0, y - 24), w, y, color & 0xFFFFFF, ((a / 3) << 24) | (color & 0xFFFFFF));
        }
    }

    private static void reticle(GuiGraphics g, int w, int h, float time, int color, float k) {
        int a = (int) (k * 200);
        int c = (a << 24) | (color & 0xFFFFFF);
        int cx = w / 2;
        int cy = h / 2;
        int r = (int) (40 + 8 * Math.sin(time * 0.2));
        g.fill(cx - r, cy - 1, cx - 8, cy + 1, c);
        g.fill(cx + 8, cy - 1, cx + r, cy + 1, c);
        g.fill(cx - 1, cy - r, cx + 1, cy - 8, c);
        g.fill(cx - 1, cy + 8, cx + 1, cy + r, c);
        int b = r + 14;
        int l = 12;
        g.fill(cx - b, cy - b, cx - b + l, cy - b + 2, c);
        g.fill(cx - b, cy - b, cx - b + 2, cy - b + l, c);
        g.fill(cx + b - l, cy - b, cx + b, cy - b + 2, c);
        g.fill(cx + b - 2, cy - b, cx + b, cy - b + l, c);
        g.fill(cx - b, cy + b - 2, cx - b + l, cy + b, c);
        g.fill(cx - b, cy + b - l, cx - b + 2, cy + b, c);
        g.fill(cx + b - l, cy + b - 2, cx + b, cy + b, c);
        g.fill(cx + b - 2, cy + b - l, cx + b, cy + b, c);
        for (int i = 0; i < 4; i++) {
            long rr = mix(noiseSeed + (long) (time / 6) * 31 + i);
            int x = (int) Long.remainderUnsigned(rr, Math.max(1, w - 40)) + 20;
            int y = (int) Long.remainderUnsigned(rr >>> 20, Math.max(1, h - 40)) + 20;
            g.fill(x - 6, y - 6, x + 6, y - 5, c);
            g.fill(x - 6, y + 5, x + 6, y + 6, c);
            g.fill(x - 6, y - 6, x - 5, y + 6, c);
            g.fill(x + 5, y - 6, x + 6, y + 6, c);
        }
    }

    /** Concentric rectangles shrinking toward the centre, like the picture falling inward. */
    private static void tunnelRings(GuiGraphics g, int w, int h, float time, int color, float k, float spread) {
        int cx = w / 2;
        int cy = h / 2;
        for (int i = 0; i < 6; i++) {
            float f = ((time * 0.02f) + i / 6f) % 1f;
            float s = 1f - f;
            int rw = (int) (w * 0.5f * s * (0.3f + spread));
            int rh = (int) (h * 0.5f * s * (0.3f + spread));
            int a = (int) (k * 90 * f);
            int c = (a << 24) | (color & 0xFFFFFF);
            g.fill(cx - rw, cy - rh, cx + rw, cy - rh + 1, c);
            g.fill(cx - rw, cy + rh - 1, cx + rw, cy + rh, c);
            g.fill(cx - rw, cy - rh, cx - rw + 1, cy + rh, c);
            g.fill(cx + rw - 1, cy - rh, cx + rw, cy + rh, c);
        }
    }

    private static void grid(GuiGraphics g, int w, int h, float time, int color, float k) {
        if (k <= 0.01f) return;
        int a = (int) (Mth.clamp(k, 0f, 1f) * 110);
        int c = (a << 24) | (color & 0xFFFFFF);
        int step = 24;
        int off = (int) (time * 0.6f) % step;
        for (int x = -off; x < w; x += step) g.fill(x, 0, x + 1, h, c);
        for (int y = off; y < h; y += step) g.fill(0, y, w, y + 1, c);
    }

    private static void rewindStreaks(GuiGraphics g, int w, int h, float time, float k) {
        for (int i = 0; i < 10; i++) {
            long r = mix(noiseSeed + i * 977L);
            int y = (int) Long.remainderUnsigned(r, Math.max(1, h));
            float speed = 4f + (r >>> 40 & 7);
            int x = w - (int) ((time * speed + Long.remainderUnsigned(r >>> 8, Math.max(1, w))) % (w + 120));
            int a = (int) (k * 120);
            g.fill(x, y, x + 120, y + 1, (a << 24) | 0xFFFFFF);
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}

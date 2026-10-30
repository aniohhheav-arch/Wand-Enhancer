package dev.infinitygauntlet.client.vfx;

import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.network.CameraPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Cinematic camera state: trauma-based shake, eased FOV pushes, screen flashes and the dimensional travel overlay.
 * All of it respects the client config (camera effects, shake intensity, flashes, transition overlay).
 */
public final class CameraEffects {
    private static float trauma;
    private static float zoomTarget = 1f;
    private static int zoomTicks;
    private static int zoomAge;
    private static int flashColor;
    private static float flashAlpha;
    private static int flashTicks;
    private static int flashAge;
    private static int transitionTicks;
    private static int transitionAge;
    private static int transitionColor;
    private static long time;

    public static void apply(CameraPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double dist = mc.player.getEyePosition().distanceTo(p.origin());
        float falloff = (float) Mth.clamp(1 - dist / Math.max(1, p.radius()), 0, 1);
        if (falloff <= 0) {
            return;
        }
        if (p.shake() > 0) {
            trauma = Math.min(1.5f, trauma + p.shake() * falloff);
        }
        if (p.zoomTicks() > 0 && p.zoom() > 0) {
            zoomTarget = p.zoom();
            zoomTicks = p.zoomTicks();
            zoomAge = 0;
        }
        if (p.flashTicks() > 0) {
            flash(p.flashColor(), p.flashAlpha() * falloff, p.flashTicks());
        }
    }

    public static void flash(int color, float alpha, int ticks) {
        flashColor = color;
        flashAlpha = alpha;
        flashTicks = ticks;
        flashAge = 0;
    }

    public static void transition(int ticks, int color) {
        transitionTicks = ticks;
        transitionAge = 0;
        transitionColor = color;
        trauma = Math.min(1.5f, trauma + 0.4f);
    }

    public static void tick() {
        time++;
        trauma = Math.max(0, trauma - 0.045f);
        if (zoomTicks > 0 && ++zoomAge > zoomTicks) {
            zoomTicks = 0;
        }
        if (flashTicks > 0 && ++flashAge > flashTicks) {
            flashTicks = 0;
        }
        if (transitionTicks > 0 && ++transitionAge > transitionTicks) {
            transitionTicks = 0;
        }
    }

    public static void reset() {
        trauma = 0;
        zoomTicks = 0;
        flashTicks = 0;
        transitionTicks = 0;
    }

    private static boolean cameraEnabled() {
        return ClientConfig.cameraEffects();
    }

    /** Returns (yaw, pitch, roll) offsets in degrees. */
    public static Vec3 shakeAngles(float partialTick) {
        if (!cameraEnabled() || trauma <= 0) {
            return Vec3.ZERO;
        }
        float intensity = ClientConfig.SPEC.isLoaded() ? ClientConfig.CAMERA_SHAKE.get().floatValue() : 1f;
        float s = trauma * trauma * intensity;
        float t = time + partialTick;
        return new Vec3(
                s * 4.5f * (Mth.sin(t * 1.9f) * 0.6f + Mth.sin(t * 4.3f + 1) * 0.4f),
                s * 3.5f * (Mth.sin(t * 2.3f + 2) * 0.6f + Mth.sin(t * 5.1f) * 0.4f),
                s * 3.0f * Mth.sin(t * 1.4f + 4));
    }

    public static float fovMultiplier(float partialTick) {
        if (!cameraEnabled() || zoomTicks <= 0) {
            return 1f;
        }
        float t = (zoomAge + partialTick) / zoomTicks;
        float envelope = VfxDraw.smooth(t / 0.25f) * (1 - VfxDraw.smooth((t - 0.75f) / 0.25f));
        return Mth.lerp(envelope, 1f, zoomTarget);
    }

    /** ARGB of the full-screen flash this frame (0 when none). */
    public static int flashArgb(float partialTick) {
        boolean enabled = !ClientConfig.SPEC.isLoaded() || ClientConfig.SCREEN_FLASHES.get();
        if (!enabled || flashTicks <= 0) {
            return 0;
        }
        float t = (flashAge + partialTick) / flashTicks;
        float a = flashAlpha * (1 - t) * (1 - t);
        return (int) (Mth.clamp(a, 0, 1) * 255) << 24 | flashColor & 0xFFFFFF;
    }

    /** 0..1 progress of the travel overlay, or -1 when inactive. */
    public static float transitionProgress(float partialTick) {
        boolean enabled = !ClientConfig.SPEC.isLoaded() || ClientConfig.PORTAL_TRANSITION.get();
        if (!enabled || transitionTicks <= 0) {
            return -1;
        }
        return Mth.clamp((transitionAge + partialTick) / transitionTicks, 0, 1);
    }

    public static int transitionColor() {
        return transitionColor;
    }

    private CameraEffects() {
    }
}

package dev.finalframe.finisher;

import net.minecraft.util.Mth;

/** Easing curves shared by server choreography and the client cinematic engine. */
public final class Ease {
    private Ease() {
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    /** Normalized progress of {@code t} through [a, b], clamped. */
    public static float range(float t, float a, float b) {
        return b <= a ? (t >= b ? 1 : 0) : clamp01((t - a) / (b - a));
    }

    public static float inOutSine(float t) {
        return -(Mth.cos((float) Math.PI * clamp01(t)) - 1) / 2;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float outQuad(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t);
    }

    public static float inQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    /** 0 → 1 → 0 bump peaking at the middle of [a, b]. */
    public static float bump(float t, float a, float b) {
        float p = range(t, a, b);
        return Mth.sin((float) Math.PI * p);
    }
}

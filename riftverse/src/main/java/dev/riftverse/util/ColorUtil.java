package dev.riftverse.util;

public final class ColorUtil {
    private ColorUtil() {}

    public static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int rgb(float r, float g, float b) {
        return rgb(Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static float r(int c) {
        return ((c >> 16) & 0xFF) / 255f;
    }

    public static float g(int c) {
        return ((c >> 8) & 0xFF) / 255f;
    }

    public static float b(int c) {
        return (c & 0xFF) / 255f;
    }

    public static int lerp(int a, int b, float t) {
        return rgb(r(a) + (r(b) - r(a)) * t, g(a) + (g(b) - g(a)) * t, b(a) + (b(b) - b(a)) * t);
    }

    public static int scale(int c, float s) {
        return rgb(r(c) * s, g(c) * s, b(c) * s);
    }

    /** h in [0,1), s and v in [0,1]. */
    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float r, g, b;
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return rgb(r, g, b);
    }

    public static float[] toHsv(int c) {
        float r = r(c), g = g(c), b = b(c);
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 1e-5f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[] {h, max <= 0 ? 0 : d / max, max};
    }

    public static int shiftHue(int c, float dh) {
        float[] hsv = toHsv(c);
        return hsv(hsv[0] + dh, hsv[1], hsv[2]);
    }
}

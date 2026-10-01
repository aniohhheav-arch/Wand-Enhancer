package dev.riftverse.util;

/** Stateless integer hashing used for deterministic placement. */
public final class Hash {
    private Hash() {}

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long of(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    public static long of(long seed, long a, long b) {
        return mix(of(seed, a) ^ mix(b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L));
    }

    public static long of(long seed, long a, long b, long c) {
        return mix(of(seed, a, b) ^ mix(c * 0x27D4EB2F165667C5L + 0x61C8864680B583EBL));
    }

    /** Uniform float in [0, 1). */
    public static float unit(long h) {
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Uniform float in [0, 1) for a (seed, a, b) triple. */
    public static float unit(long seed, long a, long b) {
        return unit(of(seed, a, b));
    }

    public static int range(long h, int min, int maxInclusive) {
        return min + (int) Long.remainderUnsigned(h, maxInclusive - min + 1L);
    }

    public static long hashString(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return mix(h);
    }
}

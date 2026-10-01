package dev.riftverse.util;

/** Seeded improved-Perlin noise with fractal helpers. Thread-safe once constructed. */
public final class Noise {
    private final int[] perm = new int[512];
    private final double offX;
    private final double offY;
    private final double offZ;

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        long s = seed ^ 0x5DEECE66DL;
        for (int i = 255; i > 0; i--) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((s >>> 33) % (i + 1));
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        s = s * 6364136223846793005L + 1442695040888963407L;
        offX = ((s >>> 40) & 0xFFFF) / 65536.0 * 256.0;
        s = s * 6364136223846793005L + 1442695040888963407L;
        offY = ((s >>> 40) & 0xFFFF) / 65536.0 * 256.0;
        s = s * 6364136223846793005L + 1442695040888963407L;
        offZ = ((s >>> 40) & 0xFFFF) / 65536.0 * 256.0;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** 3D noise in roughly [-1, 1]. */
    public double noise(double x, double y, double z) {
        x += offX;
        y += offY;
        z += offZ;
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int A = perm[X] + Y, AA = perm[A] + Z, AB = perm[A + 1] + Z;
        int B = perm[X + 1] + Y, BA = perm[B] + Z, BB = perm[B + 1] + Z;
        return lerp(w,
                lerp(v, lerp(u, grad(perm[AA], x, y, z), grad(perm[BA], x - 1, y, z)),
                        lerp(u, grad(perm[AB], x, y - 1, z), grad(perm[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(perm[AA + 1], x, y, z - 1), grad(perm[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(perm[AB + 1], x, y - 1, z - 1), grad(perm[BB + 1], x - 1, y - 1, z - 1))));
    }

    public double noise(double x, double z) {
        return noise(x, 0.5, z);
    }

    public double fbm(double x, double z, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x * freq, z * freq) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    public double fbm(double x, double y, double z, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x * freq, y * freq, z * freq) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    /** Ridged multifractal in [0, 1], sharp crests. */
    public double ridged(double x, double z, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0, weight = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise(x * freq, z * freq));
            n *= n;
            n *= weight;
            weight = Math.min(1.0, n * 2.0);
            sum += n * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.1;
        }
        return sum / norm;
    }

    /** Domain-warped fbm for organic, swirling shapes. */
    public double warped(double x, double z, double warp, int octaves) {
        double wx = fbm(x + 13.7, z - 9.2, 3) * warp;
        double wz = fbm(x - 41.3, z + 27.1, 3) * warp;
        return fbm(x + wx, z + wz, octaves);
    }
}

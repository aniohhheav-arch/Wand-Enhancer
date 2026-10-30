package dev.arcana.magic;

/** Per-school mastery curve: level n needs 40*n^2 xp, capped at level 10. */
public final class Mastery {
    public static final int MAX_LEVEL = 10;

    public static int level(int xp) {
        return Math.min(MAX_LEVEL, (int) Math.floor(Math.sqrt(xp / 40.0)));
    }

    public static int xpFor(int level) {
        return 40 * level * level;
    }

    /** Progress 0..1 toward the next level. */
    public static float progress(int xp) {
        int l = level(xp);
        if (l >= MAX_LEVEL) return 1f;
        int a = xpFor(l), b = xpFor(l + 1);
        return (xp - a) / (float) (b - a);
    }

    private Mastery() {}
}

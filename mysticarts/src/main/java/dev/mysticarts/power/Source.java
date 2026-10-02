package dev.mysticarts.power;

/** Where an ability's power comes from. The six stones double as gauntlet socket bits. */
public enum Source {
    MYSTIC("mystic", 0xFF9A2E, 0xFFD27A),
    SPACE("space", 0x2F7BFF, 0x9FD4FF),
    MIND("mind", 0xFFC81E, 0xFFF3A0),
    REALITY("reality", 0xE3122E, 0xFF8A7A),
    POWER("power", 0x9B2CFF, 0xE0A8FF),
    TIME("time", 0x22E06A, 0xB4FFC8),
    SOUL("soul", 0xFF7A12, 0xFFD08A),
    COMBO("combo", 0xFFFFFF, 0xFFE8B0);

    public static final Source[] STONES = {SPACE, MIND, REALITY, POWER, TIME, SOUL};
    public static final int ALL_STONES = 0b111111;

    public final String id;
    public final int color;
    public final int glow;

    Source(String id, int color, int glow) {
        this.id = id;
        this.color = color;
        this.glow = glow;
    }

    public boolean isStone() {
        return this != MYSTIC && this != COMBO;
    }

    /** Bit in the gauntlet's socket mask; only meaningful for stones. */
    public int bit() {
        return isStone() ? 1 << (ordinal() - 1) : 0;
    }

    public static Source byId(int ordinal) {
        Source[] v = values();
        return v[Math.floorMod(ordinal, v.length)];
    }
}

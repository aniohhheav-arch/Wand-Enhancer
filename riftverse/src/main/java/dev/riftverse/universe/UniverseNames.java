package dev.riftverse.universe;

import java.util.Random;

public final class UniverseNames {
    private static final String[] START = {"Ka", "Ve", "Or", "Zy", "Ael", "Thra", "Ny", "Sol", "Ix", "Mor", "Quel", "Ery", "Vor", "Lum", "Cael", "Xan", "Ith", "Sera", "Dra", "Oph"};
    private static final String[] MIDDLE = {"ra", "the", "lo", "vi", "xa", "ne", "ry", "mi", "ka", "dor", "sil", "tho", "ven", "qua", "ri"};
    private static final String[] END = {"xis", "on", "ara", "eth", "ion", "us", "ys", "ael", "ium", "or", "a", "ex", "yth", "ante", "orum"};

    private UniverseNames() {}

    public static String generate(long seed, Archetype archetype) {
        Random r = new Random(seed ^ 0x7E57A11L);
        StringBuilder sb = new StringBuilder(START[r.nextInt(START.length)]);
        if (r.nextFloat() < 0.65f) sb.append(MIDDLE[r.nextInt(MIDDLE.length)]);
        sb.append(END[r.nextInt(END.length)]);
        if (r.nextFloat() < 0.12f) sb.insert(Math.min(3, sb.length() - 1), '\'');
        return sb + " " + archetype.epithets[r.nextInt(archetype.epithets.length)];
    }
}

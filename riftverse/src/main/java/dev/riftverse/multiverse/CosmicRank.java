package dev.riftverse.multiverse;

/** Cosmic progression tiers, earned through research points (exploration, scans, events, reality work). */
public enum CosmicRank {
    WANDERER("Wanderer", 0, 0xB8C0D0),
    RIFTWALKER("Riftwalker", 60, 0x7DF9FF),
    VOYAGER("Voyager", 220, 0x5CFF9D),
    CARTOGRAPHER("Cartographer", 600, 0xFFC14D),
    ARCHITECT("Reality Architect", 1400, 0xFF7AF0),
    SOVEREIGN("Multiversal Sovereign", 3500, 0xFFF0C8);

    public final String title;
    public final int points;
    public final int color;

    CosmicRank(String title, int points, int color) {
        this.title = title;
        this.points = points;
        this.color = color;
    }

    public static CosmicRank of(int research) {
        CosmicRank best = WANDERER;
        for (CosmicRank r : values()) if (research >= r.points) best = r;
        return best;
    }

    public CosmicRank next() {
        CosmicRank[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : this;
    }
}

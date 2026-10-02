package dev.riftverse.multiverse;

import org.jetbrains.annotations.Nullable;

/** The six End Protocols: distinct, fully choreographed ways to erase a universe. */
public enum EndProtocol {
    ORBITAL_ANNIHILATION("orbital", "ORBITAL ANNIHILATION", "A platform in high orbit locks onto this reality", 0xFF3A2A, 0xFFE0A0,
            CinematicType.PROTOCOL_ORBITAL, 20),
    SINGULARITY_COLLAPSE("singularity", "SINGULARITY COLLAPSE", "Everything is falling toward a single point", 0x8F6BFF, 0xFFFFFF,
            CinematicType.PROTOCOL_SINGULARITY, 20),
    CELESTIAL_DEVOURER("devourer", "THE CELESTIAL DEVOURER", "Something vast has come to feed", 0xB040FF, 0xFF7AF0,
            CinematicType.PROTOCOL_DEVOURER, 22),
    REALITY_DISASSEMBLY("disassembly", "REALITY DISASSEMBLY", "The world forgets how to hold itself together", 0x7DF9FF, 0x39FF14,
            CinematicType.PROTOCOL_DISASSEMBLY, 20),
    BLACK_HOLE_INFUSION("black_hole", "BLACK HOLE INFUSION", "A singularity is being fed this universe", 0xFF8A3A, 0x2A0A00,
            CinematicType.PROTOCOL_BLACK_HOLE, 21),
    TIMELINE_ERASURE("timeline", "TIMELINE ERASURE", "This reality is being unwritten from history", 0x7FD8FF, 0xE8C080,
            CinematicType.PROTOCOL_TIMELINE, 20);

    public final String id;
    public final String title;
    public final String subtitle;
    public final int colorA;
    public final int colorB;
    public final CinematicType cinematic;
    public final int defaultSeconds;

    EndProtocol(String id, String title, String subtitle, int colorA, int colorB, CinematicType cinematic, int defaultSeconds) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.colorA = colorA;
        this.colorB = colorB;
        this.cinematic = cinematic;
        this.defaultSeconds = defaultSeconds;
    }

    public static EndProtocol byOrdinal(int i) {
        EndProtocol[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    @Nullable
    public static EndProtocol byId(String id) {
        for (EndProtocol p : values()) if (p.id.equalsIgnoreCase(id) || p.name().equalsIgnoreCase(id)) return p;
        return null;
    }
}

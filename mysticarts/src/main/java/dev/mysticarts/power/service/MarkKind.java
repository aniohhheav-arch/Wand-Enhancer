package dev.mysticarts.power.service;

/** Status marks the power system can put on any entity. Mirrored to clients for visuals and tick suppression. */
public enum MarkKind {
    /** Time stopped: the entity does not tick at all. */
    FROZEN(0x22E06A),
    /** Time slowed: ticks only every {@code param} game ticks. */
    SLOWED(0x5CFF9A),
    /** Mystic binding: held in place by runic chains. */
    BOUND(0xFF9A2E),
    /** Mind controlled by entity {@code param}: fights for its controller. */
    CONTROLLED(0xFFC81E),
    /** Pacified: will not target players. */
    PACIFIED(0xFFF3A0),
    /** Soul tether: cannot stray from its anchor. */
    TETHERED(0xFF7A12),
    /** Soul separated: stunned and vulnerable. */
    SEPARATED(0xFFB070),
    /** Telekinetically lifted toward its anchor. */
    LIFTED(0xFFD84A),
    /** Spatial prison: locked inside a lattice cube. */
    PRISON(0x2F7BFF),
    /** Caught in a time loop. */
    LOOPING(0x22E06A),
    /** Mystic Detection / Mind Detection highlight. */
    REVEALED(0xFFD27A);

    public final int color;

    MarkKind(int color) {
        this.color = color;
    }

    public static MarkKind byId(int i) {
        MarkKind[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

package dev.riftverse.transit;

/**
 * The flavour of a journey. {@code teleportTick} is when the server moves the player, timed so the client cinematic
 * fully covers the screen at that moment.
 */
public enum TransitKind {
    RIFT(46),
    PORTAL(30),
    BLACK_HOLE(196),
    CONSOLE(56),
    HOMEWARD(42);

    public final int teleportTick;

    TransitKind(int teleportTick) {
        this.teleportTick = teleportTick;
    }

    public static TransitKind byId(int id) {
        TransitKind[] v = values();
        return v[Math.floorMod(id, v.length)];
    }
}

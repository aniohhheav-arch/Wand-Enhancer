package dev.riftverse.multiverse;

/** Lifecycle of a universe as far as reality manipulation is concerned. */
public enum RealityStatus {
    /** Reachable and generating normally. */
    ACTIVE,
    /** Sealed away: terrain is untouched but no one may travel there until it is restored. */
    ARCHIVED,
    /** Unmade: new chunks generate as empty void, travel is refused and only a backup can bring it back. */
    ERASED;

    public static RealityStatus byId(int i) {
        RealityStatus[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

package com.infinitemultiverse.stand;

/** What a manifested Stand is doing. Synced to clients to pick the looping animation and positioning. */
public enum StandAction {
    IDLE,
    BARRAGE,
    HEAVY,
    GUARD,
    TIME_STOP,
    DISMISSING;

    private static final StandAction[] VALUES = values();

    public static StandAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
    }

    /** Actions that place the Stand in front of its user rather than over the shoulder. */
    public boolean isForward() {
        return this == BARRAGE || this == HEAVY || this == GUARD;
    }
}

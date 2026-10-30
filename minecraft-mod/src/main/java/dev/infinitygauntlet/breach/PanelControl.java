package dev.infinitygauntlet.breach;

/**
 * The control panel's buttons, laid out in rows. Toggle buttons light up while their manipulation is active
 * ({@link SpecimenEntity} flag bit {@link #flag}, or -1 for momentary actions).
 */
public enum PanelControl {
    // The operator faces south in the chamber, so screen-left is east and "up/away" is south: the arrows match
    // both the non-rotating survey hologram and the real world.
    MOVE_EAST("east", 0, 0, -1),
    MOVE_SOUTH("south", 0, 1, -1),
    MOVE_NORTH("north", 0, 2, -1),
    MOVE_WEST("west", 0, 3, -1),
    LIFT("lift", 0, 4, SpecimenEntity.FLAG_LEVITATING),
    DROP("drop", 0, 5, -1),
    BLIND("blind", 1, 0, SpecimenEntity.FLAG_BLIND),
    GLOW("glow", 1, 1, SpecimenEntity.FLAG_GLOWING),
    TURN_LEFT("turn_left", 1, 2, -1),
    TURN_RIGHT("turn_right", 1, 3, -1),
    PACIFY("pacify", 1, 4, SpecimenEntity.FLAG_PACIFIED),
    SHRINK("shrink", 2, 0, -1),
    GROW("grow", 2, 1, -1),
    FREEZE("freeze", 2, 2, SpecimenEntity.FLAG_FROZEN),
    PHASE("phase", 2, 3, SpecimenEntity.FLAG_PHASED),
    RESET("reset", 2, 4, -1),
    RELEASE("release", 3, 0, -1);

    public static final int COLUMNS = 6;
    public static final String[] ROWS = {"movement", "vision", "dimension", "system"};

    public final String key;
    public final int row;
    public final int column;
    public final int flag;

    PanelControl(String key, int row, int column, int flag) {
        this.key = key;
        this.row = row;
        this.column = column;
        this.flag = flag;
    }

    /** Number of grid columns this button spans (the release bar spans the whole row). */
    public int span() {
        return this == RELEASE ? COLUMNS : 1;
    }
}

package dev.mysticarts.power;

/** Casting poses, shared by the server (which picks them) and the player-model animation (which plays them). */
public final class Poses {
    public static final int NONE = 0;
    /** Both palms forward: blasts, pushes, beams. */
    public static final int PUSH = 1;
    /** Both arms raised: ultimates, fields, summons. */
    public static final int RAISE = 2;
    /** Left forearm up, palm out: shields. */
    public static final int SHIELD = 3;
    /** Arms swept wide: whips, pulls. */
    public static final int WHIP = 4;
    /** Right arm forward, fingers to the sky: the Snap. */
    public static final int SNAP = 5;
    /** Right arm pointing: targeted spells, teleports. */
    public static final int POINT = 6;
    /** Fists driven down: slams, shockwaves. */
    public static final int SLAM = 7;
    /** Right hand circling: sling ring portals. */
    public static final int CIRCLE = 8;
    /** Both hands cupped at the chest, gathering energy. */
    public static final int CHARGE = 9;
    /** Eye of Agamotto mudra: hands interlocked in front of the chest, turning. */
    public static final int MUDRA = 10;

    private Poses() {}

    public static int forAbility(Ability a) {
        return switch (a) {
            case ELDRITCH_WHIP, MYSTIC_PULL, SOUL_TETHER -> WHIP;
            case ELDRITCH_SHIELD, SHIELD_DOME, DEFLECTION, PORTAL_REDIRECT, SPACE_BARRIER, REALITY_BARRIER, TIME_STONE_BARRIER -> SHIELD;
            case SLING_PORTAL, PORTAL_TRAP, SPACE_PORTAL -> CIRCLE;
            case TIME_REVERSAL, TIME_LOOP, TIME_SLOW, TIME_ACCELERATION, TIME_STOP -> MUDRA;
            case GROUND_SLAM, POWER_SHOCKWAVE, POWER_DESTRUCTION, REALITY_TERRAIN -> SLAM;
            case THE_SNAP -> SNAP;
            case MYSTIC_BLAST, MYSTIC_PUSH, MIND_BEAM, POWER_BLAST, POWER_BEAM, SOUL_BEAM, MIND_PULSE, POWER_PULSE, SOUL_PROJECTION, POWER_ORB -> PUSH;
            default -> a.ultimate() || a.source == Source.COMBO ? RAISE : POINT;
        };
    }
}

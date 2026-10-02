package dev.mysticarts.fx;

/** Identifiers for one-shot effects carried by the Fx payload. Vector/parameter meaning is documented per kind. */
public final class FxKind {
    /** Spark burst at pos; d = bias direction, a = size scale, b = count scale. */
    public static final int BURST = 0;
    /** Expanding shock ring at pos on the plane with normal d; a = radius, b = duration ticks. */
    public static final int RING = 1;
    /** Projectile impact flash + sparks; a = size. */
    public static final int IMPACT = 2;
    /** Eldritch shield struck at pos (on entity's shield); a = strength. */
    public static final int SHIELD_HIT = 3;
    /** Casting mandala blooms at pos facing d; a = size, b = duration ticks. */
    public static final int CAST_CIRCLE = 4;
    /** Teleport streak from pos travelling by offset d (relative); a = width. */
    public static final int TELEPORT = 5;
    /** Time stop begins: centre pos, a = radius, b = duration ticks, entity = caster. */
    public static final int TIME_STOP = 6;
    /** Time resumes at pos; a = radius. */
    public static final int TIME_RESUME = 7;
    /** Green rewind spiral at pos; a = radius. */
    public static final int REWIND = 8;
    /** Disintegration of entity (the Snap). */
    public static final int DUST_AWAY = 9;
    /** Camera shake / flash for nearby viewers; a = shake strength, b = flash, colour = flash colour. */
    public static final int SCREEN = 10;
    /** Huge spherical wave at pos; a = max radius, b = duration ticks. */
    public static final int WAVE = 11;
    /** Particles spiralling into pos from radius a. */
    public static final int ABSORB = 12;
    /** Arc of energy from pos to pos + d. */
    public static final int ARC = 13;
    /** Snap cinematic for the caster entity. */
    public static final int SNAP = 14;
    /** Detection pulse; a = radius. */
    public static final int DETECT = 15;
    /** Clock-face rings at pos facing d; a = size, b = duration. */
    public static final int CLOCKS = 16;
    /** Soul torn from entity toward pos. */
    public static final int SOUL_RIP = 17;
    /** Ultimate charge-up around entity; b = duration ticks. */
    public static final int CHARGE_UP = 18;
    /** Mirror dimension entered (a = 1) or left (a = 0) by the receiving player. */
    public static final int MIRROR = 19;
    /** Screen tint pulse for the receiving player; a = strength, b = duration ticks. */
    public static final int TINT = 20;
    /** Rune chain wraps the entity (binding). */
    public static final int CHAINS = 21;
    /** Transmutation shimmer over a block at pos. */
    public static final int TRANSMUTE = 22;

    private FxKind() {}
}

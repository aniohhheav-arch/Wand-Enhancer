package dev.infinitygauntlet.power;

/** Body poses a player strikes while using the Power Stone. Each one is a distinct arm animation on the client. */
public enum PowerPose {
    /** Gauntlet held out, other hand bracing the wrist, trembling with gathered energy. */
    CHARGE,
    /** Gauntlet arm locked straight at the aim, recoiling. */
    THRUST,
    /** Fist drawn back then driven through. */
    PUNCH,
    /** Both arms raised overhead then hammered down. */
    SLAM,
    /** Arms flung wide, palms open. */
    SPREAD,
    /** Gauntlet raised to the sky, calling something down. */
    RAISE,
    /** Gauntlet held low at the hip, crackling. */
    OVERLOAD,
    /** Both hands cupped forward around a growing mass. */
    HOLD
}

package dev.infinitygauntlet.finisher;

/** Beat ticks of the stationary-caster finishers, shared by the server scripts and the client scripts. */
public final class Timelines {
    /** Space: a rift opens around the target, swallows it and is crushed to a point. */
    public static final class Erasure {
        public static final int OPEN = 10, PULL = 26, COLLAPSE = 62, ERASE = 72, DURATION = 100;
    }

    /** Mind: the target is lifted inside a cage of psychic symbols that detonates as one pulse. */
    public static final class Psychic {
        public static final int LIFT = 12, BUILD = 40, PULSE = 72, DURATION = 100;
        public static final int[] CHIMES = {44, 52, 58, 63, 67, 70};
    }

    /** Reality: the target is frozen, warped through four impossible states and shattered into fragments. */
    public static final class Rewrite {
        public static final int FREEZE = 10, STATES = 24, STATE_LENGTH = 14, SHATTER = 84, DURATION = 110;
    }

    /** Time: the scene stops, the target's past and future selves surround it, converge, and time snaps back. */
    public static final class Collapse {
        public static final int FREEZE = 8, COPIES = 14, CONVERGE = 62, RELEASE = 82, DURATION = 110;
    }

    /** Soul: the target's soul is pulled out of its body, held up, and drunk by the Soul Stone. */
    public static final class Extraction {
        public static final int REACH = 10, SUSPEND = 40, ABSORB = 70, CONSUMED = 86, DURATION = 110;
    }

    private Timelines() {
    }
}

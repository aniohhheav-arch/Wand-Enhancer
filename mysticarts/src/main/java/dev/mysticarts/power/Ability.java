package dev.mysticarts.power;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every castable ability. Costs are in energy points (mystic energy for {@link Source#MYSTIC}, cosmic energy for the
 * stones and combinations); cooldowns are in ticks. Both are scaled by the server config at cast time.
 */
public enum Ability {
    // ------------------------------------------------------------------------------------------ mystic arts
    ELDRITCH_WHIP(Source.MYSTIC, 12, 30, Req.INITIATE),
    ELDRITCH_SHIELD(Source.MYSTIC, 6, 20, Req.INITIATE, Flag.TOGGLE),
    SHIELD_DOME(Source.MYSTIC, 25, 160, Req.ADEPT),
    MYSTIC_BLAST(Source.MYSTIC, 8, 10, Req.INITIATE, Flag.CHARGE),
    MYSTIC_BINDING(Source.MYSTIC, 20, 120, Req.ADEPT),
    SLING_PORTAL(Source.MYSTIC, 25, 60, Req.RING, Flag.SNEAK_ALT),
    PORTAL_TRAP(Source.MYSTIC, 30, 200, Req.RING),
    PORTAL_REDIRECT(Source.MYSTIC, 20, 160, Req.RING),
    PORTAL_DODGE(Source.MYSTIC, 10, 40, Req.RING),
    TIME_REVERSAL(Source.MYSTIC, 40, 400, Req.EYE),
    TIME_LOOP(Source.MYSTIC, 35, 300, Req.EYE),
    TIME_SLOW(Source.MYSTIC, 30, 300, Req.EYE),
    TIME_ACCELERATION(Source.MYSTIC, 25, 300, Req.EYE),
    TIME_STOP(Source.MYSTIC, 60, 600, Req.EYE),
    MIRROR_DIMENSION(Source.MYSTIC, 40, 200, Req.ADEPT),
    MIRROR_FOLD(Source.MYSTIC, 12, 30, Req.MIRROR),
    ASTRAL_PROJECTION(Source.MYSTIC, 30, 200, Req.ADEPT, Flag.TOGGLE),
    ASTRAL_FORM(Source.MYSTIC, 30, 300, Req.ADEPT),
    TELEKINESIS(Source.MYSTIC, 15, 30, Req.INITIATE),
    LEVITATION(Source.MYSTIC, 15, 100, Req.INITIATE),
    MYSTIC_PUSH(Source.MYSTIC, 10, 30, Req.INITIATE),
    MYSTIC_PULL(Source.MYSTIC, 10, 30, Req.INITIATE),
    BANISHMENT(Source.MYSTIC, 45, 600, Req.ADEPT),
    CLONING(Source.MYSTIC, 40, 600, Req.ADEPT),
    GROUND_SLAM(Source.MYSTIC, 20, 100, Req.INITIATE),
    ENERGY_ABSORPTION(Source.MYSTIC, 15, 300, Req.ADEPT),
    MYSTIC_DETECTION(Source.MYSTIC, 10, 200, Req.INITIATE),
    DIMENSIONAL_DASH(Source.MYSTIC, 8, 30, Req.INITIATE),
    DEFLECTION(Source.MYSTIC, 15, 120, Req.INITIATE),

    // ------------------------------------------------------------------------------------------ space stone
    SPACE_TELEPORT(Source.SPACE, 10, 30),
    SPACE_TRAVEL(Source.SPACE, 60, 600),
    SPACE_PORTAL(Source.SPACE, 25, 80, Flag.SNEAK_ALT),
    SPACE_DISPLACE(Source.SPACE, 20, 80),
    SPACE_SWAP(Source.SPACE, 15, 60),
    SPACE_PRISON(Source.SPACE, 30, 240),
    SPACE_GRAVITY(Source.SPACE, 30, 240),
    SPACE_DISTORTION(Source.SPACE, 25, 160),
    SPACE_RIFT(Source.SPACE, 40, 300),
    SPACE_BARRIER(Source.SPACE, 25, 200),
    SPACE_ULTIMATE(Source.SPACE, 120, 1200, Flag.ULTIMATE, Flag.SNEAK_ALT),

    // ------------------------------------------------------------------------------------------ mind stone
    MIND_TELEKINESIS(Source.MIND, 12, 30),
    MIND_CONTROL(Source.MIND, 40, 400),
    MIND_PACIFY(Source.MIND, 30, 400),
    MIND_ILLUSION(Source.MIND, 25, 300),
    MIND_BEAM(Source.MIND, 20, 100),
    MIND_PULSE(Source.MIND, 20, 120),
    MIND_DETECT(Source.MIND, 10, 200),
    MIND_REMOTE(Source.MIND, 5, 10),
    MIND_ENHANCE(Source.MIND, 30, 600),
    MIND_COMMAND(Source.MIND, 5, 20),
    MIND_ULTIMATE(Source.MIND, 120, 1200, Flag.ULTIMATE),

    // ------------------------------------------------------------------------------------------ reality stone
    REALITY_TRANSMUTE(Source.REALITY, 20, 100),
    REALITY_TERRAIN(Source.REALITY, 25, 120),
    REALITY_DISTORT(Source.REALITY, 30, 300),
    REALITY_ILLUSION(Source.REALITY, 20, 200),
    REALITY_WEATHER(Source.REALITY, 40, 600),
    REALITY_TRANSFORM(Source.REALITY, 35, 300),
    REALITY_PHYSICS(Source.REALITY, 30, 300),
    REALITY_BARRIER(Source.REALITY, 25, 200),
    REALITY_RESTORE(Source.REALITY, 20, 200, Flag.SNEAK_ALT),
    REALITY_ENVIRONMENT(Source.REALITY, 45, 600),
    REALITY_ULTIMATE(Source.REALITY, 120, 1200, Flag.ULTIMATE),

    // ------------------------------------------------------------------------------------------ power stone
    POWER_BLAST(Source.POWER, 15, 30),
    POWER_SHOCKWAVE(Source.POWER, 25, 100),
    POWER_SUPERCHARGE(Source.POWER, 25, 300),
    POWER_DESTRUCTION(Source.POWER, 35, 200),
    POWER_BEAM(Source.POWER, 30, 160),
    POWER_ORB(Source.POWER, 20, 60, Flag.CHARGE),
    POWER_ABSORB(Source.POWER, 25, 400),
    POWER_PULSE(Source.POWER, 15, 60),
    POWER_STRENGTH(Source.POWER, 30, 600),
    POWER_EXPLOSION(Source.POWER, 45, 300),
    POWER_ULTIMATE(Source.POWER, 120, 1200, Flag.ULTIMATE),

    // ------------------------------------------------------------------------------------------ time stone
    TIME_STONE_STOP(Source.TIME, 50, 500),
    TIME_STONE_SLOW(Source.TIME, 25, 240),
    TIME_STONE_ACCELERATE(Source.TIME, 25, 240),
    TIME_STONE_REWIND(Source.TIME, 40, 400),
    TIME_STONE_LOOP(Source.TIME, 30, 240),
    TIME_STONE_AGE(Source.TIME, 10, 40),
    TIME_STONE_FREEZE_PROJECTILES(Source.TIME, 20, 160),
    TIME_STONE_DISPLACE(Source.TIME, 30, 300),
    TIME_STONE_SAVESTATE(Source.TIME, 40, 600, Flag.SNEAK_ALT),
    TIME_STONE_BARRIER(Source.TIME, 30, 300),
    TIME_ULTIMATE(Source.TIME, 120, 1200, Flag.ULTIMATE),

    // ------------------------------------------------------------------------------------------ soul stone
    SOUL_BEAM(Source.SOUL, 20, 100),
    SOUL_DETECT(Source.SOUL, 10, 200),
    SOUL_TETHER(Source.SOUL, 20, 200),
    SOUL_PROJECTION(Source.SOUL, 20, 80),
    SOUL_DRAIN(Source.SOUL, 25, 200),
    SOUL_WISPS(Source.SOUL, 35, 500),
    SOUL_HEAL(Source.SOUL, 30, 400),
    SOUL_COMPANION(Source.SOUL, 40, 900),
    SOUL_SEPARATION(Source.SOUL, 25, 300),
    SOUL_RELEASE(Source.SOUL, 10, 60),
    SOUL_ULTIMATE(Source.SOUL, 120, 1200, Flag.ULTIMATE),

    // ------------------------------------------------------------------------------------------ combinations
    COSMIC_CATACLYSM(Source.COMBO, 90, 900, Source.SPACE, Source.POWER),
    TEMPORAL_REWRITE(Source.COMBO, 90, 900, Source.TIME, Source.REALITY),
    DIMENSIONAL_TELEKINESIS(Source.COMBO, 70, 600, Source.MIND, Source.SPACE),
    SPECTRAL_DIMENSION(Source.COMBO, 90, 900, Source.SOUL, Source.REALITY),
    THE_SNAP(Source.COMBO, 200, 6000, Source.SPACE, Source.MIND, Source.REALITY, Source.POWER, Source.TIME, Source.SOUL);

    public enum Flag { ULTIMATE, CHARGE, TOGGLE, SNEAK_ALT }

    /** Mystic-arts prerequisites. */
    public enum Req { NONE, INITIATE, ADEPT, RING, EYE, MIRROR }

    private static final Map<Source, List<Ability>> BY_SOURCE = new EnumMap<>(Source.class);

    static {
        for (Source s : Source.values()) BY_SOURCE.put(s, new ArrayList<>());
        for (Ability a : values()) BY_SOURCE.get(a.source).add(a);
        BY_SOURCE.replaceAll((s, l) -> Collections.unmodifiableList(l));
    }

    public final Source source;
    public final float cost;
    public final int cooldown;
    public final Req req;
    /** Stones that must be socketed for a combination. */
    public final int stoneMask;
    private final int flags;
    public final String id;

    Ability(Source source, float cost, int cooldown, Object... extras) {
        this.source = source;
        this.cost = cost;
        this.cooldown = cooldown;
        Req r = Req.NONE;
        int mask = source.bit();
        int f = 0;
        for (Object o : extras) {
            if (o instanceof Req q) r = q;
            else if (o instanceof Flag fl) f |= 1 << fl.ordinal();
            else if (o instanceof Source s) mask |= s.bit();
        }
        this.req = r;
        this.stoneMask = mask;
        this.flags = f;
        this.id = name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean has(Flag flag) {
        return (flags & (1 << flag.ordinal())) != 0;
    }

    public boolean ultimate() {
        return has(Flag.ULTIMATE) || this == THE_SNAP;
    }

    public boolean cosmic() {
        return source != Source.MYSTIC;
    }

    public int color() {
        return switch (this) {
            case COSMIC_CATACLYSM -> 0x7A5CFF;
            case TEMPORAL_REWRITE -> 0xC8D23A;
            case DIMENSIONAL_TELEKINESIS -> 0x9FE0A0;
            case SPECTRAL_DIMENSION -> 0xFF5A3A;
            case THE_SNAP -> 0xFFE27A;
            default -> source.color;
        };
    }

    public String translationKey() {
        return "ability.mysticarts." + id;
    }

    public static List<Ability> of(Source source) {
        return BY_SOURCE.get(source);
    }

    public static Ability byId(int ordinal) {
        Ability[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : MYSTIC_BLAST;
    }
}

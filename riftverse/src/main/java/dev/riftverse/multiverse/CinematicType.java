package dev.riftverse.multiverse;

import org.jetbrains.annotations.Nullable;

/**
 * Named reality cinematics. Shared by server and client: the server picks one, the client interprets its parameters
 * (camera orbit, colour wash, letterbox, shake) frame by frame.
 */
public enum CinematicType {
    //            id               ticks orbitR rise  wash  shake camera letterbox
    ERASURE("erasure",            160, 14f, 10f, 0.55f, 0.55f, true, true),
    REBUILD("rebuild",            150, 18f, 26f, 0.35f, 0.25f, true, true),
    RESTORE("restore",            130, 12f, 16f, 0.30f, 0.20f, true, true),
    BIRTH("universe_birth",       200, 22f, 8f, 0.25f, 0.30f, true, true),
    CONVERGENCE("convergence",    180, 20f, 12f, 0.30f, 0.20f, true, true),
    COLLAPSE("collapse",          100, 10f, 6f, 0.30f, 0.80f, true, true),
    INVASION("invasion",           90, 12f, 5f, 0.20f, 0.35f, true, true),
    LEVIATHAN("leviathan",        110, 26f, 14f, 0.15f, 0.45f, true, true),
    GUARDIAN("guardian",          110, 16f, 8f, 0.18f, 0.50f, true, true),
    ANOMALY("anomaly",             80, 9f, 4f, 0.25f, 0.30f, true, true),
    SINGULARITY("singularity",    110, 20f, 9f, 0.20f, 0.40f, true, true),
    STORM("rift_storm",            90, 15f, 12f, 0.20f, 0.35f, true, true),
    SCAN("scan",                   50, 0f, 0f, 0.12f, 0.00f, false, false),
    ANNOUNCE("announce",           80, 0f, 0f, 0.00f, 0.15f, false, true),
    // End Protocols (duration comes from the protocol's config)
    PROTOCOL_ORBITAL("orbital_annihilation",   400, 26f, 30f, 0.30f, 0.50f, false, true),
    PROTOCOL_SINGULARITY("singularity_collapse", 400, 22f, 14f, 0.35f, 0.45f, false, true),
    PROTOCOL_DEVOURER("celestial_devourer",    440, 30f, 6f, 0.30f, 0.40f, false, true),
    PROTOCOL_DISASSEMBLY("reality_disassembly", 400, 16f, 18f, 0.25f, 0.25f, false, true),
    PROTOCOL_BLACK_HOLE("black_hole_infusion",  420, 24f, 10f, 0.30f, 0.55f, false, true),
    PROTOCOL_TIMELINE("timeline_erasure",       400, 14f, 12f, 0.40f, 0.20f, false, true),
    DISCOVERY("discovery",        150, 4f, 2f, 0.90f, 0.20f, true, true),
    RUPTURE("final_rupture",      260, 22f, 18f, 0.40f, 0.70f, true, true),
    TIME_TRAVEL("time_travel",    120, 0f, 0f, 0.30f, 0.25f, false, true),
    TSA_ARREST("tsa_arrest",       90, 0f, 0f, 0.25f, 0.30f, false, true);

    public final String id;
    public final int defaultTicks;
    public final float orbitRadius;
    public final float rise;
    public final float wash;
    public final float shake;
    public final boolean camera;
    public final boolean letterbox;

    CinematicType(String id, int defaultTicks, float orbitRadius, float rise, float wash, float shake, boolean camera, boolean letterbox) {
        this.id = id;
        this.defaultTicks = defaultTicks;
        this.orbitRadius = orbitRadius;
        this.rise = rise;
        this.wash = wash;
        this.shake = shake;
        this.camera = camera;
        this.letterbox = letterbox;
    }

    public static CinematicType byOrdinal(int i) {
        CinematicType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    @Nullable
    public static CinematicType byId(String id) {
        for (CinematicType t : values()) if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
        return null;
    }
}

package dev.riftverse.multiverse.event;

import org.jetbrains.annotations.Nullable;

/** Every multiverse event. Ids are used by commands, config keys and the saved event ledger. */
public enum EventType {
    REALITY_COLLAPSE("reality_collapse", "REALITY COLLAPSE", "The fabric of this world is coming apart", 0xFF3A5A, 900, 90, 600,
            "Terrain fractures and falls into the void around the epicentre. Stabilize reality or flee."),
    DIMENSIONAL_INVASION("dimensional_invasion", "DIMENSIONAL INVASION", "Hostile rifts are tearing open", 0xFF2440, 500, 45, 1200,
            "Three hostile rifts disgorge waves of void creatures. Defeat every wave for a reward."),
    COSMIC_LEVIATHAN("cosmic_leviathan", "COSMIC LEVIATHAN", "Something vast swims between the stars", 0x40FFE0, 1400, 120, 6000,
            "An Abyssal Leviathan breaches into this reality. The event ends when it is slain or departs."),
    ANCIENT_GUARDIAN("ancient_guardian", "THE ANCIENT GUARDIAN", "A custodian of the boundaries awakens", 0xFFC14D, 1600, 150, 6000,
            "The Rift Warden descends to judge those who travel between worlds."),
    DIMENSIONAL_ANOMALY("dimensional_anomaly", "DIMENSIONAL ANOMALY", "Physics no longer applies here", 0x7AF0D0, 350, 30, 900,
            "A zone of broken physics: gravity flips, matter drifts and creatures blink through space."),
    VOID_WANDERER("void_wanderer", "THE VOID WANDERER", "It walks between worlds, and it is hungry", 0x9B30FF, 900, 90, 2400,
            "A colossal void stalker roams the area, smothering the light around it."),
    UNIVERSE_BIRTH("universe_birth", "A UNIVERSE IS BORN", "A new reality condenses out of nothing", 0xFFF0C8, 1200, 120, 420,
            "A brand-new universe forms overhead and leaves a stable rift leading into it."),
    BLACK_HOLE("black_hole", "SINGULARITY", "A black hole is forming", 0xFF8A3A, 1000, 90, 1800,
            "A natural black hole blooms nearby and grows, pulling in everything around it."),
    RIFT_STORM("rift_storm", "RIFT STORM", "Reality is splitting at the seams", 0x8A3AFF, 450, 40, 1200,
            "Unstable rifts flicker open everywhere, each leading somewhere different."),
    COSMIC_CONVERGENCE("cosmic_convergence", "COSMIC CONVERGENCE", "Every reality briefly touches this one", 0xFF7AF0, 2000, 240, 1600,
            "Rifts to many realities align and their creatures drift through. Witnesses gain deep research."),
    DIMENSIONAL_MIGRATION("dimensional_migration", "DIMENSIONAL MIGRATION", "Another ecosystem is pouring through", 0x5CFF9D, 700, 60, 1400,
            "Two rifts open into a living migration route: alien creatures arrive through one while local life is drawn into the other."),
    COSMIC_DEITY("cosmic_deity", "THE COSMIC DEITY", "A god made of night has come to feed", 0xC070FF, 2500, 240, 9000,
            "A titan wearing the night sky descends. Between star-bolts it inhales, dragging creatures and land into its mouth.");

    public final String id;
    public final String title;
    public final String subtitle;
    public final int color;
    public final int defaultRarity;
    public final int defaultCooldownMinutes;
    public final int durationTicks;
    public final String description;

    EventType(String id, String title, String subtitle, int color, int defaultRarity, int defaultCooldownMinutes, int durationTicks, String description) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.color = color;
        this.defaultRarity = defaultRarity;
        this.defaultCooldownMinutes = defaultCooldownMinutes;
        this.durationTicks = durationTicks;
        this.description = description;
    }

    @Nullable
    public static EventType byId(String id) {
        for (EventType t : values()) if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
        return null;
    }
}

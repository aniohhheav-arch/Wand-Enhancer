package com.infinitemultiverse.core;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.network.chat.Component;

/**
 * Every gameplay pillar of the multiverse. Abilities belong to exactly one system, and each system can be
 * switched off per world in the server config. {@link #plannedPhase()} is the roadmap phase that ships it.
 */
public enum MultiverseSystem {
    CORE("core", 1, 0x7FD6FF),
    STANDS("stands", 2, 0xC77DFF),
    CURSED_TECHNIQUES("cursed_techniques", 2, 0x6F7BFF),
    SUPERHEROES("superheroes", 2, 0xFF5D5D),
    MYSTIC_ARTS("mystic_arts", 2, 0xFFB347),
    INFINITY_GAUNTLET("infinity_gauntlet", 3, 0xFFD700),
    SPACE("space", 3, 0x8FB8FF),
    PORTALS("portals", 3, 0x4DFFB8),
    TIME_TRAVEL("time_travel", 4, 0x5CE1E6),
    WEAPONS("weapons", 4, 0xB0B7C3),
    CONSTRUCTION("construction", 5, 0x7CFC9A),
    ARENA("arena", 5, 0xFF8A3D),
    DIMENSIONS("dimensions", 5, 0xE6D36B),
    SANDBOX("sandbox", 6, 0x9AD17A),
    MUSICAL("musical", 7, 0xFF7AD9);

    private final String id;
    private final int plannedPhase;
    private final int color;

    MultiverseSystem(String id, int plannedPhase, int color) {
        this.id = id;
        this.plannedPhase = plannedPhase;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int plannedPhase() {
        return plannedPhase;
    }

    /** RGB accent colour used by the HUD and menus. */
    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("system." + InfiniteMultiverse.MOD_ID + "." + id);
    }

    public Component description() {
        return Component.translatable("system." + InfiniteMultiverse.MOD_ID + "." + id + ".desc");
    }
}

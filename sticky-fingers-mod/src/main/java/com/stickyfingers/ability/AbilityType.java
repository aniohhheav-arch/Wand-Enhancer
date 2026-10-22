package com.stickyfingers.ability;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Every ability across all three Stands.
 * The {@code stand} field declares which Stand owns this ability.
 */
public enum AbilityType {
    // ─────────────────── Sticky Fingers ───────────────────
    ZIPPER_OPEN        (StandType.STICKY_FINGERS, true,  20,   8,  6,  200),
    ZIPPER_DOOR        (StandType.STICKY_FINGERS, true,  60,  18,  6,  400),
    ZIPPER_STORAGE     (StandType.STICKY_FINGERS, true,  20,   5,  0,    0),
    ZIPPER_TRAVEL      (StandType.STICKY_FINGERS, true,  30,  15, 32, 1200),
    ZIPPER_RESTRAINT   (StandType.STICKY_FINGERS, true, 160,  25, 12,   80),
    ZIPPER_PUNCH       (StandType.STICKY_FINGERS, true,  40,  12,  3,   24),
    ZIPPER_TELEPORT    (StandType.STICKY_FINGERS, true,  60,  20, 48,    0),
    ZIPPER_TRAP        (StandType.STICKY_FINGERS, true,  80,  15,  6,  100),
    ZIPPER_PROJECTILE  (StandType.STICKY_FINGERS, true,  30,  10, 40,   60),
    ZIPPER_NETWORK     (StandType.STICKY_FINGERS, true,   0,   0, 64,    0),
    ZIPPER_WORLD       (StandType.STICKY_FINGERS, true,1800, 100, 14,    0),
    ZIPPER_MOB         (StandType.STICKY_FINGERS, false,120,  20,  8,  100),
    AERIAL_ZIPPER      (StandType.STICKY_FINGERS, false, 60,  15,  5,  300),
    ZIPPER_ESCAPE      (StandType.STICKY_FINGERS, false,200,  25,  0,  100),
    ZIPPER_BODY        (StandType.STICKY_FINGERS, false,300,  10,  0,  300),
    ZIPPER_DECOY       (StandType.STICKY_FINGERS, false,240,  25,  6,  200),
    ZIPPER_SLICE       (StandType.STICKY_FINGERS, false, 80,  20, 10,  200),
    ZIPPER_COMBO       (StandType.STICKY_FINGERS, false,600,  60,  8,    0),
    ZIPPER_AMBUSH      (StandType.STICKY_FINGERS, false,300,  30, 24,  160),
    ZIPPER_WALL        (StandType.STICKY_FINGERS, false,100,  25,  6,  300),
    ZIPPER_FLOOR       (StandType.STICKY_FINGERS, false,100,  25, 12,  300),
    ZIPPER_REPAIR      (StandType.STICKY_FINGERS, false, 40,  10, 16,    0),

    // ─────────────────── Star Platinum ───────────────────
    SP_ORA_BARRAGE     (StandType.STAR_PLATINUM, true,  60,  20, 5,   80),
    SP_PRECISION_STRIKE(StandType.STAR_PLATINUM, true,  40,  15, 8,    0),
    SP_HEAVY_PUNCH     (StandType.STAR_PLATINUM, true,  80,  25, 4,    0),
    SP_UPPERCUT        (StandType.STAR_PLATINUM, true,  80,  20, 4,    0),
    SP_STAR_FINGER     (StandType.STAR_PLATINUM, true,  50,  18, 16,   0),
    SP_TIME_STOP       (StandType.STAR_PLATINUM, true, 400,  40, 0,  140),
    SP_GUARD           (StandType.STAR_PLATINUM, false,200,  30, 0,   60),
    SP_GRAB_THROW      (StandType.STAR_PLATINUM, false,100,  20, 4,    0),
    SP_ORA_FINISHER    (StandType.STAR_PLATINUM, false,600,  60, 6,    0),
    SP_THE_WORLD       (StandType.STAR_PLATINUM, false,1200,100, 0,  200),

    // ─────────────────── D4C ───────────────────
    D4C_PORTAL         (StandType.D4C, true,  80,  20, 24,  300),
    D4C_DIMENSION_JUMP (StandType.D4C, true,  60,  15, 32,    0),
    D4C_CLONE          (StandType.D4C, true, 200,  30, 12,  600),
    D4C_PARALLEL_SWAP  (StandType.D4C, true, 120,  20,  0,    0),
    D4C_AMBUSH         (StandType.D4C, true,  80,  25, 16,    0),
    D4C_LOVE_TRAIN     (StandType.D4C, false,400,  50,  0,  160),
    D4C_REALITY_FRACTURE(StandType.D4C,false,160,  30, 10,    0),
    D4C_COLLISION      (StandType.D4C, false,300,  40, 12,    0),
    D4C_CLONE_ASSAULT  (StandType.D4C, false,500,  60, 16,    0),
    D4C_DIMENSIONAL_COLLAPSE(StandType.D4C, false,1200,100, 0,    0);

    public static final AbilityType[] VALUES = values();

    public final StandType stand;
    public final boolean primary;
    public final int defaultCooldown;
    public final int defaultCost;
    public final int defaultRange;
    public final int defaultDuration;

    AbilityType(StandType stand, boolean primary, int cooldown, int cost, int range, int duration) {
        this.stand = stand;
        this.primary = primary;
        this.defaultCooldown = cooldown;
        this.defaultCost = cost;
        this.defaultRange = range;
        this.defaultDuration = duration;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("ability.stickyfingers." + key());
    }

    public Component description() {
        return Component.translatable("ability.stickyfingers." + key() + ".desc");
    }

    public ResourceLocation icon() {
        return StickyFingersMod.id("textures/gui/icons/" + key() + ".png");
    }

    public static AbilityType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ZIPPER_OPEN;
    }

    /** First ability in this enum that belongs to the given Stand type. */
    public static AbilityType firstFor(StandType type) {
        for (AbilityType a : VALUES) if (a.stand == type) return a;
        return ZIPPER_OPEN;
    }
}

package com.stickyfingers.ability;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Every Sticky Fingers ability. Defaults are only seeds for the server config;
 * gameplay always reads cooldown/cost/range/duration through {@link com.stickyfingers.config.SFConfig}.
 * {@code primary} abilities are placed on the inner ring of the ability wheel.
 */
public enum AbilityType {
    ZIPPER_OPEN(true, 20, 8, 6, 200),
    ZIPPER_DOOR(true, 60, 18, 6, 400),
    ZIPPER_STORAGE(true, 20, 5, 0, 0),
    ZIPPER_TRAVEL(true, 30, 15, 32, 1200),
    ZIPPER_RESTRAINT(true, 160, 25, 12, 80),
    ZIPPER_PUNCH(true, 40, 12, 3, 24),
    ZIPPER_TELEPORT(true, 60, 20, 48, 0),
    ZIPPER_TRAP(true, 80, 15, 6, 100),
    ZIPPER_PROJECTILE(true, 30, 10, 40, 60),
    ZIPPER_NETWORK(true, 0, 0, 64, 0),
    ZIPPER_WORLD(true, 1800, 100, 14, 0),
    ZIPPER_MOB(false, 120, 20, 8, 100),
    AERIAL_ZIPPER(false, 60, 15, 5, 300),
    ZIPPER_ESCAPE(false, 200, 25, 0, 100),
    ZIPPER_BODY(false, 300, 10, 0, 300),
    ZIPPER_DECOY(false, 240, 25, 6, 200),
    ZIPPER_SLICE(false, 80, 20, 10, 200),
    ZIPPER_COMBO(false, 600, 60, 8, 0),
    ZIPPER_AMBUSH(false, 300, 30, 24, 160),
    ZIPPER_WALL(false, 100, 25, 6, 300),
    ZIPPER_FLOOR(false, 100, 25, 12, 300),
    ZIPPER_REPAIR(false, 40, 10, 16, 0);

    public static final AbilityType[] VALUES = values();

    public final boolean primary;
    public final int defaultCooldown;
    public final int defaultCost;
    public final int defaultRange;
    public final int defaultDuration;

    AbilityType(boolean primary, int cooldown, int cost, int range, int duration) {
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
}

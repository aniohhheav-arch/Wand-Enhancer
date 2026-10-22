package com.stickyfingers.ability;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The three available Stands. */
public enum StandType {
    STICKY_FINGERS("Sticky Fingers", "Bucciarati"),
    STAR_PLATINUM("Star Platinum", "Jotaro"),
    D4C("Dirty Deeds Done Dirt Cheap", "Valentine");

    public static final StandType[] VALUES = values();
    public final String standName;
    public final String userName;

    StandType(String standName, String userName) {
        this.standName = standName;
        this.userName = userName;
    }

    public Component displayName() {
        return Component.translatable("stand.stickyfingers." + name().toLowerCase(Locale.ROOT));
    }

    public static StandType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : STICKY_FINGERS;
    }
}

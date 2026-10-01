package com.infinitemultiverse.core.ability;

import net.minecraft.network.chat.Component;

public enum ActivationType {
    /** Fires once, then the cooldown starts. */
    INSTANT("instant"),
    /** Stays on until pressed again or energy runs out; drains upkeep per second. Cooldown starts when it ends. */
    TOGGLE("toggle");

    private final String id;

    ActivationType(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("activation_type.infinitemultiverse." + id);
    }
}

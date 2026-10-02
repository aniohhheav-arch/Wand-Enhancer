package com.infinitemultiverse.cosmic.gauntlet;

import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.cosmic.CosmicContent;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** The six Infinity Stones, in gauntlet socket order. Each grants three techniques while set in a held gauntlet. */
public enum InfinityStone {
    SPACE(0x3D7BFF),
    MIND(0xFFD83D),
    REALITY(0xE0202A),
    POWER(0xA040FF),
    TIME(0x2EE06A),
    SOUL(0xFF8A1A);

    private final int color;

    InfinityStone(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("stone.infinitemultiverse." + id());
    }

    public List<Supplier<? extends Ability>> abilities() {
        return CosmicContent.stoneAbilities(this);
    }
}

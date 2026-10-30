package dev.infinitygauntlet.ability;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** A server-authoritative gauntlet ability. Implementations perform all gameplay on the server and request visuals through {@code Vfx}. */
public abstract class Ability {
    private final ResourceLocation id;
    private final InfinityStone stone;
    private final int defaultCooldown;
    private final Set<InfinityStone> requiredStones;

    protected Ability(String name, InfinityStone stone, int defaultCooldown) {
        this(name, stone, defaultCooldown, EnumSet.of(stone));
    }

    /** A combination ability: available under each of {@code required} once all of them are installed. */
    protected Ability(String name, InfinityStone primary, int defaultCooldown, Set<InfinityStone> required) {
        this.id = InfinityGauntlet.id(name);
        this.stone = primary;
        this.defaultCooldown = defaultCooldown;
        this.requiredStones = Collections.unmodifiableSet(EnumSet.copyOf(required));
    }

    public Set<InfinityStone> requiredStones() {
        return requiredStones;
    }

    public boolean isCombination() {
        return requiredStones.size() > 1;
    }

    public abstract CastResult cast(AbilityContext ctx);

    /**
     * Lets a cast run while the ability is recharging, for follow-up actions such as dismissing what the
     * previous cast created. Such casts should return {@link CastResult#utility}.
     */
    public boolean bypassesCooldown(AbilityContext ctx) {
        return false;
    }

    public ResourceLocation id() {
        return id;
    }

    public InfinityStone stone() {
        return stone;
    }

    public int defaultCooldown() {
        return defaultCooldown;
    }

    public String translationKey() {
        return "ability.infinitygauntlet." + id.getPath();
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    public Component description() {
        return Component.translatable(translationKey() + ".desc");
    }
}

package com.infinitemultiverse.power;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A bundle of abilities a player can own within one system: a cursed technique, a mutant power, the Mystic Arts.
 * A player holds at most one set per system; granting one unlocks and binds its abilities.
 */
public final class PowerSet {
    private final MultiverseSystem system;
    private final int color;
    private final List<Supplier<? extends Ability>> abilities;
    @Nullable
    private ResourceLocation id;

    public PowerSet(MultiverseSystem system, int color, List<Supplier<? extends Ability>> abilities) {
        this.system = system;
        this.color = color;
        this.abilities = abilities;
    }

    public MultiverseSystem system() {
        return system;
    }

    public int color() {
        return color;
    }

    public List<Ability> abilities() {
        return abilities.stream().<Ability>map(Supplier::get).toList();
    }

    public ResourceLocation id() {
        if (id == null) {
            id = MultiverseRegistries.POWER_SETS.getKey(this);
            if (id == null) {
                throw new IllegalStateException("Unregistered power set");
            }
        }
        return id;
    }

    public Component displayName() {
        return Component.translatable(Util.makeDescriptionId("power", id()));
    }
}

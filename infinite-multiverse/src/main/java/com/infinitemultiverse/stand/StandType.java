package com.infinitemultiverse.stand;

import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A Stand archetype. Assets are resolved by convention from the registry id {@code ns:path}:
 * {@code geo/entity/stand/<path>.geo.json}, {@code animations/entity/stand/<path>.animation.json} and
 * {@code textures/entity/stand/<path>.png} (+ {@code _glowmask.png}).
 */
public final class StandType {
    private final int color;
    private final float scale;
    private final boolean tracksRewind;
    private final List<Supplier<? extends Ability>> abilities;
    @Nullable
    private ResourceLocation id;

    public StandType(int color, List<Supplier<? extends Ability>> abilities) {
        this(color, 1f, false, abilities);
    }

    public StandType(int color, float scale, boolean tracksRewind, List<Supplier<? extends Ability>> abilities) {
        this.color = color;
        this.scale = scale;
        this.tracksRewind = tracksRewind;
        this.abilities = abilities;
    }

    /** Render scale of the model (Tusk is small). */
    public float scale() {
        return scale;
    }

    /** Whether the user's recent positions are recorded for a rewind ability. */
    public boolean tracksRewind() {
        return tracksRewind;
    }

    public int color() {
        return color;
    }

    /** Abilities granted (unlocked) when a player awakens this Stand, in default loadout order. */
    public List<Ability> abilities() {
        return abilities.stream().<Ability>map(Supplier::get).toList();
    }

    public ResourceLocation id() {
        if (id == null) {
            id = MultiverseRegistries.STAND_TYPES.getKey(this);
            if (id == null) {
                throw new IllegalStateException("Unregistered stand type");
            }
        }
        return id;
    }

    public Component displayName() {
        return Component.translatable(Util.makeDescriptionId("stand", id()));
    }

    public ResourceLocation model() {
        return asset("geo/entity/stand/", ".geo.json");
    }

    public ResourceLocation animations() {
        return asset("animations/entity/stand/", ".animation.json");
    }

    public ResourceLocation texture() {
        return asset("textures/entity/stand/", ".png");
    }

    private ResourceLocation asset(String prefix, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(id().getNamespace(), prefix + id().getPath() + suffix);
    }
}

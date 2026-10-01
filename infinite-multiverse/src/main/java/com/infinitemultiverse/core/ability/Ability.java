package com.infinitemultiverse.core.ability;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A registered, server-authoritative ability. Instances are singletons in {@link MultiverseRegistries#ABILITIES};
 * all per-player state lives in {@link com.infinitemultiverse.core.data.PlayerMultiverseData}.
 *
 * <p>Energy and cooldowns are charged by {@link AbilityManager} only when {@link #activate} reports success, so an
 * ability that finds no valid target or destination costs nothing.
 */
public abstract class Ability {
    private final MultiverseSystem system;
    private final ActivationType activationType;
    private final boolean unlockedByDefault;
    @Nullable
    private ResourceLocation iconTexture;
    @Nullable
    private String descriptionId;

    protected Ability(MultiverseSystem system, ActivationType activationType, boolean unlockedByDefault) {
        this.system = system;
        this.activationType = activationType;
        this.unlockedByDefault = unlockedByDefault;
    }

    public abstract float energyCost();

    public abstract int cooldownTicks();

    /** Energy drained per second while a {@link ActivationType#TOGGLE} ability is active. */
    public float upkeepPerSecond() {
        return 0f;
    }

    /** Server side. Returns {@code true} only if the ability actually took effect. */
    public abstract boolean activate(AbilityContext ctx);

    /** Server side, every tick while a toggle ability is active. {@code activeTicks} starts at 1. */
    public void tickActive(AbilityContext ctx, int activeTicks) {
    }

    /** Cooldown applied when a toggle ends after {@code activeTicks}. Defaults to {@link #cooldownTicks()}. */
    public int toggleCooldown(ServerPlayer player, int activeTicks) {
        return cooldownTicks();
    }

    /** Server side, when a toggle ability ends for any reason. Must undo every lasting effect. */
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
    }

    public final MultiverseSystem system() {
        return system;
    }

    public final ActivationType activationType() {
        return activationType;
    }

    public final boolean isUnlockedByDefault() {
        return unlockedByDefault;
    }

    public final ResourceLocation id() {
        ResourceLocation id = MultiverseRegistries.ABILITIES.getKey(this);
        if (id == null) {
            throw new IllegalStateException("Ability " + getClass().getName() + " is not registered");
        }
        return id;
    }

    public final String descriptionId() {
        if (descriptionId == null) {
            descriptionId = Util.makeDescriptionId("ability", id());
        }
        return descriptionId;
    }

    public final Component displayName() {
        return Component.translatable(descriptionId());
    }

    public final Component description() {
        return Component.translatable(descriptionId() + ".desc");
    }

    /** {@code <namespace>:textures/gui/ability/<path>.png}, a 64x64 icon. */
    public final ResourceLocation iconTexture() {
        if (iconTexture == null) {
            ResourceLocation id = id();
            iconTexture = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/gui/ability/" + id.getPath() + ".png");
        }
        return iconTexture;
    }
}

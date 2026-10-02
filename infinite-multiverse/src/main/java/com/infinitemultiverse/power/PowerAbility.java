package com.infinitemultiverse.power;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.config.MultiverseConfig;

/** Base for cursed, mutant, suit and mystic abilities: cost and cooldown come from {@code powers.abilities.<id path>}. */
public abstract class PowerAbility extends Ability {
    private final float upkeep;

    protected PowerAbility(MultiverseSystem system) {
        this(system, ActivationType.INSTANT, 0f);
    }

    protected PowerAbility(MultiverseSystem system, ActivationType type, float upkeepPerSecond) {
        this(system, type, upkeepPerSecond, false);
    }

    /** {@code unlocked}: available without owning a power set (gear-gated abilities check their gear instead). */
    protected PowerAbility(MultiverseSystem system, ActivationType type, float upkeepPerSecond, boolean unlocked) {
        super(system, type, unlocked);
        this.upkeep = upkeepPerSecond;
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.powerTuning(id().getPath()).cost();
    }

    @Override
    public float energyCost(net.minecraft.server.level.ServerPlayer player) {
        return energyCost() * com.infinitemultiverse.power.mastery.Mastery.costMultiplier(player, system());
    }

    /** Damage multiplier from mastery. */
    protected static float mastery(net.minecraft.server.level.ServerPlayer player, com.infinitemultiverse.core.MultiverseSystem system) {
        return com.infinitemultiverse.power.mastery.Mastery.damageMultiplier(player, system);
    }

    protected static int masteryLevel(net.minecraft.server.level.ServerPlayer player, com.infinitemultiverse.core.MultiverseSystem system) {
        return com.infinitemultiverse.power.mastery.Mastery.level(player, system);
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.powerTuning(id().getPath()).cooldown();
    }

    @Override
    public float upkeepPerSecond() {
        return upkeep;
    }
}

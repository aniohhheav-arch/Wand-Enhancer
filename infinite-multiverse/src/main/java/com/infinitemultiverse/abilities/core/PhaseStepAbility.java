package com.infinitemultiverse.abilities.core;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.core.world.Blink;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Short-range blink along the look direction to the nearest validated safe position. */
public final class PhaseStepAbility extends Ability {

    public PhaseStepAbility() {
        super(MultiverseSystem.CORE, ActivationType.INSTANT, true);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.phaseStep.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.phaseStep.cooldown();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        return Blink.perform(ctx, MultiverseConfig.SERVER.phaseStepRange.get());
    }
}

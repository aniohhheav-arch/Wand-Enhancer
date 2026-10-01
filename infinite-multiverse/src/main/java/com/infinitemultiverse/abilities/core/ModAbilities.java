package com.infinitemultiverse.abilities.core;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Phase 1 "Multiverse Core" abilities: each exercises a different part of the engine (teleport, motion, AoE, toggle, debuff). */
public final class ModAbilities {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MultiverseRegistries.ABILITY_KEY, InfiniteMultiverse.MOD_ID);

    public static final DeferredHolder<Ability, PhaseStepAbility> PHASE_STEP = ABILITIES.register("phase_step", PhaseStepAbility::new);
    public static final DeferredHolder<Ability, KineticLeapAbility> KINETIC_LEAP = ABILITIES.register("kinetic_leap", KineticLeapAbility::new);
    public static final DeferredHolder<Ability, ShockwaveAbility> SHOCKWAVE = ABILITIES.register("shockwave", ShockwaveAbility::new);
    public static final DeferredHolder<Ability, AegisFieldAbility> AEGIS_FIELD = ABILITIES.register("aegis_field", AegisFieldAbility::new);
    public static final DeferredHolder<Ability, TemporalDragAbility> TEMPORAL_DRAG = ABILITIES.register("temporal_drag", TemporalDragAbility::new);

    private ModAbilities() {
    }
}

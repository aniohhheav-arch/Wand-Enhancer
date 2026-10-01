package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every ability owns its sound events so they can be re-skinned through resource packs. The bundled
 * sounds.json layers pitch-shifted vanilla sources until recorded audio replaces them.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, InfiniteMultiverse.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE_STEP_DEPART = register("ability.phase_step.depart");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE_STEP_ARRIVE = register("ability.phase_step.arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINETIC_LEAP = register("ability.kinetic_leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCKWAVE = register("ability.shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> AEGIS_ACTIVATE = register("ability.aegis_field.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> AEGIS_DEACTIVATE = register("ability.aegis_field.deactivate");
    public static final DeferredHolder<SoundEvent, SoundEvent> AEGIS_IMPACT = register("ability.aegis_field.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEMPORAL_DRAG = register("ability.temporal_drag");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_DENIED = register("ui.denied");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_SELECT = register("ui.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_BIND = register("ui.bind");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(InfiniteMultiverse.id(name)));
    }
}

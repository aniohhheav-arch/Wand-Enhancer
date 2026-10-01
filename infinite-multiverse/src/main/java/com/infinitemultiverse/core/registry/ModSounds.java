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
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_SUMMON = register("stand.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_DISMISS = register("stand.dismiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_AWAKEN = register("stand.awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_SWING = register("stand.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_PUNCH = register("stand.punch");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_HEAVY = register("stand.heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_CATCH = register("stand.catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STOP = register("stand.time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_RESUME = register("stand.time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_KNIFE = register("stand.knife");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_BOMB_MARK = register("stand.bomb_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_REWIND = register("stand.rewind");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_LIFE = register("stand.life");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_HEAL = register("stand.heal");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_EPITAPH = register("stand.epitaph");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_TIME_ERASE = register("stand.time_erase");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_ACCELERATE = register("stand.accelerate");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_NAIL = register("stand.nail");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_DENIED = register("ui.denied");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_SELECT = register("ui.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_BIND = register("ui.bind");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(InfiniteMultiverse.id(name)));
    }
}

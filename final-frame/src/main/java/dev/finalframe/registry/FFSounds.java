package dev.finalframe.registry;

import dev.finalframe.FinalFrame;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FFSounds {
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FinalFrame.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SHOT = sound("revolver.shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRY_FIRE = sound("revolver.dry_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAMMER_COCK = sound("revolver.hammer_cock");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYLINDER_SPIN = sound("revolver.cylinder_spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD = sound("revolver.reload");

    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WORD_SHOT = sound("finisher.last_word_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> REVOLVER_SPIN = sound("finisher.revolver_spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAW = sound("finisher.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLSTER = sound("finisher.holster");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCH = sound("finisher.catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHOOSH = sound("finisher.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOVE = sound("finisher.shove");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOTH = sound("finisher.cloth");
    public static final DeferredHolder<SoundEvent, SoundEvent> STUMBLE = sound("finisher.stumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = sound("finisher.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOWMO_IN = sound("finisher.slowmo_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOWMO_OUT = sound("finisher.slowmo_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> EAR_RING = sound("finisher.ear_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_HISS = sound("finisher.smoke_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> STINGER = sound("finisher.stinger");

    private FFSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FinalFrame.id(name)));
    }
}

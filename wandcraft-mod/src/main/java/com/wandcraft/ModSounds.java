package com.wandcraft;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom sounds, all synthesized by {@code tools/gen_sounds.py}. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WandCraft.ID);

    public static final Supplier<SoundEvent> DC_SUMMON = reg("dark_clone.summon");
    public static final Supplier<SoundEvent> DC_WHISPER = reg("dark_clone.whisper");
    public static final Supplier<SoundEvent> DC_HUNT = reg("dark_clone.hunt");
    public static final Supplier<SoundEvent> DC_PHASE = reg("dark_clone.phase");
    public static final Supplier<SoundEvent> DC_REACH = reg("dark_clone.reach");
    public static final Supplier<SoundEvent> DC_POSSESS = reg("dark_clone.possess");
    public static final Supplier<SoundEvent> DC_DISINTEGRATE = reg("dark_clone.disintegrate");
    public static final Supplier<SoundEvent> DC_IMPLODE = reg("dark_clone.implode");
    public static final Supplier<SoundEvent> ASH_SCATTER = reg("ash.scatter");

    private ModSounds() {}

    private static Supplier<SoundEvent> reg(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WandCraft.ID, id)));
    }
}

package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, StickyFingersMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ZIPPER_OPEN = reg("zipper_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZIPPER_CLOSE = reg("zipper_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZIPPER_PLACE = reg("zipper_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZIPPER_SNAP = reg("zipper_snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH = reg("punch");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH_BARRAGE = reg("punch_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH_HEAVY = reg("punch_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_SUMMON = reg("stand_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAND_DISMISS = reg("stand_dismiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT = reg("teleport");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROJECTILE_FIRE = reg("projectile_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> PROJECTILE_HIT = reg("projectile_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORAGE_OPEN = reg("storage_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> DODGE = reg("dodge");
    public static final DeferredHolder<SoundEvent, SoundEvent> DECOY_POP = reg("decoy_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_SLAM = reg("world_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_OPEN = reg("world_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_CLOSE = reg("world_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINAL_IMPACT = reg("final_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOWMO = reg("slowmo");
    // Star Platinum
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STOP = reg("time_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_RESUME = reg("time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent> SP_SUMMON = reg("sp_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORA = reg("ora");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_FINGER = reg("star_finger");
    // D4C
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = reg("portal_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CLOSE = reg("portal_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> D4C_SUMMON = reg("d4c_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOVE_TRAIN = reg("love_train");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIMENSION_JUMP = reg("dimension_jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_FRACTURE = reg("reality_fracture");
    // Items
    public static final DeferredHolder<SoundEvent, SoundEvent> SP_ARROW_USE = reg("sp_arrow_use");
    public static final DeferredHolder<SoundEvent, SoundEvent> D4C_ARROW_USE = reg("d4c_arrow_use");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(StickyFingersMod.id(name)));
    }

    private ModSounds() {}
}

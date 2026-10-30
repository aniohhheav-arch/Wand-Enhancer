package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events; the .ogg files are synthesized by tools/generate_assets.py. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, InfinityGauntlet.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_PORTAL_OPEN = register("space_portal_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_PORTAL_CLOSE = register("space_portal_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_PORTAL_AMBIENT = register("space_portal_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_TELEPORT = register("space_teleport");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_BLINK = register("space_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_BOLT_FIRE = register("space_bolt_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_BOLT_IMPACT = register("space_bolt_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_RIFT = register("space_rift");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_SHOCKWAVE = register("space_shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_COMPRESSION = register("space_compression");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_ARC = register("space_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_PRISON = register("space_prison");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_SWAP = register("space_swap");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_FREEZE = register("time_freeze");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_RESUME = register("time_resume");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_FIELD_LOOP = register("time_field_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_FIELD_OPEN = register("time_field_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_ACCELERATE = register("time_accelerate");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_REWIND = register("time_rewind");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_ECHO = register("time_echo");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_LOOP = register("time_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_BARRIER = register("time_barrier");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SUSPEND = register("time_suspend");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_FRACTURE = register("time_fracture");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SHATTER = register("time_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUNTLET_CHARGE = register("gauntlet_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_INSERT = register("stone_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUNTLET_CAST = register("gauntlet_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUNTLET_FAIL = register("gauntlet_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUNTLET_SELECT = register("gauntlet_select");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(InfinityGauntlet.id(name)));
    }

    private ModSounds() {
    }
}

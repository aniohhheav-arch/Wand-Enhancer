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
    public static final DeferredHolder<SoundEvent, SoundEvent> NEXUS_POWER_ON = register("nexus_power_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEXUS_HUM = register("nexus_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEXUS_LINK = register("nexus_link");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEXUS_DISCONNECT = register("nexus_disconnect");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEXUS_STATIC = register("nexus_static");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEXUS_WARNING = register("nexus_warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREACH_OPEN = register("breach_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREACH_ENTER = register("breach_enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAMBER_AMBIENT = register("chamber_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_GLITCH = register("reality_glitch");
    public static final DeferredHolder<SoundEvent, SoundEvent> PANEL_PRESS = register("panel_press");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPECIMEN_LOST = register("specimen_lost");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_CHARGE = register("power_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_BEAM = register("power_beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_EXPLOSION = register("power_explosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_PUNCH_IMPACT = register("power_punch_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_WHOOSH = register("power_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_GROUND_CRACK = register("power_ground_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_NOVA_CHARGE = register("power_nova_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_NOVA_RELEASE = register("power_nova_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_LAUNCH = register("power_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_METEOR = register("power_meteor");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_OVERLOAD = register("power_overload");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_HIT = register("power_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_GRAVITY = register("power_gravity");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_ORB = register("power_orb");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_PLANETARY_CHARGE = register("power_planetary_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_PLANETARY_IMPACT = register("power_planetary_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_FLESH = register("impact_flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_BONE = register("impact_bone");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAD_POP = register("head_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_FINISHER = register("power_finisher");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_BASS = register("finisher_bass");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_SPACE_COLLAPSE = register("finisher_space_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_REALITY_SHIFT = register("finisher_reality_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_TIME_TICK = register("finisher_time_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_SOUL_WAIL = register("finisher_soul_wail");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_SOUL_ABSORB = register("finisher_soul_absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_GRAB = register("mind_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_THROW = register("mind_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_CONTROL = register("mind_control");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_BEAM_CHARGE = register("mind_beam_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_BEAM = register("mind_beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_PULSE = register("mind_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_MARK = register("mind_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_BURST = register("mind_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_FIELD = register("mind_field");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_ASTRAL_OUT = register("mind_astral_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_ASTRAL_IN = register("mind_astral_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_SHIELD = register("mind_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_SHIELD_HIT = register("mind_shield_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND_EYE = register("mind_eye");
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

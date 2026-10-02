package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Riftverse.MODID);
    public static final List<String> NAMES = new ArrayList<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_AMBIENT = reg("rift.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = reg("rift.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_ENTER = reg("rift.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = reg("portal.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_ENTER = reg("portal.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_GUN_FIRE = reg("portal_gun.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_AMBIENT = far("black_hole.ambient", 96f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_PULL = reg("black_hole.pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_COLLAPSE = far("black_hole.collapse", 128f);
    public static final DeferredHolder<SoundEvent, SoundEvent> WORMHOLE_TRAVEL = reg("wormhole.travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORMHOLE_EMERGE = reg("wormhole.emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNIVERSE_ARRIVE = reg("universe.arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_PULSE = reg("gravity.pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_BEAM = reg("gravity.beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_IMPLODE = far("singularity.implode", 64f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_SLASH = reg("blade.slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_DASH = reg("blade.dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENERGY_FIRE = reg("energy.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENERGY_HIT = reg("energy.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLITCH_NOISE = reg("glitch.noise");
    public static final DeferredHolder<SoundEvent, SoundEvent> JELLY_CHIME = reg("jelly.chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALE_CALL = far("whale.call", 96f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_HUM = reg("drone.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_HISS = reg("stalker.hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_SCREAM = reg("wraith.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_STEP = reg("sentinel.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_ROAR = far("warden.roar", 96f);
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_CHARGE = reg("warden.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIATHAN_ROAR = far("leviathan.roar", 128f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CREATURE_HURT = reg("creature.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CREATURE_DEATH = reg("creature.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_EQUIP = reg("armor.equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABILITY_ACTIVATE = reg("ability.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_SELECT = reg("ui.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MANIFEST = reg("ui.manifest");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENESIS_THEME = reg("genesis.theme");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENESIS_IGNITE = reg("genesis.ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENESIS_BANG = reg("genesis.bang");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENESIS_FORM = reg("genesis.form");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENESIS_LIFE = reg("genesis.life");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENESIS_GATE = reg("genesis.gate");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COSMIC = reg("music.cosmic");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_NEON = reg("music.neon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DREAM = reg("music.dream");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VOID = reg("music.void");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_NEXUS = reg("music.nexus");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_OCEAN = reg("music.ocean");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ANCIENT = reg("music.ancient");

    private RvSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        NAMES.add(name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Riftverse.id(name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> far(String name, float range) {
        NAMES.add(name);
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(Riftverse.id(name), range));
    }
}

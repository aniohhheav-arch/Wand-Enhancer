package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MysticArts.MODID);
    public static final List<String> NAMES = new ArrayList<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> CAST_MYSTIC = reg("cast.mystic");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST_COSMIC = reg("cast.cosmic");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIP_CRACK = reg("whip.crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIP_GRAB = reg("whip.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_UP = reg("shield.up");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_DOWN = reg("shield.down");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_IMPACT = reg("shield.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_CHARGE = reg("blast.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_FIRE = reg("blast.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_IMPACT = reg("blast.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> BINDING_CHAINS = reg("binding.chains");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = reg("portal.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CLOSE = reg("portal.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_TRAVEL = reg("portal.travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STOP = reg("time.stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_RESUME = reg("time.resume");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SLOW = reg("time.slow");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_ACCELERATE = reg("time.accelerate");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_REWIND = reg("time.rewind");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_LOOP = reg("time.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_ENTER = reg("mirror.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_EXIT = reg("mirror.exit");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_FOLD = reg("mirror.fold");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASTRAL_SEPARATE = reg("astral.separate");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASTRAL_RETURN = reg("astral.return");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEKINESIS_GRAB = reg("telekinesis.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEKINESIS_THROW = reg("telekinesis.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUSH = reg("push");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = reg("slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABSORB = reg("absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECT = reg("detect");
    public static final DeferredHolder<SoundEvent, SoundEvent> DASH = reg("dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT = reg("deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> BANISH = reg("banish");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLONE = reg("clone");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUNTLET_EQUIP = reg("gauntlet.equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_SOCKET = reg("stone.socket");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_SELECT = reg("stone.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_SPACE = reg("stone.space");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_MIND = reg("stone.mind");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_REALITY = reg("stone.reality");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_POWER = reg("stone.power");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_TIME = reg("stone.time");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_SOUL = reg("stone.soul");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM_COSMIC = reg("beam.cosmic");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM_MYSTIC = reg("beam.mystic");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCKWAVE = reg("shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> ULTIMATE_CHARGE = reg("ultimate.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_BUILD = reg("snap.build");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_CLICK = reg("snap.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_DUST = reg("snap.dust");
    public static final DeferredHolder<SoundEvent, SoundEvent> COOLDOWN_READY = reg("cooldown.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENERGY_EMPTY = reg("energy.empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_OPEN = reg("ui.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_HOVER = reg("ui.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_SELECT = reg("ui.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPECTRAL_HOWL = reg("spectral.howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> WISP_CHIME = reg("wisp.chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> RITUAL_COMPLETE = reg("ritual.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOAK_FLAP = reg("cloak.flap");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFORM = reg("transform");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_AMBIENT = far("portal.ambient", 32f);
    public static final DeferredHolder<SoundEvent, SoundEvent> COSMIC_EXPLOSION = far("cosmic.explosion", 128f);
    public static final DeferredHolder<SoundEvent, SoundEvent> ULTIMATE_RELEASE = far("ultimate.release", 128f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP_WAVE = far("snap.wave", 256f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = far("rift.open", 96f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SANCTUM_AMBIENT = far("sanctum.ambient", 24f);

    private MaSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        NAMES.add(name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(MysticArts.id(name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> far(String name, float range) {
        NAMES.add(name);
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(MysticArts.id(name), range));
    }
}

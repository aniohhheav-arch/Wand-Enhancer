package com.infinitemultiverse.core.vfx;

import com.infinitemultiverse.InfiniteMultiverse;
import net.minecraft.resources.ResourceLocation;

/** Effect identifiers shared by the server (which triggers them) and the client (which renders them). */
public final class VfxIds {
    public static final ResourceLocation PHASE_STEP = InfiniteMultiverse.id("phase_step");
    public static final ResourceLocation KINETIC_LEAP = InfiniteMultiverse.id("kinetic_leap");
    public static final ResourceLocation SHOCKWAVE = InfiniteMultiverse.id("shockwave");
    public static final ResourceLocation AEGIS_PULSE = InfiniteMultiverse.id("aegis_pulse");
    public static final ResourceLocation AEGIS_IMPACT = InfiniteMultiverse.id("aegis_impact");
    public static final ResourceLocation AEGIS_COLLAPSE = InfiniteMultiverse.id("aegis_collapse");
    public static final ResourceLocation TEMPORAL_DRAG = InfiniteMultiverse.id("temporal_drag");
    public static final ResourceLocation STAND_SUMMON = InfiniteMultiverse.id("stand_summon");
    public static final ResourceLocation STAND_DISMISS = InfiniteMultiverse.id("stand_dismiss");
    public static final ResourceLocation STAND_AWAKEN = InfiniteMultiverse.id("stand_awaken");
    public static final ResourceLocation STAND_PUNCH = InfiniteMultiverse.id("stand_punch");
    public static final ResourceLocation STAND_HEAVY = InfiniteMultiverse.id("stand_heavy");
    public static final ResourceLocation TIME_STOP = InfiniteMultiverse.id("time_stop");
    public static final ResourceLocation TIME_RESUME = InfiniteMultiverse.id("time_resume");
    public static final ResourceLocation KQ_MARK = InfiniteMultiverse.id("kq_mark");
    public static final ResourceLocation REWIND = InfiniteMultiverse.id("rewind");
    public static final ResourceLocation HEAL_PULSE = InfiniteMultiverse.id("heal_pulse");
    public static final ResourceLocation EPITAPH = InfiniteMultiverse.id("epitaph");
    public static final ResourceLocation TIME_ERASE = InfiniteMultiverse.id("time_erase");
    public static final ResourceLocation AFTERIMAGE = InfiniteMultiverse.id("afterimage");
    // Generic, colour-parametrised effects (colour travels in the payload's scale field).
    public static final ResourceLocation BEAM = InfiniteMultiverse.id("beam");
    public static final ResourceLocation BEAM_HEAVY = InfiniteMultiverse.id("beam_heavy");
    public static final ResourceLocation SLASH = InfiniteMultiverse.id("slash");
    public static final ResourceLocation ORB = InfiniteMultiverse.id("orb");
    public static final ResourceLocation BURST = InfiniteMultiverse.id("burst");
    public static final ResourceLocation BOLT = InfiniteMultiverse.id("bolt");
    public static final ResourceLocation AURA = InfiniteMultiverse.id("aura");
    public static final ResourceLocation MANDALA = InfiniteMultiverse.id("mandala");
    public static final ResourceLocation PORTAL_RING = InfiniteMultiverse.id("portal_ring");
    public static final ResourceLocation FROST = InfiniteMultiverse.id("frost");
    public static final ResourceLocation DOMAIN_OPEN = InfiniteMultiverse.id("domain_open");
    public static final ResourceLocation DOMAIN_CLOSE = InfiniteMultiverse.id("domain_close");

    private VfxIds() {
    }
}

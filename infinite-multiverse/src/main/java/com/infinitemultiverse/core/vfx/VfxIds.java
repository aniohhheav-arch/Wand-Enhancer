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

    private VfxIds() {
    }
}

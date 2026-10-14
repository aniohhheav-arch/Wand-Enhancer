package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, StickyFingersMod.MODID);

    /** Metallic star sparks thrown off a zipper slider. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ZIPPER_SPARK = reg("zipper_spark");
    /** Tumbling golden zipper teeth. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ZIPPER_TOOTH = reg("zipper_tooth");
    /** Glowing motes drifting out of the space inside a zipper. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_MOTE = reg("void_mote");
    /** Short-lived cyan seam light. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEAM_GLOW = reg("seam_glow");
    /** Expanding shock ring used by Stand punches. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PUNCH_IMPACT = reg("punch_impact");
    /** Stand-coloured afterimage streak. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AFTERIMAGE = reg("afterimage");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> reg(String name) {
        return REGISTER.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {}
}

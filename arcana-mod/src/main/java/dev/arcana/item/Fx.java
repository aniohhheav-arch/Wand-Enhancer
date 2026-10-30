package dev.arcana.item;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/** Cosmetic particle styles for casting trails and charging. */
public enum Fx {
    DEFAULT("default", null),
    STARS("stars", ParticleTypes.END_ROD),
    EMBERS("embers", ParticleTypes.SMALL_FLAME),
    SOULS("souls", ParticleTypes.SOUL_FIRE_FLAME),
    GLYPHS("glyphs", ParticleTypes.ENCHANT),
    FIREFLIES("fireflies", ParticleTypes.GLOW);

    public final String id;
    public final ParticleOptions particle;

    Fx(String id, ParticleOptions particle) {
        this.id = id;
        this.particle = particle;
    }

    public static Fx byIndex(int i) {
        Fx[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

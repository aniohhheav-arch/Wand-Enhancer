package dev.riftverse.block;

import dev.riftverse.universe.Archetype;
import net.minecraft.util.StringRepresentable;

/** Every rift tears open differently and leads somewhere different. The ordinal doubles as the shader style id. */
public enum RiftType implements StringRepresentable {
    AZURE("azure", 0x3DA5FF, 0xB8F4FF, null),
    CRIMSON("crimson", 0xFF2440, 0xFFB040, new Archetype[] {Archetype.CINDER, Archetype.ASHEN, Archetype.NEON_SPRAWL, Archetype.HOLLOW}),
    VERDANT("verdant", 0x3DFF6E, 0xE6FF80, new Archetype[] {Archetype.XENOFLORA, Archetype.SKYSHATTER, Archetype.THALASSIC, Archetype.SOMNIUM}),
    VOID("void", 0x8A3AFF, 0x1A0030, new Archetype[] {Archetype.HOLLOW, Archetype.CORRUPTED, Archetype.ASHEN}),
    PRISMATIC("prismatic", 0xFF7AF0, 0x7DF9FF, new Archetype[] {Archetype.PRISMATIC, Archetype.RIME, Archetype.ELDER}),
    NEXUS("nexus", 0xFFC14D, 0xFFFFFF, null),
    GLITCH("glitch", 0x39FF14, 0xFF0055, new Archetype[] {Archetype.CORRUPTED, Archetype.NEON_SPRAWL}),
    STELLAR("stellar", 0x8F6BFF, 0xFF4FD8, new Archetype[] {Archetype.ASTRAL, Archetype.INVERTED, Archetype.SKYSHATTER}),
    RETURN("return", 0xE8F4FF, 0x7FB8FF, null);

    private final String name;
    public final int colorA;
    public final int colorB;
    private final Archetype[] destinations;

    RiftType(String name, int colorA, int colorB, Archetype[] destinations) {
        this.name = name;
        this.colorA = colorA;
        this.colorB = colorB;
        this.destinations = destinations;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Archetypes this rift prefers, or null for "any reality". */
    public Archetype[] destinations() {
        return destinations;
    }

    public static RiftType byId(int id) {
        RiftType[] v = values();
        return v[Math.floorMod(id, v.length)];
    }
}

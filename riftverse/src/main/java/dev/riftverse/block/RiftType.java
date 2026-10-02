package dev.riftverse.block;

import dev.riftverse.universe.Archetype;
import net.minecraft.util.StringRepresentable;

/** Every rift tears open differently and leads somewhere different. Each carries its own shader style id. */
public enum RiftType implements StringRepresentable {
    AZURE("azure", 0x3DA5FF, 0xB8F4FF, 0, null),
    CRIMSON("crimson", 0xFF2440, 0xFFB040, 1, new Archetype[] {Archetype.CINDER, Archetype.ASHEN, Archetype.NEON_SPRAWL, Archetype.HOLLOW}),
    VERDANT("verdant", 0x3DFF6E, 0xE6FF80, 2, new Archetype[] {Archetype.XENOFLORA, Archetype.SKYSHATTER, Archetype.THALASSIC, Archetype.SOMNIUM}),
    VOID("void", 0x8A3AFF, 0x1A0030, 3, new Archetype[] {Archetype.HOLLOW, Archetype.CORRUPTED, Archetype.ASHEN}),
    PRISMATIC("prismatic", 0xFF7AF0, 0x7DF9FF, 4, new Archetype[] {Archetype.PRISMATIC, Archetype.RIME, Archetype.ELDER}),
    NEXUS("nexus", 0xFFC14D, 0xFFFFFF, 5, null),
    GLITCH("glitch", 0x39FF14, 0xFF0055, 6, new Archetype[] {Archetype.CORRUPTED, Archetype.NEON_SPRAWL}),
    STELLAR("stellar", 0x8F6BFF, 0xFF4FD8, 7, new Archetype[] {Archetype.ASTRAL, Archetype.INVERTED, Archetype.SKYSHATTER}),
    RETURN("return", 0xE8F4FF, 0x7FB8FF, 8, null),
    // --- expansion rifts: each reuses an existing shader style (0-7) that suits its colours ---
    SOLAR("solar", 0xFFD24D, 0xFFF0A0, 5, new Archetype[] {Archetype.SUNSCAR, Archetype.RADIANCE, Archetype.ELDER}),
    ABYSSAL("abyssal", 0x1FA0C8, 0x80FFE0, 0, new Archetype[] {Archetype.CORAL, Archetype.THALASSIC, Archetype.MIRE}),
    FUNGAL("fungal", 0xB060FF, 0x80FF60, 2, new Archetype[] {Archetype.MYCELIA, Archetype.XENOFLORA, Archetype.BLOOM}),
    SANGUINE("sanguine", 0xFF1030, 0xFF8040, 1, new Archetype[] {Archetype.SANGUINE, Archetype.CINDER, Archetype.HOLLOW}),
    BRASS("brass", 0xC88A3A, 0xFFD080, 5, new Archetype[] {Archetype.CLOCKWORK, Archetype.FERROUS, Archetype.NEON_SPRAWL}),
    SACCHARINE("saccharine", 0xFF8FD0, 0xB0E0FF, 4, new Archetype[] {Archetype.CONFECTION, Archetype.SOMNIUM, Archetype.BLOOM}),
    TEMPEST("tempest", 0x5A8AFF, 0xC0E0FF, 0, new Archetype[] {Archetype.TEMPEST, Archetype.SKYSHATTER, Archetype.INVERTED}),
    UMBRAL("umbral", 0x6A2AC8, 0x200040, 3, new Archetype[] {Archetype.OBSIDIAN, Archetype.HOLLOW, Archetype.ASHEN}),
    PATINA("patina", 0x3AC8A0, 0xC8E080, 2, new Archetype[] {Archetype.VERDIGRIS, Archetype.MESA, Archetype.ELDER});

    private final String name;
    public final int colorA;
    public final int colorB;
    private final int shaderStyle;
    private final Archetype[] destinations;

    RiftType(String name, int colorA, int colorB, int shaderStyle, Archetype[] destinations) {
        this.name = name;
        this.colorA = colorA;
        this.colorB = colorB;
        this.shaderStyle = shaderStyle;
        this.destinations = destinations;
    }

    /** The shader style id to render this rift with (decoupled from the ordinal to avoid clashing with portal/gate styles). */
    public int shaderStyle() {
        return shaderStyle;
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

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
    SOLAR("solar", 0xFFD24D, 0xFFF0A0, 13, new Archetype[] {Archetype.SUNSCAR, Archetype.RADIANCE, Archetype.ELDER}),
    ABYSSAL("abyssal", 0x1FA0C8, 0x80FFE0, 14, new Archetype[] {Archetype.CORAL, Archetype.THALASSIC, Archetype.MIRE}),
    FUNGAL("fungal", 0xB060FF, 0x80FF60, 15, new Archetype[] {Archetype.MYCELIA, Archetype.XENOFLORA, Archetype.BLOOM}),
    SANGUINE("sanguine", 0xFF1030, 0xFF8040, 19, new Archetype[] {Archetype.SANGUINE, Archetype.CINDER, Archetype.HOLLOW}),
    BRASS("brass", 0xC88A3A, 0xFFD080, 16, new Archetype[] {Archetype.CLOCKWORK, Archetype.FERROUS, Archetype.NEON_SPRAWL}),
    SACCHARINE("saccharine", 0xFF8FD0, 0xB0E0FF, 4, new Archetype[] {Archetype.CONFECTION, Archetype.SOMNIUM, Archetype.BLOOM}),
    TEMPEST("tempest", 0x5A8AFF, 0xC0E0FF, 23, new Archetype[] {Archetype.TEMPEST, Archetype.SKYSHATTER, Archetype.INVERTED}),
    UMBRAL("umbral", 0x6A2AC8, 0x200040, 3, new Archetype[] {Archetype.OBSIDIAN, Archetype.HOLLOW, Archetype.ASHEN}),
    PATINA("patina", 0x3AC8A0, 0xC8E080, 22, new Archetype[] {Archetype.VERDIGRIS, Archetype.MESA, Archetype.ELDER}),
    AURORAL("auroral", 0x40FFB0, 0xB060FF, 20, new Archetype[] {Archetype.AURORA, Archetype.RIME, Archetype.MIRROR}),
    MOLTEN("molten", 0xFF4A10, 0xFFD040, 18, new Archetype[] {Archetype.MAGMA, Archetype.WASTELAND, Archetype.CINDER}),
    PRIMAL("primal", 0x3ABF3A, 0xFFE060, 2, new Archetype[] {Archetype.PRIMEVAL, Archetype.SAVANNA, Archetype.HIVE}),
    CHROME("chrome", 0xE0F0FF, 0x80E0FF, 22, new Archetype[] {Archetype.CHROME, Archetype.MIRROR, Archetype.NEON_SPRAWL}),
    SCULK("sculk", 0x0A8A9A, 0x002030, 19, new Archetype[] {Archetype.DEEPDARK, Archetype.GEODE, Archetype.HOLLOW}),
    NEBULAR("nebular", 0xC050FF, 0x40A0FF, 7, new Archetype[] {Archetype.NEBULA, Archetype.LUNAR, Archetype.ASTRAL}),
    FORGE("forge", 0xFF8A3A, 0x9AC8FF, 16, new Archetype[] {Archetype.STARFORGE, Archetype.EMBERSTEPPE, Archetype.MOLTENSEA}),
    FROST("frost", 0xE0FFFF, 0x80C0FF, 17, new Archetype[] {Archetype.FROSTGLASS, Archetype.CRYSTALOCEAN, Archetype.DUSKHIGHLANDS, Archetype.CORALKING}),
    WEALD("weald", 0x20D0B0, 0xD02040, 15, new Archetype[] {Archetype.CRIMSONWEALD, Archetype.WARPEDWEALD, Archetype.PETRIFIED, Archetype.NEONJUNGLE}),
    CELESTIAL("celestial", 0xFFF0C8, 0xFFC14D, 13, new Archetype[] {Archetype.CELESTIAL, Archetype.CLOUDKINGDOM, Archetype.GOLDENTEMPLE, Archetype.RAINBOW, Archetype.PASTEL, Archetype.LUNARCOLONY}),
    RUNIC("runic", 0xFFC14D, 0x8F6BFF, 21, new Archetype[] {Archetype.RUNIC, Archetype.DROWNED, Archetype.ECHO, Archetype.VOIDGLASS, Archetype.TOXIC}),
    ARTISAN("artisan", 0xFFD0A0, 0x40C0FF, 11, new Archetype[] {Archetype.SKETCHBOOK, Archetype.NOIR, Archetype.PSYCHEDELIA, Archetype.COMICVERSE, Archetype.PIXELWORLD, Archetype.CANVAS, Archetype.PAPERCRAFT, Archetype.NEONGRID, Archetype.REVERIE, Archetype.CORRUPTDATA, Archetype.GRAPHITE, Archetype.CYBERROT, Archetype.DREAMWASH, Archetype.POPTRIP}),
    ANOMALOUS("anomalous", 0xFFFFFF, 0x000000, 12, new Archetype[] {Archetype.BLUEPRINT, Archetype.CLAYMATION, Archetype.WIREFRAME, Archetype.INKWASH, Archetype.GLASSWORLD, Archetype.WATERCOLOR, Archetype.NEGATIVE, Archetype.SILHOUETTE, Archetype.VHSTAPE, Archetype.ASTRALPLANE, Archetype.ORIGAMI, Archetype.XRAY, Archetype.FRACTAL, Archetype.COMICPANELS, Archetype.TAPEHORROR, Archetype.PRISMPIXEL});

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

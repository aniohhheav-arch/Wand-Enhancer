package com.wandcraft.magic;

/** The ten schools of magic. Colors are 0xRRGGBB accents used by the HUD, wheel and particles. */
public enum Category {
    ELEMENTAL("Elemental", 0xFF7A1A, Glyph.FLAME),
    ARCANE("Arcane", 0x6C8BFF, Glyph.RUNE),
    FORCE("Force", 0xC9D6E8, Glyph.HAND),
    NATURE("Nature", 0x57C84D, Glyph.LEAF),
    DEFENSE("Defense", 0x4FB3FF, Glyph.SHIELD),
    ILLUSION("Illusion", 0xD27BFF, Glyph.MIRROR),
    SPACE("Space", 0x8A3CFF, Glyph.PORTAL),
    LIGHT("Light", 0xFFE06B, Glyph.SUN),
    DARK("Dark Arts", 0x7A2BD6, Glyph.SKULL),
    FORBIDDEN("Forbidden", 0xE02450, Glyph.FORBID);

    public final String display;
    public final int color;
    public final Glyph icon;

    Category(String display, int color, Glyph icon) {
        this.display = display;
        this.color = color;
        this.icon = icon;
    }
}

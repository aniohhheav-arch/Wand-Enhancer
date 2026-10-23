package com.wandcraft.magic.spells;

/** Elemental Magic: fire, ice, lightning and earth. */
public final class ElementalSpells {
    private ElementalSpells() {}

    public static void register() {
        FireSpells.register();
        IceSpells.register();
        StormSpells.register();
        EarthSpells.register();
    }
}

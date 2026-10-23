package com.wandcraft.magic.spells;

/** Registers the Force, Nature, Defense and Dark Arts schools (package-private classes live next to it). */
public final class OtherSchools {
    private OtherSchools() {}

    public static void register() {
        ForceSpells.register();
        NatureSpells.register();
        DefenseSpells.register();
        DarkSpells.register();
    }
}

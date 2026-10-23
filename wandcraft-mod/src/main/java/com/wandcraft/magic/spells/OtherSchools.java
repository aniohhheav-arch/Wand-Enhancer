package com.wandcraft.magic.spells;

/** Registers the Force, Nature, Defense, Dark Arts, Illusion, Space, Light and Forbidden schools (package-private classes live next to it). */
public final class OtherSchools {
    private OtherSchools() {}

    public static void register() {
        ForceSpells.register();
        NatureSpells.register();
        DefenseSpells.register();
        DarkSpells.register();
        IllusionSpells.register();
        SpaceSpells.register();
        LightSpells.register();
        TimeSpells.register();
        ForbiddenSpells.register();
    }
}

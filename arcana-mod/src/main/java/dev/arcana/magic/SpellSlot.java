package dev.arcana.magic;

public enum SpellSlot {
    PRIMARY, SECONDARY, SPECIAL, ULTIMATE;

    public static SpellSlot byOrdinal(int i) {
        SpellSlot[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

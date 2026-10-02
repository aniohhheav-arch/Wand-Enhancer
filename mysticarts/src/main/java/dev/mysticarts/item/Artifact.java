package dev.mysticarts.item;

/** The four artifact slots every sorcerer carries in addition to armour. */
public enum Artifact {
    CLOAK("cloak", 0xD8263A),
    AMULET("amulet", 0x22E06A),
    RING("ring", 0xFF9A2E),
    BRACERS("bracers", 0xE8D6B0);

    public final String id;
    public final int color;

    Artifact(String id, int color) {
        this.id = id;
        this.color = color;
    }
}

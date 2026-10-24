package dev.telephoneorder.block;

import net.minecraft.util.StringRepresentable;

/** Visual state of a telephone block (selects the 3D model: handset position, ring wobble, dial rotation). */
public enum PhoneStateProp implements StringRepresentable {
    IDLE("idle"), RING_A("ring_a"), RING_B("ring_b"), OFF_HOOK("off_hook"), DIAL_A("dial_a"), DIAL_B("dial_b"), DIAL_C("dial_c");

    private final String name;
    PhoneStateProp(String n) { name = n; }
    @Override public String getSerializedName() { return name; }
    public boolean ringing() { return this == RING_A || this == RING_B; }
    public boolean offHook() { return this == OFF_HOOK || this == DIAL_A || this == DIAL_B || this == DIAL_C; }
}

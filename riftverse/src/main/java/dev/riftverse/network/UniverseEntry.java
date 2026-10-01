package dev.riftverse.network;

import dev.riftverse.universe.UniverseSpec;
import net.minecraft.network.FriendlyByteBuf;

/** A compact catalogue line shown in the Multiverse browser. */
public record UniverseEntry(long id, String name, String designation, int archetype, String description, int color, String prompt, int category) {
    public static final int PRIME = 0;
    public static final int DISCOVERED = 1;
    public static final int MANIFESTED = 2;

    public static UniverseEntry of(UniverseSpec spec, int category) {
        return new UniverseEntry(spec.id.pack(), spec.name, spec.id.designation(), spec.archetype.ordinal(), spec.describe(), spec.accent, spec.prompt, category);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(id);
        buf.writeUtf(name, 128);
        buf.writeUtf(designation, 32);
        buf.writeVarInt(archetype);
        buf.writeUtf(description, 512);
        buf.writeInt(color);
        buf.writeUtf(prompt, 256);
        buf.writeVarInt(category);
    }

    public static UniverseEntry read(FriendlyByteBuf buf) {
        return new UniverseEntry(buf.readLong(), buf.readUtf(128), buf.readUtf(32), buf.readVarInt(), buf.readUtf(512), buf.readInt(), buf.readUtf(256), buf.readVarInt());
    }
}

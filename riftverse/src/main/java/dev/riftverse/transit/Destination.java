package dev.riftverse.transit;

import dev.riftverse.block.RiftType;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Where a rift, portal or black hole leads. Random kinds are resolved at the moment of travel. */
public record Destination(Kind kind, long universe, int archetype, String dimension, BlockPos pos, int flavor) {
    public enum Kind { UNIVERSE, ARCHETYPE, RANDOM, FLAVOR, NEXUS, LOCATION, HOME }

    public static Destination universe(UniverseId id) {
        return new Destination(Kind.UNIVERSE, id.pack(), 0, "", BlockPos.ZERO, 0);
    }

    public static Destination archetype(Archetype a) {
        return new Destination(Kind.ARCHETYPE, 0, a.ordinal(), "", BlockPos.ZERO, 0);
    }

    public static Destination random() {
        return new Destination(Kind.RANDOM, 0, 0, "", BlockPos.ZERO, 0);
    }

    public static Destination flavor(RiftType type) {
        if (type == RiftType.NEXUS) return nexus();
        return new Destination(Kind.FLAVOR, 0, 0, "", BlockPos.ZERO, type.ordinal());
    }

    public static Destination nexus() {
        return new Destination(Kind.NEXUS, 0, 0, "", BlockPos.ZERO, 0);
    }

    public static Destination home() {
        return new Destination(Kind.HOME, 0, 0, "", BlockPos.ZERO, 0);
    }

    public static Destination location(ResourceKey<Level> dim, BlockPos pos) {
        return new Destination(Kind.LOCATION, 0, 0, dim.location().toString(), pos.immutable(), 0);
    }

    @Nullable
    public ResourceKey<Level> dimensionKey() {
        ResourceLocation rl = ResourceLocation.tryParse(dimension);
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("kind", kind.ordinal());
        t.putLong("universe", universe);
        t.putInt("archetype", archetype);
        t.putString("dimension", dimension);
        t.putLong("pos", pos.asLong());
        t.putInt("flavor", flavor);
        return t;
    }

    public static Destination load(CompoundTag t) {
        Kind[] kinds = Kind.values();
        Kind kind = kinds[Math.floorMod(t.getInt("kind"), kinds.length)];
        return new Destination(kind, t.getLong("universe"), t.getInt("archetype"), t.getString("dimension"), BlockPos.of(t.getLong("pos")), t.getInt("flavor"));
    }

    public String describe() {
        return switch (kind) {
            case UNIVERSE -> UniverseId.unpack(universe).designation();
            case ARCHETYPE -> Archetype.byId(archetype).displayName;
            case RANDOM -> "Uncharted Reality";
            case FLAVOR -> "A " + RiftType.byId(flavor).getSerializedName() + " reality";
            case NEXUS -> "The Multiverse Nexus";
            case LOCATION -> "Return Passage";
            case HOME -> "Home";
        };
    }
}

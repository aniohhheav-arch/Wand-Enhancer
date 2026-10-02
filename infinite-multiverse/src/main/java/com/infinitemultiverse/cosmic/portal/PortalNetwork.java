package com.infinitemultiverse.cosmic.portal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Every player's portal pair, saved with the world. Transit uses these records (not the partner entity), so a portal
 * works even when its partner is in an unloaded chunk or another dimension.
 */
public final class PortalNetwork extends SavedData {
    public record End(ResourceKey<Level> dimension, Vec3 center, Direction facing, Direction up, UUID entity) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dimension.location().toString());
            t.putDouble("x", center.x);
            t.putDouble("y", center.y);
            t.putDouble("z", center.z);
            t.putInt("facing", facing.get3DDataValue());
            t.putInt("up", up.get3DDataValue());
            t.putUUID("entity", entity);
            return t;
        }

        static End load(CompoundTag t) {
            return new End(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                    new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")), Direction.from3DDataValue(t.getInt("facing")),
                    Direction.from3DDataValue(t.getInt("up")), t.getUUID("entity"));
        }
    }

    private final Map<UUID, End[]> pairs = new HashMap<>();

    public static PortalNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PortalNetwork::new, PortalNetwork::load, null), "infinitemultiverse_portals");
    }

    @Nullable
    public End get(UUID owner, int color) {
        End[] pair = pairs.get(owner);
        return pair == null ? null : pair[color];
    }

    @Nullable
    public End partner(UUID owner, int color) {
        return get(owner, 1 - color);
    }

    public void set(UUID owner, int color, @Nullable End end) {
        pairs.computeIfAbsent(owner, k -> new End[2])[color] = end;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        pairs.forEach((owner, pair) -> {
            CompoundTag p = new CompoundTag();
            for (int c = 0; c < 2; c++) {
                if (pair[c] != null) {
                    p.put(Integer.toString(c), pair[c].save());
                }
            }
            all.put(owner.toString(), p);
        });
        tag.put("pairs", all);
        return tag;
    }

    private static PortalNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        PortalNetwork net = new PortalNetwork();
        CompoundTag all = tag.getCompound("pairs");
        for (String key : all.getAllKeys()) {
            CompoundTag p = all.getCompound(key);
            End[] pair = new End[2];
            for (int c = 0; c < 2; c++) {
                if (p.contains(Integer.toString(c))) {
                    pair[c] = End.load(p.getCompound(Integer.toString(c)));
                }
            }
            net.pairs.put(UUID.fromString(key), pair);
        }
        return net;
    }
}

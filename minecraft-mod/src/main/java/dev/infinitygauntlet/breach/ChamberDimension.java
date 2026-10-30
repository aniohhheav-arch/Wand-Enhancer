package dev.infinitygauntlet.breach;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The observation chamber dimension: an empty void in which every player gets their own white room ("cell"),
 * 1024 blocks from the next so rooms never meet. White fog (client side) dissolves the room's edges into an
 * infinite-looking white expanse; invisible barriers keep visitors on the floor.
 * <p>
 * Cell layout, relative to its origin (floor surface at the origin's y): the player arrives at z = -6 facing +z,
 * the return rift hangs behind them at z = -9, the control panel floats at z = -2.5 and the specimen's containment
 * unit stands at z = +4, with the survey plinth beside it at x = +3.6.
 */
public final class ChamberDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("observation_chamber"));
    public static final int FLOOR_Y = 64;
    public static final int HALF_SIZE = 20;
    private static final int SPACING = 1024;

    public static boolean isChamber(Level level) {
        return level.dimension() == KEY;
    }

    public static Vec3 origin(int cell) {
        return new Vec3(cell * SPACING + 0.5, FLOOR_Y, 0.5);
    }

    public static Vec3 arrival(int cell) {
        return origin(cell).add(0, 0, -6);
    }

    public static Vec3 returnRift(int cell) {
        return origin(cell).add(0, 1.5, -9.5);
    }

    public static Vec3 panel(int cell) {
        return origin(cell).add(0, 0.95, -3);
    }

    public static Vec3 containment(int cell) {
        return origin(cell).add(0, 0, 3);
    }

    /** Builds the cell's floor and barrier ring if it has not been built yet. */
    public static void ensureBuilt(ServerLevel level, int cell) {
        BlockPos o = BlockPos.containing(origin(cell)).below();
        BlockState floor = ModBlocks.CHAMBER_FLOOR.get().defaultBlockState();
        if (level.getBlockState(o).is(floor.getBlock())) {
            return;
        }
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (int dx = -HALF_SIZE - 1; dx <= HALF_SIZE + 1; dx++) {
            for (int dz = -HALF_SIZE - 1; dz <= HALF_SIZE + 1; dz++) {
                boolean edge = Math.abs(dx) == HALF_SIZE + 1 || Math.abs(dz) == HALF_SIZE + 1;
                if (edge) {
                    for (int dy = 0; dy <= 5; dy++) {
                        level.setBlock(o.offset(dx, dy, dz), barrier, 2);
                    }
                } else {
                    level.setBlock(o.offset(dx, 0, dz), floor, 2);
                }
            }
        }
    }

    /** Persistent player → cell assignment. */
    public static int cellFor(MinecraftServer server, UUID player) {
        Cells cells = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Cells::new, Cells::load, null),
                "infinitygauntlet_chamber_cells");
        Integer cell = cells.cells.get(player);
        if (cell == null) {
            cell = cells.cells.size();
            cells.cells.put(player, cell);
            cells.setDirty();
        }
        return cell;
    }

    private static final class Cells extends SavedData {
        final Map<UUID, Integer> cells = new HashMap<>();

        static Cells load(CompoundTag tag, HolderLookup.Provider registries) {
            Cells c = new Cells();
            for (String key : tag.getAllKeys()) {
                c.cells.put(UUID.fromString(key), tag.getInt(key));
            }
            return c;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            cells.forEach((uuid, cell) -> tag.putInt(uuid.toString(), cell));
            return tag;
        }
    }


    private ChamberDimension() {
    }
}

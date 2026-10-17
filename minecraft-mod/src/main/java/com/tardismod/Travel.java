package com.tardismod;

import com.tardismod.block.TardisBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.List;

/** Console-driven travel: the exterior police box dematerializes and rematerializes at the chosen destination. */
public final class Travel {
    public static final List<String> DIMENSIONS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    public static final String[] NAMES = {"Overworld", "The Nether", "The End"};

    private Travel() {}

    public static String describe(TardisBlockEntity door) {
        int i = Math.max(0, DIMENSIONS.indexOf(door.destDimension()));
        return NAMES[i] + " @ " + door.destX() + ", " + door.destZ();
    }

    public static void cycleDimension(TardisBlockEntity door) {
        int i = (DIMENSIONS.indexOf(door.destDimension()) + 1) % DIMENSIONS.size();
        door.setDestination(DIMENSIONS.get(i), door.destX(), door.destZ());
    }

    public static void randomise(TardisBlockEntity door, net.minecraft.util.RandomSource r) {
        door.setDestination(door.destDimension(), r.nextInt(4001) - 2000, r.nextInt(4001) - 2000);
    }

    /** Safe standing Y at x,z (or -1 if none): scans down from the top for solid, non-liquid ground with two blocks of air above. */
    static int safeY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
        int top = level.dimension() == Level.NETHER ? 120 : level.getMaxY() - 2;
        for (int y = top; y > level.getMinY() + 4; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(p.below());
            if (below.isSolid() && level.getFluidState(p.below()).isEmpty() && !below.is(net.minecraft.tags.BlockTags.LEAVES)
                && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return y;
        }
        return -1;
    }

    /** Returns a human-readable result. Runs on the server thread. */
    public static String fly(ServerLevel interior, TardisBlockEntity door) {
        var server = interior.getServer();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(door.destDimension()));
        ServerLevel target = server.getLevel(key);
        if (target == null) return "Destination dimension unavailable.";
        int x = door.destX(), z = door.destZ(), y = safeY(target, x, z);
        boolean groundless = y < 0;
        if (groundless) { // no ground (e.g. the End void or a Nether lava lake): build a small landing pad so the TARDIS is never lost
            y = target.dimension() == Level.END ? 64 : 80;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) target.setBlock(new BlockPos(x + dx, y - 1, z + dz), Blocks.OBSIDIAN.defaultBlockState(), 3);
            for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) target.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
        }
        // dematerialize at the old location, if the box is still there
        if (!door.returnDimension().isEmpty()) {
            ServerLevel from = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(door.returnDimension())));
            if (from != null && from.getBlockState(door.returnPos()).getBlock() == TardisMod.TARDIS_BLOCK) {
                from.playSound(null, door.returnPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
                from.removeBlock(door.returnPos(), false);
            }
        }
        BlockPos land = new BlockPos(x, y, z);
        BlockState box = TardisMod.TARDIS_BLOCK.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        target.setBlock(land, box, 3);
        if (target.getBlockEntity(land) instanceof TardisBlockEntity ext) ext.setOpen(false);
        target.playSound(null, land, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
        door.setReturn(key.identifier().toString(), land);
        for (ServerPlayer p : interior.players()) p.sendSystemMessage(Component.literal("Materialized: " + describe(door) + " (y=" + y + ")"), true);
        return "Materialized at " + describe(door) + (groundless ? " [built a landing pad: no ground]" : ".");
    }
}

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

    private static final int FLIGHT_TICKS = 160;
    private record Pending(ServerLevel interior, BlockPos doorPos, int[] left) {}
    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

    private Travel() {}

    /** Begin a flight: the vortex plays for everyone in the interior, then the box materializes at the destination. */
    public static String start(ServerLevel interior, TardisBlockEntity door) {
        for (Pending p : PENDING) if (p.doorPos().equals(door.getBlockPos())) return "The TARDIS is already in flight.";
        PENDING.add(new Pending(interior, door.getBlockPos(), new int[]{FLIGHT_TICKS}));
        for (ServerPlayer p : interior.players()) {
            if (Interior.idOf(p.blockPosition()) == Interior.idOf(door.getBlockPos())) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new FlightPayload(FLIGHT_TICKS));
        }
        interior.playSound(null, door.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.5f);
        return "Dematerializing — entering the time vortex…";
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (var it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next(); int n = --p.left()[0];
            if (n % 20 == 0 && n > 0) p.interior().playSound(null, p.doorPos(), SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 1.2f, 0.7f + (FLIGHT_TICKS - n) / 400f);
            if (n <= 0) {
                it.remove();
                if (p.interior().getBlockEntity(p.doorPos()) instanceof TardisBlockEntity door) {
                    String r = fly(p.interior(), door);
                    org.slf4j.LoggerFactory.getLogger("tardismod").info("[tardis] flight finished: {} (door {})", r, p.doorPos().toShortString());
                    for (ServerPlayer pl : p.interior().players()) if (Interior.idOf(pl.blockPosition()) == Interior.idOf(p.doorPos())) pl.sendSystemMessage(Component.literal(r), true);
                }
            }
        }
    }

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
        if (target.getBlockEntity(land) instanceof TardisBlockEntity ext) { ext.setInterior(Interior.idOf(door.getBlockPos())); ext.setOpen(false); }
        target.playSound(null, land, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
        door.setReturn(key.identifier().toString(), land);
        return "Materialized at " + describe(door) + (groundless ? " [built a landing pad: no ground]" : ".");
    }
}

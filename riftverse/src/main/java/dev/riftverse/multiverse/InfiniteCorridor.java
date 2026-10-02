package dev.riftverse.multiverse;

import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftType;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.MusicKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.util.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The Infinite Corridor: a hallway suspended in the void of slot (0, 0) of the Expanse, running for the whole width of
 * the slot. Every 32 blocks a recessed doorway holds a rift into a different family of realities (and now and then one
 * back to the Nexus). It is generated procedurally like any universe, so it never ends and costs nothing to store.
 */
public final class InfiniteCorridor {
    public static final UniverseId ID = new UniverseId(0, 0);
    public static final int FLOOR = 64;
    public static final int CEILING = 72;
    public static final int HALF_WIDTH = 4;
    public static final int DOOR_SPACING = 32;
    private static final RiftType[] DOOR_TYPES = buildDoorTypes();

    private static volatile UniverseSpec spec;

    private InfiniteCorridor() {}

    private static RiftType[] buildDoorTypes() {
        java.util.List<RiftType> list = new java.util.ArrayList<>();
        for (RiftType t : RiftType.values()) if (t != RiftType.RETURN) list.add(t);
        return list.toArray(new RiftType[0]);
    }

    public static boolean isCorridor(UniverseId id) {
        return id.gx() == 0 && id.gz() == 0;
    }

    public static UniverseSpec spec() {
        UniverseSpec s = spec;
        if (s == null) {
            s = new UniverseSpec();
            s.id = ID;
            s.name = "The Infinite Corridor";
            s.archetype = Archetype.ASTRAL;
            s.materials = Archetype.ASTRAL;
            s.seed = 0xC0221D0DL;
            s.terrain = TerrainMode.FRAGMENTS;
            s.hasSea = false;
            s.megaDensity = 0f;
            s.decorDensity = 0f;
            s.skyTop = 0x05030C;
            s.skyHorizon = 0x1A1030;
            s.fogColor = 0x120A20;
            s.nebulaA = 0xB0A0FF;
            s.nebulaB = 0xFFC14D;
            s.accent = 0xB0A0FF;
            s.starDensity = 0.9f;
            s.nebulaIntensity = 0.5f;
            s.galaxyIntensity = 0.6f;
            s.time = TimeMode.ETERNAL_NIGHT;
            s.fogDensity = 0.15f;
            s.hostility = 0f;
            s.music = MusicKind.COSMIC;
            s.gradeTint = 0xB0A0FF;
            s.gradeStrength = 0.08f;
            spec = s;
        }
        return s;
    }

    private static int doorIndex(int x) {
        return Math.floorDiv(x + DOOR_SPACING / 2, DOOR_SPACING);
    }

    private static int doorOffset(int x) {
        return x - doorIndex(x) * DOOR_SPACING;
    }

    /** Block of the corridor at a world position (null-free; air where there is nothing). */
    public static BlockState stateAt(int x, int y, int z) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (Math.abs(x) > UniverseId.BOUNDARY) return air;
        int az = Math.abs(z);
        if (az > HALF_WIDTH + 2 || y < FLOOR - 1 || y > CEILING) return air;
        int door = doorOffset(x);
        boolean doorway = Math.abs(door) <= 1;
        BlockState stone = RvBlocks.NEXUS_STONE.get().defaultBlockState();
        BlockState bricks = RvBlocks.NEXUS_BRICKS.get().defaultBlockState();
        BlockState glow = RvBlocks.NEXUS_GLOW.get().defaultBlockState();
        if (az > HALF_WIDTH) {
            // doorway recesses: two blocks deep, framed in light
            if (Math.abs(door) > 2) return air;
            if (y == FLOOR - 1) return air;
            if (az == HALF_WIDTH + 2) return Math.abs(door) == 2 || y == CEILING - 3 ? glow : bricks;
            if (Math.abs(door) == 2) return bricks;
            if (y == FLOOR) return stone;
            if (y >= CEILING - 3) return y == CEILING - 3 ? bricks : air;
            return air;
        }
        if (y == FLOOR - 1) return stone;
        if (y == FLOOR) {
            if (az == 0 && Math.floorMod(x, 16) == 0) return glow;
            if (az == 0 && Math.floorMod(x, 4) == 0) return RvBlocks.ANCIENT_GLYPH.get().defaultBlockState();
            return az == HALF_WIDTH ? bricks : stone;
        }
        if (y == CEILING) return az == 0 && Math.floorMod(x, 6) == 0 ? glow : bricks;
        if (az == HALF_WIDTH) {
            if (doorway && y > FLOOR && y < CEILING - 3) return air;
            if (Math.floorMod(x, 8) == 4 && (y == FLOOR + 1 || y == CEILING - 1)) return glow;
            return y == CEILING - 1 || y == FLOOR + 1 ? RvBlocks.ANCIENT_BRICKS.get().defaultBlockState() : bricks;
        }
        return air;
    }

    public static void fill(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        if (minZ > HALF_WIDTH + 2 || minZ + 15 < -HALF_WIDTH - 2) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                if (Math.abs(z) > HALF_WIDTH + 2) continue;
                for (int y = FLOOR - 1; y <= CEILING; y++) {
                    BlockState s = stateAt(x, y, z);
                    if (!s.isAir()) chunk.setBlockState(pos.set(x, y, z), s, false);
                }
            }
        }
    }

    public static RiftType doorType(int doorIndex, int side) {
        long h = Hash.of(0xD00D5L, doorIndex, side);
        if (Hash.unit(h) < 0.06f) return RiftType.NEXUS;
        return DOOR_TYPES[(int) Long.remainderUnsigned(Hash.mix(h + 1), DOOR_TYPES.length)];
    }

    public static void decorate(WorldGenLevel level, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int x = minX; x < minX + 16; x++) {
            if (doorOffset(x) != 0 || Math.abs(x) > UniverseId.BOUNDARY) continue;
            for (int side = -1; side <= 1; side += 2) {
                int z = side * (HALF_WIDTH + 1);
                if (z < minZ || z >= minZ + 16) continue;
                BlockPos p = new BlockPos(x, FLOOR + 2, z);
                level.setBlock(p, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, doorType(doorIndex(x), side)), 2);
            }
        }
    }

    public static int baseHeight(int x, int z, LevelHeightAccessor level) {
        return Math.abs(z) <= HALF_WIDTH + 2 && Math.abs(x) <= UniverseId.BOUNDARY ? CEILING + 1 : level.getMinBuildHeight();
    }

    public static BlockPos arrival(RandomSource random) {
        int x = (random.nextInt(201) - 100) * DOOR_SPACING + DOOR_SPACING / 2;
        return new BlockPos(x, FLOOR + 1, 0);
    }
}

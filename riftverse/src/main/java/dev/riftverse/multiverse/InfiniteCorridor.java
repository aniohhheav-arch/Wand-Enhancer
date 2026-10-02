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

    // ------------------------------------------------------------------ zones

    public static final int ZONE_LENGTH = 128;
    public static final String[] ZONES = {"The Grand Hall", "The Endless Library", "The Drowned Passage", "The Overgrown Way", "The Frozen Gallery",
            "The Burning Corridor", "The Crystal Vault", "The Void Gallery", "The Hall of Mirrors", "The Clockwork Passage", "The Neon Arcade",
            "The Ruined Wing", "The Deep Dark", "The Gilded Hall", "The Dreaming Corridor"};
    public static final int MIRROR = 8;
    public static final int VOID = 7;

    /** Zone type (0-14) of the stretch of corridor containing x. The first stretch is always the Grand Hall. */
    public static int zone(int x) {
        int seg = Math.floorDiv(x, ZONE_LENGTH);
        if (seg == 0 || seg == -1) return 0;
        return (int) Long.remainderUnsigned(Hash.of(0xC0B1D0L, seg), ZONES.length);
    }

    /** Wall, trim, floor, light and ceiling blocks for a zone. */
    private static BlockState[] palette(int zone) {
        BlockState stone = RvBlocks.NEXUS_STONE.get().defaultBlockState(), bricks = RvBlocks.NEXUS_BRICKS.get().defaultBlockState(),
                glow = RvBlocks.NEXUS_GLOW.get().defaultBlockState();
        return switch (zone) {
            case 1 -> new BlockState[] {Blocks.BOOKSHELF.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, false), Blocks.DARK_OAK_PLANKS.defaultBlockState()};
            case 2 -> new BlockState[] {Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState()};
            case 3 -> new BlockState[] {Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()};
            case 4 -> new BlockState[] {Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.ICE.defaultBlockState()};
            case 5 -> new BlockState[] {Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()};
            case 6 -> new BlockState[] {Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.BUDDING_AMETHYST.defaultBlockState()};
            case 7 -> new BlockState[] {Blocks.BLACK_STAINED_GLASS.defaultBlockState(), glow, Blocks.BLACK_STAINED_GLASS.defaultBlockState(), glow, Blocks.BLACK_STAINED_GLASS.defaultBlockState()};
            case 8 -> new BlockState[] {Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState(), Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState()};
            case 9 -> new BlockState[] {Blocks.WAXED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_EXPOSED_COPPER.defaultBlockState(), Blocks.WAXED_COPPER_GRATE.defaultBlockState(), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true), Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState()};
            case 10 -> new BlockState[] {Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.CYAN_STAINED_GLASS.defaultBlockState()};
            case 11 -> new BlockState[] {Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState()};
            case 12 -> new BlockState[] {Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.SCULK.defaultBlockState(), Blocks.SCULK.defaultBlockState(), Blocks.SCULK_CATALYST.defaultBlockState(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState()};
            case 13 -> new BlockState[] {Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState()};
            case 14 -> new BlockState[] {Blocks.PINK_WOOL.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(), Blocks.LIGHT_BLUE_WOOL.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState()};
            default -> new BlockState[] {bricks, RvBlocks.ANCIENT_BRICKS.get().defaultBlockState(), stone, glow, bricks};
        };
    }

    /** True for the rare hidden alcove behind a cracked wall panel (a corridor secret). */
    public static boolean secretAt(int doorIdx) {
        return Hash.unit(Hash.of(0x5EC12E7L, doorIdx)) < 0.08f;
    }

    /** Block of the corridor at a world position (null-free; air where there is nothing). */
    public static BlockState stateAt(int x, int y, int z) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (Math.abs(x) > UniverseId.BOUNDARY) return air;
        int zone = zone(x);
        // the Hall of Mirrors stands on glass over an upside-down copy of itself
        if (zone == MIRROR && y < FLOOR - 1 && y >= 2 * FLOOR - CEILING - 1) {
            int my = 2 * FLOOR - 1 - y;
            BlockState m = stateAt(x, my, z);
            return m.is(Blocks.LIGHT_GRAY_STAINED_GLASS) ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : m;
        }
        int az = Math.abs(z);
        int door = doorOffset(x);
        int idx = doorIndex(x);
        // a secret alcove: a small chamber hidden behind the wall opposite every rare "secret" door
        if (secretAt(idx) && z > HALF_WIDTH && z <= HALF_WIDTH + 6 && Math.abs(door - 6) <= 2 && y >= FLOOR - 1 && y <= FLOOR + 4) {
            if (y == FLOOR - 1 || y == FLOOR + 4 || z == HALF_WIDTH + 6 || Math.abs(door - 6) == 2) return RvBlocks.ANCIENT_GOLD.get().defaultBlockState();
            return air;
        }
        if (az > HALF_WIDTH + 2 || y < FLOOR - 1 || y > CEILING) return air;
        boolean doorway = Math.abs(door) <= 1;
        BlockState[] pal = palette(zone);
        BlockState wall = pal[0], trim = pal[1], floor = pal[2], glow = pal[3], ceil = pal[4];
        BlockState stone = RvBlocks.NEXUS_STONE.get().defaultBlockState();
        if (az > HALF_WIDTH) {
            if (Math.abs(door) > 2) return air;
            if (y == FLOOR - 1) return air;
            if (az == HALF_WIDTH + 2) return Math.abs(door) == 2 || y == CEILING - 3 ? glow : trim;
            if (Math.abs(door) == 2) return trim;
            if (y == FLOOR) return floor;
            if (y >= CEILING - 3) return y == CEILING - 3 ? trim : air;
            return air;
        }
        if (y == FLOOR - 1) return zone == MIRROR ? air : stone;
        if (y == FLOOR) {
            if (zone == 2 && az < HALF_WIDTH) return Blocks.WATER.defaultBlockState();
            if (az == 0 && Math.floorMod(x, 16) == 0) return glow;
            if (zone == 0 && az == 0 && Math.floorMod(x, 4) == 0) return RvBlocks.ANCIENT_GLYPH.get().defaultBlockState();
            if (zone == 5 && az == 0 && Math.floorMod(x, 3) != 0) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return az == HALF_WIDTH ? trim : floor;
        }
        if (zone == 2 && y == FLOOR - 1) return Blocks.PRISMARINE.defaultBlockState();
        if (y == CEILING) return az == 0 && Math.floorMod(x, 6) == 0 ? glow : (zone == VOID ? Blocks.BLACK_STAINED_GLASS.defaultBlockState() : ceil);
        if (az == HALF_WIDTH) {
            if (doorway && y > FLOOR && y < CEILING - 3) return air;
            // the secret: one cracked panel opposite a secret door, which breaks open into the alcove
            if (secretAt(idx) && z > 0 && Math.abs(door - 6) <= 1 && y > FLOOR && y < FLOOR + 4) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            if (Math.floorMod(x, 8) == 4 && (y == FLOOR + 1 || y == CEILING - 1)) return glow;
            if (zone == 11 && Hash.unit(Hash.of(0xBAD, x, y, z)) < 0.12f) return air;
            return y == CEILING - 1 || y == FLOOR + 1 ? trim : wall;
        }
        if (zone == 3 && y == CEILING - 1 && Hash.unit(Hash.of(0x1A7E, x, z)) < 0.3f) return Blocks.MOSS_CARPET.defaultBlockState();
        if (zone == 6 && y == FLOOR + 1 && az == HALF_WIDTH - 1 && Math.floorMod(x, 5) == 0) return Blocks.AMETHYST_CLUSTER.defaultBlockState();
        return air;
    }

    public static void fill(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int reach = HALF_WIDTH + 6;
        if (minZ > reach || minZ + 15 < -HALF_WIDTH - 2) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                if (z > reach || z < -HALF_WIDTH - 2) continue;
                for (int y = 2 * FLOOR - CEILING - 1; y <= CEILING; y++) {
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
        for (int x = minX; x < minX + 16; x++) {
            if (doorOffset(x) != 6 || !secretAt(doorIndex(x))) continue;
            BlockPos c = new BlockPos(x, FLOOR, HALF_WIDTH + 4);
            if (c.getZ() < minZ || c.getZ() >= minZ + 16) continue;
            level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH), 2);
            if (level.getBlockEntity(c) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
                chest.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.END_CITY_TREASURE, Hash.of(0x5EC, x));
            }
            level.setBlock(c.above(2), RvBlocks.NEXUS_GLOW.get().defaultBlockState(), 2);
        }
    }

    public static int baseHeight(int x, int z, LevelHeightAccessor level) {
        return z >= -HALF_WIDTH - 2 && z <= HALF_WIDTH + 6 && Math.abs(x) <= UniverseId.BOUNDARY ? CEILING + 1 : level.getMinBuildHeight();
    }

    public static BlockPos arrival(RandomSource random) {
        int x = (random.nextInt(201) - 100) * DOOR_SPACING + DOOR_SPACING / 2;
        return new BlockPos(x, FLOOR + 1, 0);
    }
}

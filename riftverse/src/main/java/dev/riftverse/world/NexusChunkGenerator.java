package dev.riftverse.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.riftverse.block.MultiverseConsoleBlock;
import dev.riftverse.block.PortalFieldBlockEntity;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.transit.Destination;
import dev.riftverse.universe.Archetype;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Generates the Multiverse Hub. */
public class NexusChunkGenerator extends ChunkGenerator {
    public static final MapCodec<NexusChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, i.stable(NexusChunkGenerator::new)));

    private static final long LAYOUT_SEED = 0x7E2A55L;

    public NexusChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        if (NexusLayout.isEmptyFar(minX + 8, minZ + 8)) return CompletableFuture.completedFuture(chunk);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Set<LevelChunkSection> acquired = new HashSet<>();
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            LevelChunkSection section = chunk.getSection(i);
            section.acquire();
            acquired.add(section);
        }
        try {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int y = 20; y <= 200; y++) {
                        BlockState s = NexusLayout.stateAt(minX + lx, y, minZ + lz, LAYOUT_SEED);
                        if (s == null || s.isAir()) continue;
                        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, y & 15, lz, s, false);
                        oceanFloor.update(lx, y, lz, s);
                        worldSurface.update(lx, y, lz, s);
                    }
                }
            }
        } finally {
            for (LevelChunkSection section : acquired) section.release();
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private static boolean inChunk(int minX, int minZ, int x, int z) {
        return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        RandomSource random = RandomSource.create(LAYOUT_SEED ^ chunk.getPos().toLong());
        int top = NexusLayout.TOP;

        for (NexusLayout.Gate g : NexusLayout.GATES) {
            if (!inChunk(minX, minZ, g.x(), g.z())) continue;
            Archetype a = Archetype.byId(g.index());
            BlockPos min = g.axis() == Direction.Axis.X ? new BlockPos(g.x() - 2, top + 1, g.z()) : new BlockPos(g.x(), top + 1, g.z() - 2);
            PortalFieldBlockEntity.placeInWorldgen(level, min, g.axis(), 5, 7, Destination.archetype(a), a.displayName, a.signatureColor);
        }

        if (inChunk(minX, minZ, 0, 0)) {
            int[][] consoles = {{0, -6}, {6, 0}, {0, 6}, {-6, 0}};
            Direction[] facing = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            for (int i = 0; i < 4; i++) {
                level.setBlock(new BlockPos(consoles[i][0], top + 3, consoles[i][1]),
                        RvBlocks.MULTIVERSE_CONSOLE.get().defaultBlockState().setValue(MultiverseConsoleBlock.FACING, facing[i]), 2);
            }
            BlackHoleEntity core = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), level.getLevel());
            core.moveTo(NexusLayout.CORE.getX() + 0.5, NexusLayout.CORE.getY(), NexusLayout.CORE.getZ() + 0.5, 0, 0);
            core.setHorizonRadius(7.0f);
            core.setNatural(true);
            core.setNexusCore(true);
            level.addFreshEntity(core);
        }

        if (inChunk(minX, minZ, NexusLayout.ARRIVAL.getX(), NexusLayout.ARRIVAL.getZ())) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) level.setBlock(NexusLayout.ARRIVAL.offset(dx, -1, dz), RvBlocks.NEXUS_GLOW.get().defaultBlockState(), 2);
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            int[] c = NexusLayout.satellite(i);
            if (!inChunk(minX, minZ, c[0], c[1])) continue;
            BlockPos center = new BlockPos(c[0], top + 1, c[1]);
            switch (i) {
                case NexusLayout.SAT_RANDOM -> gate(level, center, Destination.random(), "Uncharted Reality", 0x7DF9FF);
                case NexusLayout.SAT_HOME -> gate(level, center, Destination.home(), "Return Home", 0xFFFFFF);
                case NexusLayout.SAT_ARENA -> {
                    level.setBlock(center, RvBlocks.RIFT_ALTAR.get().defaultBlockState(), 2);
                    for (int k = 0; k < 4; k++) {
                        double a = k * Math.PI / 2 + Math.PI / 4;
                        BlockPos p = center.offset((int) Math.round(Math.cos(a) * 6), 0, (int) Math.round(Math.sin(a) * 6));
                        for (int y = 0; y < 5; y++) level.setBlock(p.above(y), y == 4 ? RvBlocks.NEXUS_GLOW.get().defaultBlockState() : RvBlocks.NEXUS_BRICKS.get().defaultBlockState(), 2);
                    }
                }
                case NexusLayout.SAT_ARCHIVE -> {
                    for (int k = 0; k < 3; k++) {
                        double a = k * Math.PI * 2 / 3;
                        BlockPos p = center.offset((int) Math.round(Math.cos(a) * 4), 0, (int) Math.round(Math.sin(a) * 4));
                        level.setBlock(p, Blocks.CHEST.defaultBlockState(), 2);
                        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, p, Decorator.RIFT_CACHE);
                    }
                    level.setBlock(center, RvBlocks.MULTIVERSE_CONSOLE.get().defaultBlockState(), 2);
                }
                default -> {}
            }
        }
    }

    private static void gate(WorldGenLevel level, BlockPos center, Destination dest, String label, int color) {
        BlockState bricks = RvBlocks.NEXUS_BRICKS.get().defaultBlockState();
        BlockState glow = RvBlocks.NEXUS_GLOW.get().defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 8; dy++) {
                boolean frame = Math.abs(dx) == 3 || dy >= 7;
                if (frame) level.setBlock(center.offset(dx, dy, 0), (Math.abs(dx) == 3 && (dy == 0 || dy >= 7)) ? glow : bricks, 2);
            }
        }
        PortalFieldBlockEntity.placeInWorldgen(level, center.offset(-2, 0, 0), Direction.Axis.X, 5, 7, dest, label, color);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        for (int y = 200; y >= 20; y--) {
            BlockState s = NexusLayout.stateAt(x, y, z, LAYOUT_SEED);
            if (s != null && !s.isAir()) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int minY = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            BlockState s = NexusLayout.stateAt(x, minY + i, z, LAYOUT_SEED);
            states[i] = s == null ? Blocks.AIR.defaultBlockState() : s;
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Riftverse: The Multiverse Nexus");
    }
}

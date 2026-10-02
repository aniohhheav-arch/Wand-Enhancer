package dev.riftverse.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.riftverse.multiverse.InfiniteCorridor;
import dev.riftverse.multiverse.RealityState;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.util.Hash;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
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

/**
 * Generates every universe of the Expanse. The spec of the slot a chunk belongs to decides terrain, materials,
 * megastructures and decorations, so a single dimension hosts an unbounded number of distinct realities.
 */
public class UniverseChunkGenerator extends ChunkGenerator {
    public static final MapCodec<UniverseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, i.stable(UniverseChunkGenerator::new)));

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public UniverseChunkGenerator(BiomeSource biomeSource) {
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
        UniverseId slot = UniverseId.ofBlock(minX + 8, minZ + 8);
        if (RealityState.isErased(slot)) return CompletableFuture.completedFuture(chunk);
        if (InfiniteCorridor.isCorridor(slot)) {
            InfiniteCorridor.fill(chunk);
            return CompletableFuture.completedFuture(chunk);
        }
        UniverseSpec spec = UniverseRegistry.specAt(minX + 8, minZ + 8);
        TerrainSampler sampler = TerrainSampler.of(spec);
        MaterialSet m = MaterialSet.of(spec.materials);
        List<Megastructures.Placement> megas = Megastructures.touching(spec, sampler, minX, minZ, minX + 15, minZ + 15);

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = Math.max(chunk.getMinBuildHeight(), TerrainSampler.MIN_Y);
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, TerrainSampler.MAX_Y);

        Set<LevelChunkSection> acquired = new HashSet<>();
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            LevelChunkSection section = chunk.getSection(i);
            section.acquire();
            acquired.add(section);
        }
        try {
            TerrainSampler.Column col = new TerrainSampler.Column();
            List<Megastructures.Placement> local = new ArrayList<>(megas.size());
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = minX + lx;
                    int z = minZ + lz;
                    sampler.sample(x, z, col);
                    local.clear();
                    for (Megastructures.Placement p : megas) {
                        if (Math.abs(x - p.x) <= p.radius() && Math.abs(z - p.z) <= p.radius()) local.add(p);
                    }
                    boolean city = spec.terrain == TerrainMode.CITY && col.ground != TerrainSampler.NONE && !col.frayed;
                    for (int y = minY; y <= maxY; y++) {
                        BlockState state = terrainAt(spec, sampler, m, col, x, y, z, minY);
                        if (city && y >= col.ground) {
                            BlockState c = cityAt(spec, m, x, y, z, col.ground);
                            if (c != null) state = c;
                        }
                        for (Megastructures.Placement p : local) {
                            if (y < p.minY() || y > p.maxY()) continue;
                            BlockState s = Megastructures.sample(p, m, x, y, z);
                            if (s != null) state = s;
                        }
                        if (state.isAir()) continue;
                        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                        section.setBlockState(lx, y & 15, lz, state, false);
                        oceanFloor.update(lx, y, lz, state);
                        worldSurface.update(lx, y, lz, state);
                    }
                }
            }
        } finally {
            for (LevelChunkSection section : acquired) section.release();
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Recomputes the generated block column at (x, z) for a spec, exactly as fresh world generation would produce it.
     * Used by reality reconstruction to rewrite already-generated terrain in place.
     */
    public static void column(UniverseSpec spec, List<Megastructures.Placement> megas, int x, int z, int minY, int maxY, BlockState[] out) {
        TerrainSampler sampler = TerrainSampler.of(spec);
        MaterialSet m = MaterialSet.of(spec.materials);
        TerrainSampler.Column col = new TerrainSampler.Column();
        sampler.sample(x, z, col);
        boolean city = spec.terrain == TerrainMode.CITY && col.ground != TerrainSampler.NONE && !col.frayed;
        int genMin = Math.max(minY, TerrainSampler.MIN_Y);
        for (int y = minY; y <= maxY; y++) {
            BlockState state = y < genMin || y > TerrainSampler.MAX_Y ? AIR : terrainAt(spec, sampler, m, col, x, y, z, genMin);
            if (city && y >= col.ground) {
                BlockState c = cityAt(spec, m, x, y, z, col.ground);
                if (c != null) state = c;
            }
            for (Megastructures.Placement p : megas) {
                if (y < p.minY() || y > p.maxY()) continue;
                if (Math.abs(x - p.x) > p.radius() || Math.abs(z - p.z) > p.radius()) continue;
                BlockState s = Megastructures.sample(p, m, x, y, z);
                if (s != null) state = s;
            }
            out[y - minY] = state;
        }
    }

    /** Landmarks touching a 16x16 chunk footprint, to pass to {@link #column}. */
    public static List<Megastructures.Placement> landmarks(UniverseSpec spec, int minX, int minZ) {
        return Megastructures.touching(spec, TerrainSampler.of(spec), minX, minZ, minX + 15, minZ + 15);
    }

    static BlockState terrainAt(UniverseSpec spec, TerrainSampler sampler, MaterialSet m, TerrainSampler.Column col, int x, int y, int z, int minY) {
        if (col.frayed) return AIR;
        boolean sea = spec.hasSea && !m.liquid.isAir();
        if (col.ground != TerrainSampler.NONE) {
            if (y == minY) return BEDROCK;
            if (y <= col.ground) {
                int depth = col.ground - y;
                if (depth == 0) {
                    if (sea && col.ground < spec.seaLevel - 1) return m.sub;
                    if (spec.terrain == TerrainMode.DUNES) return m == MaterialSet.of(dev.riftverse.universe.Archetype.CINDER) || m == MaterialSet.of(dev.riftverse.universe.Archetype.ASHEN)
                            ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    return m.surface;
                }
                if (depth <= 3) return spec.terrain == TerrainMode.DUNES ? Blocks.SANDSTONE.defaultBlockState() : m.sub;
                if (depth < 48 && sampler.veinAt(x, y, z)) return m.accent;
                return m.stone;
            }
        }
        if (col.islandTop1 != TerrainSampler.NONE && y <= col.islandTop1 && y >= col.islandBottom1) {
            return islandBlock(m, col.islandTop1, col.islandBottom1, x, y, z, spec.seed);
        }
        if (col.islandTop2 != TerrainSampler.NONE && y <= col.islandTop2 && y >= col.islandBottom2) {
            return islandBlock(m, col.islandTop2, col.islandBottom2, x, y, z, spec.seed);
        }
        if (col.ceilingBottom != TerrainSampler.NONE && y >= col.ceilingBottom) {
            if (y == col.ceilingBottom && Hash.unit(Hash.of(spec.seed, x, z, 91)) < 0.08f) return m.accent;
            return y >= TerrainSampler.MAX_Y - 1 ? BEDROCK : m.stone;
        }
        if (sea && y <= spec.seaLevel && (col.ground == TerrainSampler.NONE ? spec.terrain != TerrainMode.FLOATING && spec.terrain != TerrainMode.FRAGMENTS : y > col.ground)) {
            if (m.frozenSea && y == spec.seaLevel) return Blocks.ICE.defaultBlockState();
            return m.liquid;
        }
        return AIR;
    }

    public static final int CITY_LOT = 44;
    public static final int CITY_ROAD = 10;

    /** Street grid with hollow, explorable buildings: lit windows, neon bands, rooftop parapets and antennas. */
    static BlockState cityAt(UniverseSpec spec, MaterialSet m, int x, int y, int z, int ground) {
        int gx = Math.floorDiv(x, CITY_LOT);
        int gz = Math.floorDiv(z, CITY_LOT);
        int lx = Math.floorMod(x, CITY_LOT);
        int lz = Math.floorMod(z, CITY_LOT);
        int ly = y - ground;
        if (ly < 0) return null;
        if (lx < CITY_ROAD || lz < CITY_ROAD) {
            if (ly != 0) return null;
            boolean lane = (lx == CITY_ROAD / 2 && lz >= CITY_ROAD) || (lz == CITY_ROAD / 2 && lx >= CITY_ROAD);
            if (lane && Math.floorMod(x + z, 4) < 2) return m.accent;
            boolean curb = lx == CITY_ROAD - 1 || lz == CITY_ROAD - 1 || lx == 0 || lz == 0;
            return curb ? m.structure : m.structure2;
        }
        long h = Hash.of(spec.seed, gx, gz, 17);
        if (Hash.unit(h) < 0.08f) return ly == 0 ? m.structure : null;
        boolean split = Hash.unit(Hash.mix(h + 5)) < 0.45f;
        int half = split && lx >= CITY_ROAD + (CITY_LOT - CITY_ROAD) / 2 ? 1 : 0;
        long bh = Hash.mix(h + 11 + half);
        int inset = 2 + Hash.range(bh, 0, 2);
        int x0 = CITY_ROAD + inset;
        int x1 = CITY_LOT - 1 - inset;
        if (split) {
            int mid = CITY_ROAD + (CITY_LOT - CITY_ROAD) / 2;
            if (half == 0) x1 = mid - 2;
            else x0 = mid + 1;
        }
        int z0 = CITY_ROAD + inset;
        int z1 = CITY_LOT - 1 - inset;
        if (lx < x0 || lx > x1 || lz < z0 || lz > z1) return ly == 0 ? m.structure : null;
        float tall = Hash.unit(Hash.mix(bh + 1));
        int height = 14 + (int) (Math.pow(tall, 2.2) * 120);
        height -= height % 5;
        boolean neon = Hash.unit(Hash.mix(bh + 2)) < 0.6f;
        boolean wallX = lx == x0 || lx == x1;
        boolean wallZ = lz == z0 || lz == z1;
        if (ly > height) {
            if (ly == height + 1 && (wallX || wallZ)) return m.structure;
            int cx = (x0 + x1) / 2;
            int cz = (z0 + z1) / 2;
            int mast = 6 + Hash.range(Hash.mix(bh + 3), 0, 18);
            if (lx == cx && lz == cz && ly <= height + mast) return ly == height + mast ? m.accent2 : m.structure;
            return null;
        }
        if (ly == 0 || ly == height) return m.structure;
        if (!wallX && !wallZ) return ly % 5 == 0 ? m.structure : AIR;
        int along = wallX ? lz : lx;
        int alongMid = wallX ? (z0 + z1) / 2 : (x0 + x1) / 2;
        if (ly <= 3 && Math.abs(along - alongMid) <= 1 && (lx == x0 || lz == z0)) return AIR;
        if (wallX && wallZ) return neon ? m.accent2 : m.structure;
        if (ly % 5 == 0) return m.structure;
        if (neon && ly % 20 == 10) return m.accent;
        if (Math.floorMod(along, 4) == 0) return m.structure2;
        if (Hash.unit(Hash.of(spec.seed, x, y, z)) < 0.07f) return m.accent;
        return m.glass;
    }

    private static BlockState islandBlock(MaterialSet m, int top, int bottom, int x, int y, int z, long seed) {
        int depth = top - y;
        if (depth == 0) return m.surface;
        if (depth <= 3) return m.sub;
        if (y - bottom <= 1 && Hash.unit(Hash.of(seed, x, y, z)) < 0.12f) return m.accent;
        return m.stone;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        UniverseId slot = UniverseId.ofBlock(minX + 8, minZ + 8);
        if (RealityState.isErased(slot)) return;
        if (InfiniteCorridor.isCorridor(slot)) {
            InfiniteCorridor.decorate(level, chunk);
            return;
        }
        UniverseSpec spec = UniverseRegistry.specAt(minX + 8, minZ + 8);
        Decorator.decorate(level, chunk, spec);
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
        return 63;
    }

    @Override
    public int getMinY() {
        return TerrainSampler.MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        UniverseId slot = UniverseId.ofBlock(x, z);
        if (RealityState.isErased(slot)) return level.getMinBuildHeight();
        if (InfiniteCorridor.isCorridor(slot)) return InfiniteCorridor.baseHeight(x, z, level);
        UniverseSpec spec = UniverseRegistry.specAt(x, z);
        TerrainSampler.Column col = new TerrainSampler.Column();
        TerrainSampler.of(spec).sample(x, z, col);
        int top = col.highestSurface();
        if (spec.hasSea && type != Heightmap.Types.OCEAN_FLOOR && type != Heightmap.Types.OCEAN_FLOOR_WG
                && spec.terrain != TerrainMode.FLOATING && spec.terrain != TerrainMode.FRAGMENTS) {
            top = Math.max(top, spec.seaLevel);
        }
        return top == TerrainSampler.NONE ? level.getMinBuildHeight() : top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        UniverseId slot = UniverseId.ofBlock(x, z);
        if (RealityState.isErased(slot) || InfiniteCorridor.isCorridor(slot)) {
            BlockState[] states = new BlockState[level.getHeight()];
            for (int i = 0; i < states.length; i++) {
                int y = level.getMinBuildHeight() + i;
                states[i] = RealityState.isErased(slot) ? AIR : InfiniteCorridor.stateAt(x, y, z);
            }
            return new NoiseColumn(level.getMinBuildHeight(), states);
        }
        UniverseSpec spec = UniverseRegistry.specAt(x, z);
        TerrainSampler sampler = TerrainSampler.of(spec);
        MaterialSet m = MaterialSet.of(spec.materials);
        TerrainSampler.Column col = new TerrainSampler.Column();
        sampler.sample(x, z, col);
        int minY = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) states[i] = terrainAt(spec, sampler, m, col, x, minY + i, z, minY);
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        UniverseSpec spec = UniverseRegistry.specAt(pos.getX(), pos.getZ());
        info.add("Riftverse: " + spec.name + " [" + spec.id.designation() + "] " + spec.archetype.displayName);
        UniverseId id = UniverseId.ofBlock(pos.getX(), pos.getZ());
        info.add("Riftverse slot: " + id.gx() + ", " + id.gz() + (spec.prompt.isEmpty() ? "" : " — \"" + spec.prompt + "\""));
    }
}

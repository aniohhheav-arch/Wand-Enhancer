package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.world.Decorator;
import dev.riftverse.world.Megastructures;
import dev.riftverse.world.UniverseChunkGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Rewrites already-generated terrain in expanding waves, a budgeted number of block columns per tick. ERASE unmakes
 * every block (and every non-player entity) chunk by chunk; REBUILD recomputes each column from a universe spec exactly
 * as fresh world generation would, then re-decorates the chunk. Only chunks inside the target universe's slot are
 * touched, everything goes through normal level APIs and nothing on disk is deleted, so worlds stay valid.
 */
public final class RealityRewriter {
    public enum Mode { ERASE, REBUILD }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Job> JOBS = new ArrayList<>();
    private static int nextId = 1;

    private RealityRewriter() {}

    public static final class Job {
        public final int id;
        public final ServerLevel level;
        public final Mode mode;
        public final UniverseId universe;
        @Nullable
        public final UniverseSpec spec;
        final List<ChunkPos> chunks;
        int chunkIndex;
        int column;
        List<Megastructures.Placement> landmarks = List.of();
        @Nullable
        final Runnable onDone;
        BlockState[] buffer = new BlockState[0];
        boolean cancelled;

        Job(int id, ServerLevel level, Mode mode, UniverseId universe, @Nullable UniverseSpec spec, List<ChunkPos> chunks, @Nullable Runnable onDone) {
            this.id = id;
            this.level = level;
            this.mode = mode;
            this.universe = universe;
            this.spec = spec;
            this.chunks = chunks;
            this.onDone = onDone;
        }

        public int total() {
            return chunks.size();
        }

        public int done() {
            return chunkIndex;
        }
    }

    /**
     * Starts a wave. Centres are the epicentres (players, the universe origin); every chunk within radius of any centre
     * and inside the universe slot is rewritten, nearest first.
     */
    public static Job start(ServerLevel level, Mode mode, UniverseId universe, @Nullable UniverseSpec spec, List<BlockPos> centres, int radius,
                            @Nullable Runnable onDone) {
        Set<Long> seen = new HashSet<>();
        List<ChunkPos> chunks = new ArrayList<>();
        int cr = (radius >> 4) + 1;
        for (BlockPos c : centres) {
            ChunkPos cc = new ChunkPos(c);
            for (int dx = -cr; dx <= cr; dx++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    if (dx * dx + dz * dz > cr * cr) continue;
                    ChunkPos cp = new ChunkPos(cc.x + dx, cc.z + dz);
                    if (!UniverseId.ofBlock(cp.getMiddleBlockX(), cp.getMiddleBlockZ()).equals(universe)) continue;
                    if (seen.add(cp.toLong())) chunks.add(cp);
                }
            }
        }
        chunks.sort(Comparator.comparingDouble(cp -> {
            double best = Double.MAX_VALUE;
            for (BlockPos c : centres) {
                double dx = cp.getMiddleBlockX() - c.getX();
                double dz = cp.getMiddleBlockZ() - c.getZ();
                best = Math.min(best, dx * dx + dz * dz);
            }
            return best;
        }));
        Job job = new Job(nextId++, level, mode, universe, spec, chunks, onDone);
        JOBS.add(job);
        return job;
    }

    public static List<Job> jobs() {
        return List.copyOf(JOBS);
    }

    public static boolean busy(UniverseId universe) {
        for (Job j : JOBS) if (j.universe.equals(universe)) return true;
        return false;
    }

    public static void cancelAll() {
        for (Job j : JOBS) j.cancelled = true;
        JOBS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        int budget = RiftverseConfig.get(RiftverseConfig.REWRITE_COLUMNS_PER_TICK, 96);
        int share = Math.max(8, budget / JOBS.size());
        Iterator<Job> it = JOBS.iterator();
        List<Runnable> finished = new ArrayList<>();
        while (it.hasNext()) {
            Job job = it.next();
            int left = share;
            while (left > 0 && job.chunkIndex < job.chunks.size()) {
                left -= step(job, left);
            }
            if (job.chunkIndex >= job.chunks.size()) {
                it.remove();
                if (job.onDone != null) finished.add(job.onDone);
            }
        }
        for (Runnable r : finished) r.run();
    }

    /** Processes up to {@code max} columns of the job's current chunk; returns how many were processed. */
    private static int step(Job job, int max) {
        ChunkPos cp = job.chunks.get(job.chunkIndex);
        ServerLevel level = job.level;
        LevelChunk chunk = level.getChunk(cp.x, cp.z);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        if (job.column == 0 && job.mode == Mode.REBUILD && job.spec != null) {
            job.landmarks = UniverseChunkGenerator.landmarks(job.spec, cp.getMinBlockX(), cp.getMinBlockZ());
            if (job.buffer.length != maxY - minY + 1) job.buffer = new BlockState[maxY - minY + 1];
        }
        int processed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (processed < max && job.column < 256) {
            int x = cp.getMinBlockX() + (job.column & 15);
            int z = cp.getMinBlockZ() + (job.column >> 4);
            if (job.mode == Mode.ERASE) eraseColumn(level, chunk, x, z, minY, pos);
            else if (job.spec != null) rebuildColumn(job, level, x, z, minY, maxY, pos);
            job.column++;
            processed++;
        }
        if (job.column >= 256) {
            finishChunk(job, chunk, cp);
            job.column = 0;
            job.chunkIndex++;
        }
        return Math.max(1, processed);
    }

    private static void eraseColumn(ServerLevel level, LevelChunk chunk, int x, int z, int minY, BlockPos.MutableBlockPos pos) {
        BlockState air = Blocks.AIR.defaultBlockState();
        LevelChunkSection[] sections = chunk.getSections();
        for (int si = sections.length - 1; si >= 0; si--) {
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir()) continue;
            int base = minY + si * 16;
            for (int ly = 15; ly >= 0; ly--) {
                pos.set(x, base + ly, z);
                if (!section.getBlockState(x & 15, ly, z & 15).isAir()) level.setBlock(pos, air, FLAGS);
            }
        }
    }

    private static void rebuildColumn(Job job, ServerLevel level, int x, int z, int minY, int maxY, BlockPos.MutableBlockPos pos) {
        UniverseChunkGenerator.column(job.spec, job.landmarks, x, z, minY, maxY, job.buffer);
        for (int y = minY; y <= maxY; y++) {
            BlockState want = job.buffer[y - minY];
            pos.set(x, y, z);
            if (level.getBlockState(pos) != want) level.setBlock(pos, want, FLAGS);
        }
    }

    private static void finishChunk(Job job, LevelChunk chunk, ChunkPos cp) {
        ServerLevel level = job.level;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        boolean heavy = RiftverseConfig.get(RiftverseConfig.HEAVY_EFFECTS, true);
        int sx = cp.getMiddleBlockX();
        int sz = cp.getMiddleBlockZ();
        if (job.mode == Mode.ERASE) {
            AABB box = new AABB(cp.getMinBlockX(), minY, cp.getMinBlockZ(), cp.getMaxBlockX() + 1, maxY, cp.getMaxBlockZ() + 1);
            for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            int py = y <= minY + 1 ? 64 : y;
            level.sendParticles(RvParticles.GLITCH.get().with(0xFF3A5A, 0.6f, 18), sx, py, sz, heavy ? 24 : 6, 6, 3, 6, 0.05);
            if (heavy) level.sendParticles(RvParticles.DUST.get().with(0x1A0008, 1.2f, 40), sx, py, sz, 16, 7, 4, 7, 0.02);
        } else {
            if (job.spec != null) Decorator.decorate(level, chunk, job.spec);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            level.sendParticles(RvParticles.SPARK.get().with(job.spec == null ? 0x7DF9FF : job.spec.accent, 0.7f, 24), sx, y + 1, sz, heavy ? 30 : 8, 7, 2, 7, 0.08);
            if (heavy) level.sendParticles(RvParticles.RING.get().with(0xFFF0C8, 6f, 14), sx, y + 1, sz, 1, 0, 0, 0, 0);
        }
        if ((job.chunkIndex & 7) == 0) {
            level.playSound(null, sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz), sz,
                    job.mode == Mode.ERASE ? RvSounds.SINGULARITY_IMPLODE.get() : RvSounds.UNIVERSE_ARRIVE.get(), SoundSource.AMBIENT, 1.5f,
                    job.mode == Mode.ERASE ? 0.6f : 1.2f);
        }
    }
}

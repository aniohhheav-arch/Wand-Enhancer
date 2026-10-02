package dev.mysticarts.world;

import dev.mysticarts.MaConfig;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A rolling journal of block changes (breaks, placements, explosions) per dimension, used by Time Reversal, the Time
 * Stone's Rewind and Temporal Rewrite. Only the configured window is kept, in memory.
 */
public final class BlockHistory {
    private record Change(long time, long pos, BlockState before, @Nullable CompoundTag blockEntity) {}

    private static final Map<ResourceKey<Level>, ArrayDeque<Change>> JOURNALS = new HashMap<>();
    private static final int MAX_ENTRIES = 200_000;

    private BlockHistory() {}

    public static void clearAll() {
        JOURNALS.clear();
    }

    /** Record the state a position had right before it changed. */
    public static void record(ServerLevel level, BlockPos pos, BlockState before) {
        CompoundTag be = null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) be = blockEntity.saveWithFullMetadata(level.registryAccess());
        ArrayDeque<Change> journal = JOURNALS.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
        journal.addLast(new Change(level.getGameTime(), pos.asLong(), before, be));
        while (journal.size() > MAX_ENTRIES) journal.pollFirst();
    }

    public static void trim(ServerLevel level) {
        ArrayDeque<Change> journal = JOURNALS.get(level.dimension());
        if (journal == null) return;
        long cutoff = level.getGameTime() - MaConfig.REWIND_SECONDS.get() * 20L * 5;
        while (!journal.isEmpty() && journal.peekFirst().time < cutoff) journal.pollFirst();
    }

    /**
     * Restores every recorded position within {@code radius} of {@code center} to the state it had {@code seconds} ago.
     * Item drops created inside the window are removed so the rewind cannot duplicate items.
     */
    public static int rewind(ServerLevel level, BlockPos center, int radius, int seconds) {
        ArrayDeque<Change> journal = JOURNALS.get(level.dimension());
        if (journal == null || journal.isEmpty()) return 0;
        long since = level.getGameTime() - seconds * 20L;
        long r2 = (long) radius * radius;
        Map<Long, Change> oldest = new LinkedHashMap<>();
        Iterator<Change> it = journal.descendingIterator();
        while (it.hasNext()) {
            Change c = it.next();
            if (c.time < since) break;
            if (BlockPos.of(c.pos).distSqr(center) <= r2) oldest.put(c.pos, c);
        }
        int limit = MaConfig.REWIND_MAX_BLOCKS.get();
        int n = 0;
        for (Change c : oldest.values()) {
            if (n >= limit) break;
            BlockPos pos = BlockPos.of(c.pos);
            if (!level.isLoaded(pos)) continue;
            level.setBlock(pos, c.before, Block.UPDATE_ALL);
            if (c.blockEntity != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) be.loadWithComponents(c.blockEntity, level.registryAccess());
            }
            n++;
        }
        journal.removeIf(c -> c.time >= since && BlockPos.of(c.pos).distSqr(center) <= r2);
        if (n > 0) {
            int ageLimit = seconds * 20;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius), e -> e.getAge() < ageLimit)) {
                item.discard();
            }
        }
        return n;
    }
}

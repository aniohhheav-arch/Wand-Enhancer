package dev.mysticarts.world;

import dev.mysticarts.MaConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every block change made by a spell goes through here and is reverted when it expires: transmutations, conjured
 * barriers, illusions, raised terrain and alternate environments. The original states are saved with the world, so
 * nothing is lost if the server stops before a spell ends.
 */
public final class TemporaryBlocks extends SavedData {
    private static final String NAME = "mysticarts_temporary_blocks";

    private record Entry(BlockState original, BlockState placed, long expires) {}

    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public static TemporaryBlocks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TemporaryBlocks::new, TemporaryBlocks::load, null), NAME);
    }

    /** Whether a spell may temporarily replace this block (no block entities, nothing unbreakable). */
    public static boolean replaceable(ServerLevel level, BlockPos pos, boolean airOnly) {
        if (!MaConfig.TEMPORARY_BLOCKS.get() || !level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (airOnly) return s.canBeReplaced() && s.getFluidState().isEmpty();
        if (s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0) return false;
        return !s.isAir();
    }

    /** Places {@code state} for {@code ticks}. A position already under a spell keeps its true original. */
    public boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        long key = pos.asLong();
        Entry existing = entries.get(key);
        BlockState original = existing != null ? existing.original : level.getBlockState(pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        entries.put(key, new Entry(original, state, level.getGameTime() + ticks));
        setDirty();
        return true;
    }

    public boolean tracks(BlockPos pos) {
        return entries.containsKey(pos.asLong());
    }

    public void tick(ServerLevel level) {
        if (entries.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Map.Entry<Long, Entry>> it = entries.entrySet().iterator();
        int budget = 512;
        while (it.hasNext() && budget > 0) {
            Map.Entry<Long, Entry> e = it.next();
            if (e.getValue().expires > now) continue;
            BlockPos pos = BlockPos.of(e.getKey());
            if (!level.isLoaded(pos)) continue;
            restore(level, pos, e.getValue());
            it.remove();
            budget--;
            setDirty();
        }
    }

    /** Ends every spell-made change within {@code radius}; returns how many blocks were restored. */
    public int revertAround(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        long r2 = (long) radius * radius;
        Iterator<Map.Entry<Long, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> e = it.next();
            BlockPos pos = BlockPos.of(e.getKey());
            if (pos.distSqr(center) > r2 || !level.isLoaded(pos)) continue;
            restore(level, pos, e.getValue());
            it.remove();
            n++;
        }
        if (n > 0) setDirty();
        return n;
    }

    private static void restore(ServerLevel level, BlockPos pos, Entry e) {
        if (level.getBlockState(pos).equals(e.placed)) level.setBlock(pos, e.original, Block.UPDATE_ALL);
    }

    public List<BlockPos> positions() {
        List<BlockPos> out = new ArrayList<>(entries.size());
        for (long l : entries.keySet()) out.add(BlockPos.of(l));
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> e : entries.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey());
            t.put("original", NbtUtils.writeBlockState(e.getValue().original));
            t.put("placed", NbtUtils.writeBlockState(e.getValue().placed));
            t.putLong("expires", e.getValue().expires);
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    private static TemporaryBlocks load(CompoundTag tag, HolderLookup.Provider provider) {
        TemporaryBlocks data = new TemporaryBlocks();
        var blocks = provider.lookupOrThrow(Registries.BLOCK);
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            data.entries.put(t.getLong("pos"), new Entry(NbtUtils.readBlockState(blocks, t.getCompound("original")),
                    NbtUtils.readBlockState(blocks, t.getCompound("placed")), t.getLong("expires")));
        }
        return data;
    }
}

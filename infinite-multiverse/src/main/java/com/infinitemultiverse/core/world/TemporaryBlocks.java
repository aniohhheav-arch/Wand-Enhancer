package com.infinitemultiverse.core.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
 * Blocks placed by powers that must disappear again: domain barriers, ice bridges. Every placement remembers the
 * original block and an expiry game time, and the list is saved with the level, so even a crash mid-effect is
 * reverted the next time the level ticks. Blocks with block entities are never replaced.
 */
public final class TemporaryBlocks extends SavedData {
    private static final String NAME = "infinitemultiverse_temporary_blocks";

    private record Entry(BlockPos pos, BlockState original, BlockState placed, long expiry, String group) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public static TemporaryBlocks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TemporaryBlocks::new,
                (tag, registries) -> load(level, tag), null), NAME);
    }

    /**
     * Places {@code state} at {@code pos} for {@code ticks}, if the current block is replaceable ({@code solidsToo}
     * also allows plain solid blocks without block entities). Returns true if placed.
     */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks, String group, boolean solidsToo) {
        if (!level.isLoaded(pos) || level.getBlockEntity(pos) != null || !level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (current.equals(state)) {
            return false;
        }
        boolean replaceable = current.canBeReplaced() || current.isAir();
        if (!replaceable && !(solidsToo && current.getDestroySpeed(level, pos) >= 0f)) {
            return false;
        }
        TemporaryBlocks data = get(level);
        for (Entry entry : data.entries) {
            if (entry.pos.equals(pos)) {
                return false;
            }
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        data.entries.add(new Entry(pos.immutable(), current, state, level.getGameTime() + ticks, group));
        data.setDirty();
        return true;
    }

    /** Reverts every block of {@code group} immediately. */
    public static void revertGroup(ServerLevel level, String group) {
        TemporaryBlocks data = get(level);
        Iterator<Entry> it = data.entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.group.equals(group)) {
                data.revert(level, entry);
                it.remove();
            }
        }
        data.setDirty();
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 5 != 0) {
            return;
        }
        TemporaryBlocks data = get(level);
        if (data.entries.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Entry> it = data.entries.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.expiry <= now && level.isLoaded(entry.pos)) {
                data.revert(level, entry);
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    private void revert(ServerLevel level, Entry entry) {
        // Only undo our own block; if something else replaced it since, leave the world alone.
        if (level.getBlockState(entry.pos).equals(entry.placed)) {
            level.setBlock(entry.pos, entry.original, Block.UPDATE_ALL);
        }
    }

    private static TemporaryBlocks load(ServerLevel level, CompoundTag tag) {
        TemporaryBlocks data = new TemporaryBlocks();
        HolderLookup<Block> blocks = level.holderLookup(Registries.BLOCK);
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            data.entries.add(new Entry(BlockPos.of(e.getLong("pos")), NbtUtils.readBlockState(blocks, e.getCompound("original")),
                    NbtUtils.readBlockState(blocks, e.getCompound("placed")), e.getLong("expiry"), e.getString("group")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", entry.pos.asLong());
            e.put("original", NbtUtils.writeBlockState(entry.original));
            e.put("placed", NbtUtils.writeBlockState(entry.placed));
            e.putLong("expiry", entry.expiry);
            e.putString("group", entry.group);
            list.add(e);
        }
        tag.put("entries", list);
        return tag;
    }
}

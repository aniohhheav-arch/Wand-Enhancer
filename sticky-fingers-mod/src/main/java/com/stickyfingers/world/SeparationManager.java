package com.stickyfingers.world;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Owns every temporary world change made by zippers. Each separated (or stitched) block remembers its original state
 * and the zipper that owns it, and is restored when that zipper closes or when its timer runs out.
 * The data is persisted so a crash or restart can never leave permanent holes.
 */
public final class SeparationManager extends SavedData {
    private static final String NAME = "stickyfingers_separations";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private record Entry(BlockState original, BlockState placed, @Nullable CompoundTag blockEntity, UUID owner, long restoreAt) {}

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    public static SeparationManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SeparationManager::new, SeparationManager::load, null), NAME);
    }

    /** Separates (removes) blocks. Returns the positions that were actually opened. */
    public List<BlockPos> separate(ServerLevel level, @Nullable ServerPlayer actor, Collection<BlockPos> positions, UUID owner, int durationTicks) {
        List<BlockPos> done = new ArrayList<>();
        long restoreAt = durationTicks > 0 ? level.getGameTime() + durationTicks : Long.MAX_VALUE;
        for (BlockPos raw : positions) {
            BlockPos pos = raw.immutable();
            if (entries.containsKey(pos) || !canSeparate(level, actor, pos)) continue;
            BlockState state = level.getBlockState(pos);
            CompoundTag beTag = null;
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                beTag = be.saveWithFullMetadata(level.registryAccess());
                level.removeBlockEntity(pos);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            entries.put(pos, new Entry(state, Blocks.AIR.defaultBlockState(), beTag, owner, restoreAt));
            done.add(pos);
        }
        if (!done.isEmpty()) setDirty();
        return done;
    }

    /** Temporarily places a block into empty space (aerial platforms). */
    public List<BlockPos> stitch(ServerLevel level, Collection<BlockPos> positions, BlockState state, UUID owner, int durationTicks) {
        List<BlockPos> done = new ArrayList<>();
        long restoreAt = durationTicks > 0 ? level.getGameTime() + durationTicks : Long.MAX_VALUE;
        for (BlockPos raw : positions) {
            BlockPos pos = raw.immutable();
            if (entries.containsKey(pos) || !level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()) continue;
            BlockState original = level.getBlockState(pos);
            level.setBlock(pos, state, FLAGS);
            entries.put(pos, new Entry(original, state, null, owner, restoreAt));
            done.add(pos);
        }
        if (!done.isEmpty()) setDirty();
        return done;
    }

    public boolean canSeparate(ServerLevel level, @Nullable ServerPlayer actor, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > SFConfig.MAX_BLOCK_HARDNESS.get()) return false;
        if (level.getBlockEntity(pos) != null && !SFConfig.SEPARATE_BLOCK_ENTITIES.get()) return false;
        if (actor != null) {
            if (!level.mayInteract(actor, pos)) return false;
            if (SFConfig.RESPECT_PROTECTION.get()) {
                BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, actor);
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) return false;
            }
        }
        return true;
    }

    public void restoreOwner(ServerLevel level, UUID owner) {
        restoreIf(level, e -> e.owner.equals(owner));
    }

    public int restoreNear(ServerLevel level, BlockPos center, double radius, @Nullable Set<UUID> owners) {
        double r2 = radius * radius;
        List<BlockPos> hits = new ArrayList<>();
        entries.forEach((pos, e) -> {
            if (pos.distSqr(center) <= r2 && (owners == null || owners.contains(e.owner))) hits.add(pos);
        });
        hits.forEach(p -> restore(level, p));
        return hits.size();
    }

    public void restoreAll(ServerLevel level) {
        new ArrayList<>(entries.keySet()).forEach(p -> restore(level, p));
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        restoreIf(level, e -> e.restoreAt <= now);
    }

    public boolean isSeparated(BlockPos pos) {
        return entries.containsKey(pos);
    }

    private void restoreIf(ServerLevel level, java.util.function.Predicate<Entry> pred) {
        List<BlockPos> hits = new ArrayList<>();
        entries.forEach((pos, e) -> { if (pred.test(e)) hits.add(pos); });
        hits.forEach(p -> restore(level, p));
    }

    private void restore(ServerLevel level, BlockPos pos) {
        Entry e = entries.remove(pos);
        if (e == null) return;
        setDirty();
        if (!level.isLoaded(pos)) {
            // keep it pending until the chunk is back
            entries.put(pos, e);
            return;
        }
        BlockState now = level.getBlockState(pos);
        // never overwrite something a player built in the gap
        if (!(now == e.placed || now.isAir() || now.canBeReplaced())) return;
        level.setBlock(pos, e.original, FLAGS);
        if (e.blockEntity != null) {
            BlockEntity be = BlockEntity.loadStatic(pos, e.original, e.blockEntity, level.registryAccess());
            if (be != null) level.setBlockEntity(be);
        }
        level.sendParticles(ModParticles.ZIPPER_SPARK.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
    }

    public static SeparationManager load(CompoundTag tag, HolderLookup.Provider provider) {
        SeparationManager m = new SeparationManager();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        var blocks = provider.lookupOrThrow(Registries.BLOCK);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(c, "Pos").orElse(null);
            if (pos == null) continue;
            m.entries.put(pos, new Entry(
                    NbtUtils.readBlockState(blocks, c.getCompound("Original")),
                    NbtUtils.readBlockState(blocks, c.getCompound("Placed")),
                    c.contains("BE") ? c.getCompound("BE") : null,
                    c.getUUID("Owner"),
                    // after a reload restore everything quickly rather than trusting stale timers
                    0L));
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        entries.forEach((pos, e) -> {
            CompoundTag c = new CompoundTag();
            c.put("Pos", NbtUtils.writeBlockPos(pos));
            c.put("Original", NbtUtils.writeBlockState(e.original));
            c.put("Placed", NbtUtils.writeBlockState(e.placed));
            if (e.blockEntity != null) c.put("BE", e.blockEntity);
            c.putUUID("Owner", e.owner);
            list.add(c);
        });
        tag.put("Entries", list);
        return tag;
    }
}

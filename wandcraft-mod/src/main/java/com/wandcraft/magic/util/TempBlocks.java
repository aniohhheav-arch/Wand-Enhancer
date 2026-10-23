package com.wandcraft.magic.util;

import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Temporary world edits. Every block a spell places is recorded here with the block it replaced and is restored
 * on expiry. The registry is persisted so nothing becomes permanent if the server stops mid-spell.
 */
public final class TempBlocks extends SavedData {
    private static final String KEY = "wandcraft_temp_blocks";

    private static final class Entry {
        BlockState original;
        Block placed;
        long expire;
        int hp;
    }

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    public static TempBlocks get(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TempBlocks::new, TempBlocks::load, null), KEY);
    }

    private static TempBlocks load(CompoundTag tag, HolderLookup.Provider lookup) {
        TempBlocks t = new TempBlocks();
        ListTag list = tag.getList("e", Tag.TAG_COMPOUND);
        var blocks = lookup.lookupOrThrow(Registries.BLOCK);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Entry e = new Entry();
            e.original = NbtUtils.readBlockState(blocks, c.getCompound("o"));
            e.placed = NbtUtils.readBlockState(blocks, c.getCompound("p")).getBlock();
            e.expire = c.getLong("t");
            e.hp = c.getInt("hp");
            t.entries.put(BlockPos.of(c.getLong("pos")), e);
        }
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        entries.forEach((pos, e) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            c.put("o", NbtUtils.writeBlockState(e.original));
            c.put("p", NbtUtils.writeBlockState(e.placed.defaultBlockState()));
            c.putLong("t", e.expire);
            c.putInt("hp", e.hp);
            list.add(c);
        });
        tag.put("e", list);
        return tag;
    }

    public static boolean replaceable(ServerLevel l, BlockPos pos) {
        BlockState s = l.getBlockState(pos);
        if (s.hasBlockEntity() || !l.isInWorldBounds(pos)) return false;
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return s.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && s.getFluidState().isSource();
        return s.canBeReplaced() && s.getDestroySpeed(l, pos) == 0;
    }

    /** Places a temporary block. Returns false if the spot is occupied, protected or already temporary. */
    public static boolean place(ServerLevel l, BlockPos pos, BlockState state, int ticks, int hp, ServerPlayer owner) {
        TempBlocks t = get(l);
        if (t.entries.containsKey(pos) || !replaceable(l, pos)) return false;
        if (owner != null && !l.mayInteract(owner, pos)) return false;
        Entry e = new Entry();
        e.original = l.getBlockState(pos);
        e.placed = state.getBlock();
        e.expire = l.getGameTime() + ticks;
        e.hp = hp;
        t.entries.put(pos.immutable(), e);
        l.setBlock(pos, state, 3);
        t.setDirty();
        return true;
    }

    public static boolean isTemp(ServerLevel l, BlockPos pos) { return get(l).entries.containsKey(pos); }

    /** Damages a temporary block. Returns true if it was removed. */
    public static boolean damage(ServerLevel l, BlockPos pos, int amount) {
        TempBlocks t = get(l);
        Entry e = t.entries.get(pos);
        if (e == null) return false;
        e.hp -= amount;
        t.setDirty();
        if (e.hp <= 0) {
            t.restore(l, pos, e, true);
            t.entries.remove(pos);
            return true;
        }
        return false;
    }

    public static void expireNow(ServerLevel l, BlockPos pos) {
        TempBlocks t = get(l);
        Entry e = t.entries.remove(pos);
        if (e != null) {
            t.restore(l, pos, e, true);
            t.setDirty();
        }
    }

    private void restore(ServerLevel l, BlockPos pos, Entry e, boolean fx) {
        if (l.getBlockState(pos).is(e.placed)) {
            if (fx) l.levelEvent(2001, pos, Block.getId(l.getBlockState(pos)));
            l.setBlock(pos, e.original, 3);
        }
    }

    /** Restores everything immediately (server shutdown). */
    public static void restoreAll(ServerLevel l) {
        TempBlocks t = get(l);
        for (var en : new ArrayList<>(t.entries.entrySet())) t.restore(l, en.getKey(), en.getValue(), false);
        t.entries.clear();
        t.setDirty();
    }

    /** Called every few ticks per level. Restores expired blocks. */
    public static void tick(ServerLevel l) {
        TempBlocks t = get(l);
        if (t.entries.isEmpty()) return;
        long now = l.getGameTime();
        List<BlockPos> done = new ArrayList<>();
        t.entries.forEach((pos, e) -> {
            if (e.placed == com.wandcraft.WandCraft.ICE_CONSTRUCT.get() && e.expire - now <= 40) {
                // Melt warning: crack the ice and let a few drops fall.
                e.placed = com.wandcraft.WandCraft.ICE_CONSTRUCT_MELTING.get();
                l.setBlock(pos, e.placed.defaultBlockState(), 2);
                l.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 4, .3, .1, .3, 0.01);
                l.playSound(null, pos, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.15f, 1.8f);
            }
            if (e.expire <= now || !l.getBlockState(pos).is(e.placed)) done.add(pos);
        });
        for (BlockPos p : done) {
            Entry e = t.entries.remove(p);
            t.restore(l, p, e, e.expire <= now);
        }
        if (!done.isEmpty()) t.setDirty();
    }

    /** Temporary blocks within a sphere. */
    public static List<BlockPos> within(ServerLevel l, net.minecraft.world.phys.Vec3 c, double r) {
        List<BlockPos> out = new ArrayList<>();
        get(l).entries.keySet().forEach(p -> {
            if (p.getCenter().distanceToSqr(c) <= r * r) out.add(p);
        });
        return out;
    }

    /** Blocks that will expire within the given number of ticks, for melt warnings. */
    public static List<BlockPos> expiringSoon(ServerLevel l, Block block, int within) {
        List<BlockPos> out = new ArrayList<>();
        long now = l.getGameTime();
        get(l).entries.forEach((pos, e) -> {
            if (e.placed == block && e.expire - now <= within && e.expire > now) out.add(pos);
        });
        return out;
    }
}

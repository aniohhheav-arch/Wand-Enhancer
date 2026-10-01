package dev.riftverse.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Per-player multiverse progress, persisted and copied on death. */
public final class PlayerMultiverseData implements INBTSerializable<CompoundTag> {
    private static final int MAX_DISCOVERED = 256;

    private final Set<Long> discovered = new LinkedHashSet<>();
    public int journeys;
    public boolean visitedNexus;
    public String homeDimension = "";
    public long homePos;
    public boolean hasHome;
    public long abilityReadyAt;
    public long dashReadyAt;

    public boolean discover(long universe) {
        if (discovered.contains(universe)) return false;
        discovered.add(universe);
        while (discovered.size() > MAX_DISCOVERED) {
            Long first = discovered.iterator().next();
            discovered.remove(first);
        }
        return true;
    }

    public List<Long> discovered() {
        return new ArrayList<>(discovered);
    }

    public int discoveredCount() {
        return discovered.size();
    }

    public void setHome(String dimension, BlockPos pos) {
        homeDimension = dimension;
        homePos = pos.asLong();
        hasHome = true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        long[] arr = new long[discovered.size()];
        int i = 0;
        for (long l : discovered) arr[i++] = l;
        t.put("discovered", new LongArrayTag(arr));
        t.putInt("journeys", journeys);
        t.putBoolean("visitedNexus", visitedNexus);
        t.putString("homeDimension", homeDimension);
        t.putLong("homePos", homePos);
        t.putBoolean("hasHome", hasHome);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        discovered.clear();
        for (long l : t.getLongArray("discovered")) discovered.add(l);
        journeys = t.getInt("journeys");
        visitedNexus = t.getBoolean("visitedNexus");
        homeDimension = t.getString("homeDimension");
        homePos = t.getLong("homePos");
        hasHome = t.getBoolean("hasHome");
    }
}

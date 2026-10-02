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
    private final Set<Long> scanned = new LinkedHashSet<>();
    public int research;
    public int eventsWitnessed;
    public int realitiesErased;
    public int realitiesRebuilt;

    /** Adds research points; returns true if this crossed into a new cosmic rank. */
    public boolean addResearch(int points) {
        dev.riftverse.multiverse.CosmicRank before = rank();
        research = Math.max(0, research + points);
        return rank() != before;
    }

    public dev.riftverse.multiverse.CosmicRank rank() {
        return dev.riftverse.multiverse.CosmicRank.of(research);
    }

    /** Records a scan; returns true the first time a universe is scanned. */
    public boolean scan(long universe) {
        if (scanned.contains(universe)) return false;
        scanned.add(universe);
        while (scanned.size() > MAX_DISCOVERED * 2) scanned.remove(scanned.iterator().next());
        return true;
    }

    public int scannedCount() {
        return scanned.size();
    }

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
        long[] sc = new long[scanned.size()];
        int j = 0;
        for (long l : scanned) sc[j++] = l;
        t.put("scanned", new LongArrayTag(sc));
        t.putInt("research", research);
        t.putInt("eventsWitnessed", eventsWitnessed);
        t.putInt("realitiesErased", realitiesErased);
        t.putInt("realitiesRebuilt", realitiesRebuilt);
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
        scanned.clear();
        for (long l : t.getLongArray("scanned")) scanned.add(l);
        research = t.getInt("research");
        eventsWitnessed = t.getInt("eventsWitnessed");
        realitiesErased = t.getInt("realitiesErased");
        realitiesRebuilt = t.getInt("realitiesRebuilt");
    }
}

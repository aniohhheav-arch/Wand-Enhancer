package dev.riftverse.multiverse;

import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent multiverse state: universe profiles and statuses, definition backups used to restore erased realities, and
 * the event ledger (cooldowns, runtime rarity overrides and history). Statuses are mirrored into a concurrent set so that
 * world-generation threads can cheaply ask whether a universe has been erased.
 */
public final class RealityState extends SavedData {
    public static final String NAME = "riftverse_reality";
    private static final int HISTORY = 64;

    private static final Set<Long> ERASED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> ARCHIVED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> ENDED = ConcurrentHashMap.newKeySet();
    private static final Set<String> SEALED_DIMENSIONS = ConcurrentHashMap.newKeySet();

    public record Backup(CompoundTag spec, long gameTime, String reason) {
        public UniverseSpec restore() {
            return UniverseSpec.load(spec);
        }
    }

    private final Map<Long, UniverseProfile> profiles = new HashMap<>();
    private final Set<String> sealed = new java.util.HashSet<>();
    private final Map<Long, List<Backup>> backups = new HashMap<>();
    private final Map<String, Long> eventReadyAt = new HashMap<>();
    private final Map<String, Integer> rarityOverride = new HashMap<>();
    private final Map<String, Integer> cooldownOverride = new HashMap<>();
    private final Deque<String> history = new ArrayDeque<>();
    public long lastNaturalEvent = Long.MIN_VALUE / 2;
    public boolean migrationEnabled = true;

    public static SavedData.Factory<RealityState> factory() {
        return new SavedData.Factory<>(RealityState::new, RealityState::load, null);
    }

    public static RealityState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static void onServerStarted(MinecraftServer server) {
        ERASED.clear();
        ARCHIVED.clear();
        ENDED.clear();
        SEALED_DIMENSIONS.clear();
        RealityState state = get(server);
        for (UniverseProfile p : state.profiles.values()) mirror(p);
        SEALED_DIMENSIONS.addAll(state.sealed);
    }

    public static void onServerStopped() {
        ERASED.clear();
        ARCHIVED.clear();
        ENDED.clear();
        SEALED_DIMENSIONS.clear();
    }

    private static void mirror(UniverseProfile p) {
        if (p.status.gone()) ERASED.add(p.id);
        else ERASED.remove(p.id);
        if (p.status == RealityStatus.ENDED) ENDED.add(p.id);
        else ENDED.remove(p.id);
        if (p.status == RealityStatus.ARCHIVED) ARCHIVED.add(p.id);
        else ARCHIVED.remove(p.id);
    }

    /** Thread-safe: whether new terrain in this universe should be generated as void. */
    public static boolean isErased(UniverseId id) {
        return !ERASED.isEmpty() && ERASED.contains(id.pack());
    }

    /** Thread-safe: whether travellers may enter this universe. */
    public static boolean isAccessible(UniverseId id) {
        long k = id.pack();
        return !ERASED.contains(k) && !ARCHIVED.contains(k);
    }

    public static RealityStatus statusOf(UniverseId id) {
        long k = id.pack();
        if (ENDED.contains(k)) return RealityStatus.ENDED;
        if (ERASED.contains(k)) return RealityStatus.ERASED;
        if (ARCHIVED.contains(k)) return RealityStatus.ARCHIVED;
        return RealityStatus.ACTIVE;
    }

    // ------------------------------------------------------------------ profiles

    public UniverseProfile profile(UniverseId id) {
        return profiles.computeIfAbsent(id.pack(), UniverseProfile::new);
    }

    @Nullable
    public UniverseProfile existingProfile(UniverseId id) {
        return profiles.get(id.pack());
    }

    public Collection<UniverseProfile> profiles() {
        return profiles.values();
    }

    public void setStatus(UniverseId id, RealityStatus status) {
        UniverseProfile p = profile(id);
        p.status = status;
        mirror(p);
        setDirty();
    }

    public void touch() {
        setDirty();
    }

    // ------------------------------------------------------------------ sealed (ended) dimensions

    /** Thread-safe: whether a whole dimension (Overworld, Nether, End, modded) has been permanently ended. */
    public static boolean isSealed(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        return !SEALED_DIMENSIONS.isEmpty() && SEALED_DIMENSIONS.contains(dim.location().toString());
    }

    public void seal(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        sealed.add(dim.location().toString());
        SEALED_DIMENSIONS.add(dim.location().toString());
        setDirty();
    }

    public void unseal(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        sealed.remove(dim.location().toString());
        SEALED_DIMENSIONS.remove(dim.location().toString());
        setDirty();
    }

    public java.util.List<String> sealedDimensions() {
        return new java.util.ArrayList<>(sealed);
    }

    public void forgetBackups(UniverseId id) {
        backups.remove(id.pack());
        setDirty();
    }

    // ------------------------------------------------------------------ backups

    public void backup(UniverseSpec spec, long gameTime, String reason, int keep) {
        List<Backup> list = backups.computeIfAbsent(spec.id.pack(), k -> new ArrayList<>());
        list.add(new Backup(spec.save(), gameTime, reason));
        while (list.size() > Math.max(1, keep)) list.remove(0);
        setDirty();
    }

    public List<Backup> backups(UniverseId id) {
        return backups.getOrDefault(id.pack(), List.of());
    }

    @Nullable
    public Backup latestBackup(UniverseId id) {
        List<Backup> list = backups.get(id.pack());
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public int backupCount() {
        int n = 0;
        for (List<Backup> l : backups.values()) n += l.size();
        return n;
    }

    // ------------------------------------------------------------------ event ledger

    public long eventReadyAt(String event) {
        return eventReadyAt.getOrDefault(event, 0L);
    }

    public void setEventReadyAt(String event, long gameTime) {
        eventReadyAt.put(event, gameTime);
        setDirty();
    }

    @Nullable
    public Integer rarityOverride(String event) {
        return rarityOverride.get(event);
    }

    public void setRarityOverride(String event, @Nullable Integer value) {
        if (value == null) rarityOverride.remove(event);
        else rarityOverride.put(event, value);
        setDirty();
    }

    @Nullable
    public Integer cooldownOverride(String event) {
        return cooldownOverride.get(event);
    }

    public void setCooldownOverride(String event, @Nullable Integer minutes) {
        if (minutes == null) cooldownOverride.remove(event);
        else cooldownOverride.put(event, minutes);
        setDirty();
    }

    public void record(String line) {
        history.addLast(line);
        while (history.size() > HISTORY) history.removeFirst();
        setDirty();
    }

    public List<String> history() {
        return new ArrayList<>(history);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ps = new ListTag();
        for (UniverseProfile p : profiles.values()) ps.add(p.save());
        tag.put("profiles", ps);
        ListTag bs = new ListTag();
        for (Map.Entry<Long, List<Backup>> e : backups.entrySet()) {
            for (Backup b : e.getValue()) {
                CompoundTag t = new CompoundTag();
                t.putLong("universe", e.getKey());
                t.put("spec", b.spec());
                t.putLong("time", b.gameTime());
                t.putString("reason", b.reason());
                bs.add(t);
            }
        }
        tag.put("backups", bs);
        CompoundTag ready = new CompoundTag();
        eventReadyAt.forEach(ready::putLong);
        tag.put("eventReadyAt", ready);
        CompoundTag rar = new CompoundTag();
        rarityOverride.forEach(rar::putInt);
        tag.put("rarityOverride", rar);
        CompoundTag cd = new CompoundTag();
        cooldownOverride.forEach(cd::putInt);
        tag.put("cooldownOverride", cd);
        ListTag hist = new ListTag();
        for (String s : history) hist.add(StringTag.valueOf(s));
        tag.put("history", hist);
        tag.putLong("lastNaturalEvent", lastNaturalEvent);
        tag.putBoolean("migrationEnabled", migrationEnabled);
        ListTag sl = new ListTag();
        for (String d : sealed) sl.add(StringTag.valueOf(d));
        tag.put("sealedDimensions", sl);
        return tag;
    }

    public static RealityState load(CompoundTag tag, HolderLookup.Provider registries) {
        RealityState s = new RealityState();
        ListTag ps = tag.getList("profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            UniverseProfile p = UniverseProfile.load(ps.getCompound(i));
            s.profiles.put(p.id, p);
        }
        ListTag bs = tag.getList("backups", Tag.TAG_COMPOUND);
        for (int i = 0; i < bs.size(); i++) {
            CompoundTag t = bs.getCompound(i);
            s.backups.computeIfAbsent(t.getLong("universe"), k -> new ArrayList<>())
                    .add(new Backup(t.getCompound("spec"), t.getLong("time"), t.getString("reason")));
        }
        CompoundTag ready = tag.getCompound("eventReadyAt");
        for (String k : ready.getAllKeys()) s.eventReadyAt.put(k, ready.getLong(k));
        CompoundTag rar = tag.getCompound("rarityOverride");
        for (String k : rar.getAllKeys()) s.rarityOverride.put(k, rar.getInt(k));
        CompoundTag cd = tag.getCompound("cooldownOverride");
        for (String k : cd.getAllKeys()) s.cooldownOverride.put(k, cd.getInt(k));
        ListTag hist = tag.getList("history", Tag.TAG_STRING);
        for (int i = 0; i < hist.size(); i++) s.history.addLast(hist.getString(i));
        if (tag.contains("lastNaturalEvent")) s.lastNaturalEvent = tag.getLong("lastNaturalEvent");
        s.migrationEnabled = !tag.contains("migrationEnabled") || tag.getBoolean("migrationEnabled");
        ListTag sl = tag.getList("sealedDimensions", Tag.TAG_STRING);
        for (int i = 0; i < sl.size(); i++) s.sealed.add(sl.getString(i));
        return s;
    }
}

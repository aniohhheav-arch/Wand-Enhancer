package dev.riftverse.universe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The server's catalogue of realities. Specs that have been visited or manifested are persisted; every other slot is
 * derived deterministically from the world seed, so the multiverse is effectively infinite.
 */
public final class UniverseRegistry extends SavedData {
    public static final String NAME = "riftverse_universes";

    private static final Map<Long, UniverseSpec> DERIVED = new ConcurrentHashMap<>();
    private static volatile UniverseRegistry active;
    private static volatile long cachedSeed;
    private static volatile boolean seedKnown;

    private final Map<Long, UniverseSpec> stored = new ConcurrentHashMap<>();
    private final Map<Long, UUID> manifestedBy = new ConcurrentHashMap<>();
    private int promptCounter;

    public static SavedData.Factory<UniverseRegistry> factory() {
        return new SavedData.Factory<>(UniverseRegistry::new, UniverseRegistry::load, null);
    }

    public static UniverseRegistry get(MinecraftServer server) {
        UniverseRegistry reg = server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
        active = reg;
        return reg;
    }

    public static void onServerStarted(MinecraftServer server) {
        cachedSeed = server.getWorldData().worldGenOptions().seed();
        seedKnown = true;
        DERIVED.clear();
        get(server);
    }

    public static void onServerStopped() {
        active = null;
        seedKnown = false;
        DERIVED.clear();
    }

    private static long worldSeed() {
        if (!seedKnown) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                cachedSeed = server.getWorldData().worldGenOptions().seed();
                seedKnown = true;
            }
        }
        return cachedSeed;
    }

    /** Thread-safe lookup used by world generation workers. */
    public static UniverseSpec specFor(UniverseId id) {
        UniverseRegistry reg = active;
        if (reg != null) {
            UniverseSpec s = reg.stored.get(id.pack());
            if (s != null) return s;
        }
        return DERIVED.computeIfAbsent(id.pack(), k -> SpecFactory.derive(id, worldSeed()));
    }

    public static UniverseSpec specAt(int blockX, int blockZ) {
        return specFor(UniverseId.ofBlock(blockX, blockZ));
    }

    /** Persist a spec so that its identity survives generator changes and so it shows up in catalogues. */
    public void remember(UniverseSpec spec) {
        if (stored.putIfAbsent(spec.id.pack(), spec) == null) setDirty();
    }

    public boolean isStored(UniverseId id) {
        return stored.containsKey(id.pack());
    }

    @Nullable
    public UniverseSpec stored(UniverseId id) {
        return stored.get(id.pack());
    }

    public Collection<UniverseSpec> all() {
        return stored.values();
    }

    public List<UniverseSpec> manifestedBy(UUID player) {
        List<UniverseSpec> out = new ArrayList<>();
        for (Map.Entry<Long, UUID> e : manifestedBy.entrySet()) {
            if (e.getValue().equals(player)) {
                UniverseSpec s = stored.get(e.getKey());
                if (s != null) out.add(s);
            }
        }
        return out;
    }

    public UniverseSpec primeSpec(Archetype archetype) {
        UniverseSpec s = specFor(UniverseId.prime(archetype));
        remember(s);
        return s;
    }

    /** Picks an unexplored random slot, optionally constrained to a set of archetypes. */
    public UniverseSpec randomUniverse(Random random, @Nullable Archetype[] allowed) {
        for (int attempt = 0; attempt < 400; attempt++) {
            int gx = random.nextInt(UniverseId.MAX_SLOT * 2 + 1) - UniverseId.MAX_SLOT;
            int gz = 1 + random.nextInt(UniverseId.MAX_SLOT);
            UniverseId id = new UniverseId(gx, gz);
            if (stored.containsKey(id.pack())) continue;
            UniverseSpec s = specFor(id);
            if (allowed != null && allowed.length > 0) {
                boolean ok = false;
                for (Archetype a : allowed) ok |= s.archetype == a;
                if (!ok) continue;
            }
            remember(s);
            return s;
        }
        return primeSpec(allowed != null && allowed.length > 0 ? allowed[random.nextInt(allowed.length)] : Archetype.ASTRAL);
    }

    /** Manifests a brand-new universe from a free-text description. */
    public PromptInterpreter.Result manifest(String prompt, UUID author) {
        int n = promptCounter++;
        int gx = (n % (UniverseId.MAX_SLOT * 2)) - UniverseId.MAX_SLOT;
        int gz = -1 - n / (UniverseId.MAX_SLOT * 2);
        UniverseId id = new UniverseId(gx, gz);
        long seed = PromptInterpreter.seedFor(prompt, worldSeed(), n);
        PromptInterpreter.Result result = PromptInterpreter.interpret(prompt, id, seed);
        stored.put(id.pack(), result.spec());
        manifestedBy.put(id.pack(), author);
        DERIVED.remove(id.pack());
        setDirty();
        return result;
    }

    public int manifestCount(UUID author) {
        int c = 0;
        for (UUID u : manifestedBy.values()) if (u.equals(author)) c++;
        return c;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UniverseSpec s : stored.values()) {
            CompoundTag t = s.save();
            UUID author = manifestedBy.get(s.id.pack());
            if (author != null) t.putUUID("author", author);
            list.add(t);
        }
        tag.put("universes", list);
        tag.putInt("promptCounter", promptCounter);
        return tag;
    }

    public static UniverseRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        UniverseRegistry reg = new UniverseRegistry();
        ListTag list = tag.getList("universes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UniverseSpec s = UniverseSpec.load(t);
            reg.stored.put(s.id.pack(), s);
            if (t.hasUUID("author")) reg.manifestedBy.put(s.id.pack(), t.getUUID("author"));
        }
        reg.promptCounter = tag.getInt("promptCounter");
        return reg;
    }
}

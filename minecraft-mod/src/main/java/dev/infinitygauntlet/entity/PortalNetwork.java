package dev.infinitygauntlet.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-wide registry of rupture portals. Portals look their partner up here rather than by entity reference, so
 * links survive chunk unloading, server restarts and dimension boundaries.
 */
public final class PortalNetwork extends SavedData {
    private static final String NAME = "infinitygauntlet_portals";

    public record Entry(UUID id, ResourceKey<Level> dimension, Vec3 pos, float radius, @Nullable UUID partner, UUID owner,
                        boolean exitDown, long created) {
        public Entry withPartner(@Nullable UUID newPartner) {
            return new Entry(id, dimension, pos, radius, newPartner, owner, exitDown, created);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("dim", dimension.location().toString());
            tag.putDouble("x", pos.x);
            tag.putDouble("y", pos.y);
            tag.putDouble("z", pos.z);
            tag.putFloat("radius", radius);
            if (partner != null) {
                tag.putUUID("partner", partner);
            }
            tag.putUUID("owner", owner);
            tag.putBoolean("exitDown", exitDown);
            tag.putLong("created", created);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            return new Entry(tag.getUUID("id"),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim"))),
                    new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                    tag.getFloat("radius"),
                    tag.hasUUID("partner") ? tag.getUUID("partner") : null,
                    tag.getUUID("owner"),
                    tag.getBoolean("exitDown"),
                    tag.getLong("created"));
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static PortalNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PortalNetwork::new, PortalNetwork::load, null), NAME);
    }

    private static PortalNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        PortalNetwork network = new PortalNetwork();
        for (Tag t : tag.getList("portals", Tag.TAG_COMPOUND)) {
            Entry e = Entry.load((CompoundTag) t);
            network.entries.put(e.id(), e);
        }
        return network;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.values().forEach(e -> list.add(e.save()));
        tag.put("portals", list);
        return tag;
    }

    @Nullable
    public Entry get(UUID id) {
        return entries.get(id);
    }

    public void put(Entry entry) {
        entries.put(entry.id(), entry);
        setDirty();
    }

    public void link(UUID a, UUID b) {
        Entry ea = entries.get(a);
        Entry eb = entries.get(b);
        if (ea != null && eb != null) {
            entries.put(a, ea.withPartner(b));
            entries.put(b, eb.withPartner(a));
            setDirty();
        }
    }

    /** Removes a portal (and its partner) from the network and starts the collapse animation of any loaded instance. */
    public void close(MinecraftServer server, UUID id) {
        Entry entry = entries.remove(id);
        if (entry == null) {
            return;
        }
        setDirty();
        collapseLoaded(server, entry);
        if (entry.partner() != null) {
            close(server, entry.partner());
        }
    }

    private static void collapseLoaded(MinecraftServer server, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level != null && level.getEntity(entry.id()) instanceof RupturePortalEntity portal) {
            portal.startClosing();
        }
    }

    /** Portal ids owned by {@code owner}, oldest first, counting a linked pair once (by its older member). */
    public List<Entry> pairsOwnedBy(UUID owner) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!e.owner().equals(owner)) {
                continue;
            }
            Entry partner = e.partner() == null ? null : entries.get(e.partner());
            if (partner == null || e.created() < partner.created() || e.created() == partner.created() && e.id().compareTo(partner.id()) < 0) {
                result.add(e);
            }
        }
        return result;
    }

    public List<Entry> ownedBy(UUID owner) {
        return entries.values().stream().filter(e -> e.owner().equals(owner)).toList();
    }

    public List<Entry> all() {
        return List.copyOf(entries.values());
    }
}

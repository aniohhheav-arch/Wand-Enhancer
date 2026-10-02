package dev.riftverse.multiverse;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Everything the multiverse remembers about one universe beyond its generation spec. */
public final class UniverseProfile {
    public final long id;
    public RealityStatus status = RealityStatus.ACTIVE;
    public float stability = 100f;
    public int visits;
    public long firstSeen = -1;
    @Nullable
    public UUID discoverer;
    public String discovererName = "";
    public int events;
    public String lastEvent = "";
    public int erasures;
    public int rebuilds;
    public int scans;

    public UniverseProfile(long id) {
        this.id = id;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        t.putInt("status", status.ordinal());
        t.putFloat("stability", stability);
        t.putInt("visits", visits);
        t.putLong("firstSeen", firstSeen);
        if (discoverer != null) t.putUUID("discoverer", discoverer);
        t.putString("discovererName", discovererName);
        t.putInt("events", events);
        t.putString("lastEvent", lastEvent);
        t.putInt("erasures", erasures);
        t.putInt("rebuilds", rebuilds);
        t.putInt("scans", scans);
        return t;
    }

    public static UniverseProfile load(CompoundTag t) {
        UniverseProfile p = new UniverseProfile(t.getLong("id"));
        p.status = RealityStatus.byId(t.getInt("status"));
        p.stability = t.contains("stability") ? t.getFloat("stability") : 100f;
        p.visits = t.getInt("visits");
        p.firstSeen = t.contains("firstSeen") ? t.getLong("firstSeen") : -1;
        if (t.hasUUID("discoverer")) p.discoverer = t.getUUID("discoverer");
        p.discovererName = t.getString("discovererName");
        p.events = t.getInt("events");
        p.lastEvent = t.getString("lastEvent");
        p.erasures = t.getInt("erasures");
        p.rebuilds = t.getInt("rebuilds");
        p.scans = t.getInt("scans");
        return p;
    }

    /** A one-word reading of the stability value. */
    public String stabilityLabel() {
        if (stability >= 90) return "Stable";
        if (stability >= 65) return "Wavering";
        if (stability >= 40) return "Unstable";
        if (stability >= 15) return "Fracturing";
        return "Critical";
    }
}

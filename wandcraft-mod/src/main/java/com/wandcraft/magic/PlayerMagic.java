package com.wandcraft.magic;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Per-player magic state, stored as a data attachment and mirrored to the owning client. */
public class PlayerMagic implements INBTSerializable<CompoundTag> {
    public float mana = 100;
    public String selected = "arcane_bolt";
    /** Absolute game-time tick at which each spell's cooldown ends. */
    public final Map<String, Long> cooldownEnd = new HashMap<>();
    /** Successful uses per spell; drives mastery and wizard level. */
    public final Map<String, Integer> uses = new HashMap<>();
    public long wingsUntil;
    public long pactUntil;
    public long pactCooldownUntil;
    public int overloadCharges;
    public long overloadUntil;

    // Transient server-side state, never saved or synced.
    public transient long lastCastTick;
    public transient String lastEchoSpell;
    public transient float lastEchoCharge;
    public transient String channelSpell;
    public transient int channelTicks;
    public transient Map<String, Object> channelState;
    public transient long lastWingDash;
    public transient boolean dirty = true;
    // Defensive state (server only).
    public transient float shieldCapacity;
    public transient float shieldReduction;
    public transient double shieldArc;
    public transient long parryUntil;
    public transient long mirrorUntil;
    public transient long phaseUntil;
    public transient long softFallUntil;
    /** Mobs further than a few blocks lose track of a stealthed player. */
    public transient long stealthUntil;

    public int totalUses() {
        int t = 0;
        for (int v : uses.values()) t += v;
        return t;
    }

    public int level() { return Math.min(30, (int) Math.floor(Math.sqrt(totalUses() / 3.0))); }
    public float maxMana() { return 100 + 4 * level(); }
    public Rank rank(String id) { return Rank.forUses(uses.getOrDefault(id, 0)); }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag t = new CompoundTag();
        t.putFloat("mana", mana);
        t.putString("sel", selected);
        CompoundTag cd = new CompoundTag();
        cooldownEnd.forEach(cd::putLong);
        t.put("cd", cd);
        CompoundTag u = new CompoundTag();
        uses.forEach(u::putInt);
        t.put("uses", u);
        t.putLong("wings", wingsUntil);
        t.putLong("pact", pactUntil);
        t.putLong("pactcd", pactCooldownUntil);
        t.putInt("ovc", overloadCharges);
        t.putLong("ovt", overloadUntil);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider p, CompoundTag t) {
        mana = t.getFloat("mana");
        selected = t.getString("sel");
        cooldownEnd.clear();
        CompoundTag cd = t.getCompound("cd");
        for (String k : cd.getAllKeys()) cooldownEnd.put(k, cd.getLong(k));
        uses.clear();
        CompoundTag u = t.getCompound("uses");
        for (String k : u.getAllKeys()) uses.put(k, u.getInt(k));
        wingsUntil = t.getLong("wings");
        pactUntil = t.getLong("pact");
        pactCooldownUntil = t.getLong("pactcd");
        overloadCharges = t.getInt("ovc");
        overloadUntil = t.getLong("ovt");
        if (selected.isEmpty()) selected = "arcane_bolt";
        dirty = true;
    }
}

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
    /** Player-specific override set by /wandcraft cooldowns off self. */
    public boolean cooldownsOff;
    /** Client-side mirror of the world-level cooldown setting (delivered with every sync). */
    public transient boolean worldCooldownsOff;
    /** Items stored in the Pocket Rift. Persisted, so nothing is ever lost when the rift closes. */
    public final net.minecraft.world.SimpleContainer pocket = new net.minecraft.world.SimpleContainer(9);

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
    /** Zone entity of the caster's current Portal Pair (server only). */
    public transient java.util.UUID portalZone;
    /** Synced for UI/casting checks: 0 none, 1 first portal placed, 2 linked pair open. */
    public int portalState;
    public transient net.minecraft.world.phys.Vec3 anchorPos;
    public transient net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> anchorDim;
    public transient long anchorUntil;

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
        t.putInt("portalState", portalState);
        t.putBoolean("cdOffSelf", cooldownsOff);
        t.putBoolean("cdOffWorld", worldCooldownsOff);
        t.put("pocket", pocket.createTag(p));
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
        portalState = t.getInt("portalState");
        cooldownsOff = t.getBoolean("cdOffSelf");
        worldCooldownsOff = t.getBoolean("cdOffWorld");
        pocket.fromTag(t.getList("pocket", Tag.TAG_COMPOUND), p);
        if (selected.isEmpty()) selected = "arcane_bolt";
        dirty = true;
    }
}

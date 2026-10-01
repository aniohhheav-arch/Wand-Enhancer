package com.infinitemultiverse.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player multiverse state, stored as a NeoForge data attachment on the server player.
 *
 * <p>Persisted: energy, cooldowns, loadout and unlocks. Transient (reset on relog/death): active toggles, regen delay,
 * fall protection and sync bookkeeping. Toggles are deliberately not persisted so a toggle can never be "stuck on"
 * after a crash without its {@code onDeactivate} cleanup having a matching activation.
 */
public final class PlayerMultiverseData {
    public static final int LOADOUT_SIZE = 5;

    public static final Codec<PlayerMultiverseData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("energy", -1f).forGetter(data -> data.energy),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("cooldowns", Map.of()).forGetter(data -> data.cooldowns),
            Codec.STRING.listOf().optionalFieldOf("loadout", List.of()).forGetter(PlayerMultiverseData::encodeLoadout),
            ResourceLocation.CODEC.listOf().optionalFieldOf("unlocked", List.of()).forGetter(data -> List.copyOf(data.unlocked))
    ).apply(instance, PlayerMultiverseData::new));

    /** Negative means "not initialised yet"; the first server tick fills it to the configured maximum. */
    private float energy;
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();
    private final ResourceLocation[] loadout = new ResourceLocation[LOADOUT_SIZE];
    private final Set<ResourceLocation> unlocked = new HashSet<>();

    private final Map<ResourceLocation, Integer> activeToggles = new LinkedHashMap<>();
    private int regenDelay;
    private int fallProtectionTicks;
    private boolean structuralDirty = true;
    private boolean energyDirty = true;

    public PlayerMultiverseData() {
        this.energy = -1f;
    }

    private PlayerMultiverseData(float energy, Map<ResourceLocation, Integer> cooldowns, List<String> loadout, List<ResourceLocation> unlocked) {
        this.energy = energy;
        this.cooldowns.putAll(cooldowns);
        for (int i = 0; i < Math.min(LOADOUT_SIZE, loadout.size()); i++) {
            this.loadout[i] = loadout.get(i).isEmpty() ? null : ResourceLocation.tryParse(loadout.get(i));
        }
        this.unlocked.addAll(unlocked);
    }

    private List<String> encodeLoadout() {
        List<String> out = new ArrayList<>(LOADOUT_SIZE);
        for (ResourceLocation id : loadout) {
            out.add(id == null ? "" : id.toString());
        }
        return out;
    }

    // ---- energy ----

    public float energy() {
        return energy;
    }

    public boolean isEnergyInitialised() {
        return energy >= 0f;
    }

    public void setEnergy(float value, float max) {
        float clamped = Math.max(0f, Math.min(value, max));
        if (clamped != energy) {
            energy = clamped;
            energyDirty = true;
        }
    }

    /** Spends energy for an activation and pauses regeneration. */
    public void consume(float amount, int regenDelayTicks) {
        if (amount <= 0f) {
            return;
        }
        energy = Math.max(0f, energy - amount);
        regenDelay = Math.max(regenDelay, regenDelayTicks);
        energyDirty = true;
    }

    /** Continuous drain (upkeep, absorbed damage). Returns false and empties the pool if it cannot be paid. */
    public boolean drain(float amount) {
        if (amount <= 0f) {
            return true;
        }
        energyDirty = true;
        if (energy >= amount) {
            energy -= amount;
            return true;
        }
        energy = 0f;
        return false;
    }

    public void tickRegen(float perTick, float max) {
        if (regenDelay > 0) {
            regenDelay--;
            return;
        }
        if (energy < max && perTick > 0f) {
            setEnergy(energy + perTick, max);
        }
    }

    // ---- cooldowns ----

    public int cooldown(ResourceLocation ability) {
        return cooldowns.getOrDefault(ability, 0);
    }

    public void startCooldown(ResourceLocation ability, int ticks) {
        if (ticks > 0) {
            cooldowns.put(ability, ticks);
        } else {
            cooldowns.remove(ability);
        }
        structuralDirty = true;
    }

    public void clearCooldowns() {
        cooldowns.clear();
        structuralDirty = true;
    }

    /** Expired cooldowns are dropped silently; clients count down their own copy. */
    public void tickCooldowns() {
        if (cooldowns.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<ResourceLocation, Integer>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public Map<ResourceLocation, Integer> cooldownsView() {
        return Collections.unmodifiableMap(cooldowns);
    }

    // ---- loadout ----

    public Optional<ResourceLocation> loadoutSlot(int slot) {
        return slot >= 0 && slot < LOADOUT_SIZE ? Optional.ofNullable(loadout[slot]) : Optional.empty();
    }

    public void setLoadoutSlot(int slot, @Nullable ResourceLocation ability) {
        loadout[slot] = ability;
        structuralDirty = true;
    }

    public boolean isInLoadout(ResourceLocation ability) {
        for (ResourceLocation id : loadout) {
            if (ability.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public List<Optional<ResourceLocation>> loadoutView() {
        List<Optional<ResourceLocation>> out = new ArrayList<>(LOADOUT_SIZE);
        for (ResourceLocation id : loadout) {
            out.add(Optional.ofNullable(id));
        }
        return out;
    }

    // ---- unlocks ----

    public boolean hasUnlocked(ResourceLocation ability) {
        return unlocked.contains(ability);
    }

    public boolean unlock(ResourceLocation ability) {
        boolean changed = unlocked.add(ability);
        structuralDirty |= changed;
        return changed;
    }

    public boolean lock(ResourceLocation ability) {
        boolean changed = unlocked.remove(ability);
        structuralDirty |= changed;
        return changed;
    }

    public Set<ResourceLocation> unlockedView() {
        return Collections.unmodifiableSet(unlocked);
    }

    /** Drops references to abilities that no longer exist (removed addon, renamed id). */
    public void prune(Predicate<ResourceLocation> exists) {
        for (int i = 0; i < LOADOUT_SIZE; i++) {
            if (loadout[i] != null && !exists.test(loadout[i])) {
                loadout[i] = null;
            }
        }
        unlocked.removeIf(id -> !exists.test(id));
        cooldowns.keySet().removeIf(id -> !exists.test(id));
        structuralDirty = true;
    }

    // ---- toggles ----

    public boolean isActive(ResourceLocation ability) {
        return activeToggles.containsKey(ability);
    }

    public void setActive(ResourceLocation ability) {
        activeToggles.put(ability, 0);
        structuralDirty = true;
    }

    public boolean removeActive(ResourceLocation ability) {
        boolean removed = activeToggles.remove(ability) != null;
        structuralDirty |= removed;
        return removed;
    }

    public int incrementActiveTicks(ResourceLocation ability) {
        return activeToggles.merge(ability, 1, Integer::sum);
    }

    public Set<ResourceLocation> activeView() {
        return Collections.unmodifiableSet(activeToggles.keySet());
    }

    // ---- misc transient state ----

    public int fallProtectionTicks() {
        return fallProtectionTicks;
    }

    public void grantFallProtection(int ticks) {
        fallProtectionTicks = Math.max(fallProtectionTicks, ticks);
    }

    public void clearFallProtection() {
        fallProtectionTicks = 0;
    }

    public void tickFallProtection() {
        if (fallProtectionTicks > 0) {
            fallProtectionTicks--;
        }
    }

    // ---- sync bookkeeping ----

    public void markStructuralDirty() {
        structuralDirty = true;
    }

    public boolean isStructuralDirty() {
        return structuralDirty;
    }

    public boolean isEnergyDirty() {
        return energyDirty;
    }

    public void clearDirty() {
        structuralDirty = false;
        energyDirty = false;
    }
}

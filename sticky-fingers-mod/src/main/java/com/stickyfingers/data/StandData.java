package com.stickyfingers.data;

import com.stickyfingers.ability.AbilityType;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.registry.ModAttachments;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Per-player Stand state. Lives as a data attachment on the player (persisted, kept on death)
 * and is mirrored to the owning client through {@link com.stickyfingers.network.StandSyncPayload}.
 */
public final class StandData implements INBTSerializable<CompoundTag> {
    public static final int STORAGE_SIZE = 27;

    private boolean hasStand;
    private boolean summoned;
    private float energy = 100f;
    private final long[] cooldownEnd = new long[AbilityType.VALUES.length];
    private int selected;
    private final SimpleContainer storage = new SimpleContainer(STORAGE_SIZE);
    private com.stickyfingers.ability.StandType standType = com.stickyfingers.ability.StandType.STICKY_FINGERS;

    // transient runtime state (not persisted)
    private int hiddenTicks;
    private boolean ambushHide;
    private int pendingTravelZipper = -1;
    private int markedTarget = -1;
    private long markedUntil;
    private int exitZipper = -1;
    private boolean dirty = true;
    private boolean initialized;
    private int orientation;
    private int hideOriginZipper = -1;
    private net.minecraft.world.phys.Vec3 hideOrigin = net.minecraft.world.phys.Vec3.ZERO;
    // Star Platinum time stop
    private boolean timeStopActive;
    private int timeStopTicks;
    // D4C portals / clone tracking
    private int d4cPortalA = -1;
    private int d4cPortalB = -1;
    private int d4cClone = -1;
    private boolean loveTrain;
    // D4C parallel dimension: origin coords to return to, or NaN when not in parallel
    private double dimOriginX = Double.NaN;
    private double dimOriginY = Double.NaN;
    private double dimOriginZ = Double.NaN;

    public static StandData get(Player player) {
        return player.getData(ModAttachments.STAND);
    }

    public boolean hasStand() { return hasStand; }
    public void setHasStand(boolean v) { hasStand = v; initialized = true; markDirty(); }
    public boolean isInitialized() { return initialized; }
    public void setInitialized() { initialized = true; }

    public boolean isSummoned() { return summoned; }
    public void setSummoned(boolean v) { if (summoned != v) { summoned = v; markDirty(); } }

    public float energy() { return energy; }
    public void setEnergy(float v) {
        float max = (float) (double) SFConfig.safe(SFConfig.MAX_ENERGY::get, 100.0);
        float clamped = Math.max(0, Math.min(max, v));
        if (Math.abs(clamped - energy) > 1e-3) { energy = clamped; markDirty(); }
    }

    public long cooldownEnd(AbilityType t) { return cooldownEnd[t.ordinal()]; }
    public void setCooldownEnd(AbilityType t, long gameTime) { cooldownEnd[t.ordinal()] = gameTime; markDirty(); }
    public int cooldownRemaining(AbilityType t, long now) { return (int) Math.max(0, cooldownEnd[t.ordinal()] - now); }

    public AbilityType selected() { return AbilityType.byId(selected); }
    public void setSelected(AbilityType t) { selected = t.ordinal(); markDirty(); }

    public SimpleContainer storage() { return storage; }

    public int hiddenTicks() { return hiddenTicks; }
    public boolean isHidden() { return hiddenTicks > 0; }
    public boolean isAmbushHide() { return ambushHide; }
    public void setHidden(int ticks, boolean ambush) { hiddenTicks = ticks; ambushHide = ambush; markDirty(); }
    public void tickHidden() { if (hiddenTicks > 0) hiddenTicks--; }

    public int pendingTravelZipper() { return pendingTravelZipper; }
    public void setPendingTravelZipper(int id) { pendingTravelZipper = id; }

    public int markedTarget(long now) { return now <= markedUntil ? markedTarget : -1; }
    public void mark(int entityId, long until) { markedTarget = entityId; markedUntil = until; }

    public int exitZipper() { return exitZipper; }
    public void setExitZipper(int id) { exitZipper = id; markDirty(); }

    /** Zipper orientation mode: 0 auto, 1 horizontal, 2 vertical, 3 diagonal, 4 anti-diagonal. */
    public int orientation() { return orientation; }
    public void setOrientation(int o) { orientation = Math.floorMod(o, 5); }

    public int hideOriginZipper() { return hideOriginZipper; }
    public net.minecraft.world.phys.Vec3 hideOrigin() { return hideOrigin; }
    public void setHideOrigin(int zipper, net.minecraft.world.phys.Vec3 pos) { hideOriginZipper = zipper; hideOrigin = pos; }

    public com.stickyfingers.ability.StandType standType() { return standType; }
    public void setStandType(com.stickyfingers.ability.StandType t) { standType = t; selected = 0; markDirty(); }

    public boolean isTimeStopActive() { return timeStopActive; }
    public int timeStopTicks() { return timeStopTicks; }
    public void setTimeStop(boolean active, int ticks) { timeStopActive = active; timeStopTicks = ticks; markDirty(); }
    public void tickTimeStop() { if (timeStopTicks > 0) timeStopTicks--; }

    public int d4cPortalA() { return d4cPortalA; }
    public int d4cPortalB() { return d4cPortalB; }
    public void setPortals(int a, int b) { d4cPortalA = a; d4cPortalB = b; }
    public int d4cClone() { return d4cClone; }
    public void setClone(int id) { d4cClone = id; }
    public boolean isLoveTrain() { return loveTrain; }
    public void setLoveTrain(boolean v) { loveTrain = v; markDirty(); }

    public boolean isInParallelDimension() { return !Double.isNaN(dimOriginX); }
    public double dimOriginX() { return dimOriginX; }
    public double dimOriginY() { return dimOriginY; }
    public double dimOriginZ() { return dimOriginZ; }
    public void setDimOrigin(double x, double y, double z) { dimOriginX = x; dimOriginY = y; dimOriginZ = z; }
    public void clearDimOrigin() { dimOriginX = Double.NaN; dimOriginY = Double.NaN; dimOriginZ = Double.NaN; }

    public boolean consumeDirty() { boolean d = dirty; dirty = false; return d; }
    public void markDirty() { dirty = true; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasStand", hasStand);
        tag.putBoolean("Init", initialized);
        tag.putFloat("Energy", energy);
        tag.putInt("Selected", selected);
        tag.putInt("StandType", standType.ordinal());
        NonNullList<ItemStack> items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < STORAGE_SIZE; i++) items.set(i, storage.getItem(i));
        tag.put("Storage", ContainerHelper.saveAllItems(new CompoundTag(), items, provider));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        hasStand = tag.getBoolean("HasStand");
        initialized = tag.getBoolean("Init");
        energy = tag.getFloat("Energy");
        selected = tag.getInt("Selected");
        standType = com.stickyfingers.ability.StandType.byId(tag.getInt("StandType"));
        NonNullList<ItemStack> items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("Storage"), items, provider);
        for (int i = 0; i < STORAGE_SIZE; i++) storage.setItem(i, items.get(i));
        dirty = true;
    }
}

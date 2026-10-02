package dev.mysticarts.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the power system knows about one player. Persistent fields survive death and relogs; transient fields
 * describe effects that are running right now and are rebuilt on login.
 */
public final class PowerData implements INBTSerializable<CompoundTag> {
    public static final int TIER_INITIATE = 1;
    public static final int TIER_ADEPT = 2;

    /** A saved location in any dimension. */
    public record Anchor(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dimension.location().toString());
            t.putDouble("x", pos.x);
            t.putDouble("y", pos.y);
            t.putDouble("z", pos.z);
            t.putFloat("yaw", yaw);
            t.putFloat("pitch", pitch);
            return t;
        }

        @Nullable
        static Anchor load(CompoundTag t) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
            if (id == null) return null;
            return new Anchor(ResourceKey.create(Registries.DIMENSION, id), new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")),
                    t.getFloat("yaw"), t.getFloat("pitch"));
        }

        public BlockPos block() {
            return BlockPos.containing(pos);
        }
    }

    // ------------------------------------------------------------------------------------- persistent
    public float mystic = 100f;
    public float cosmic = 100f;
    public float ultimate;
    public int tiers;
    public int souls;
    public final int[] selected = new int[Source.values().length];
    public Source activeSource = Source.MYSTIC;
    public final ItemStack[] artifacts = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    @Nullable public Anchor slingAnchor;
    @Nullable public Anchor spaceAnchor;
    @Nullable public Anchor realityAnchor;
    @Nullable public Anchor mirrorReturn;
    @Nullable public CompoundTag savedState;
    public boolean cloakFlight = true;
    /** Where the body waits during astral projection, and the game mode to restore. Persisted so a crash cannot strand a player. */
    @Nullable public Anchor astralReturn;
    public int astralGameMode = -1;

    // ------------------------------------------------------------------------------------- transient
    public final int[] cooldowns = new int[Ability.values().length];
    /** Shield: 0 none, 1 buckler, 2 dome. */
    public int shieldMode;
    public int shieldTicks;
    /** Entity held by whips / telekinesis, -1 for none; holdKind tells which. */
    public int heldEntity = -1;
    public int holdKind;
    public int holdTicks;
    /** A sustained beam (mind, power, soul) and how long it has left. */
    public int beamAbility = -1;
    public int beamTicks;
    /** Charge-up of the current CHARGE ability, in ticks; -1 while not charging. */
    public int charging = -1;
    public int chargeAbility = -1;
    /** Ultimate wind-ups that resolve after a delay. */
    public int windupAbility = -1;
    public int windupTicks;
    public int absorbTicks;
    public float absorbed;
    public int kineticTicks;
    public float kineticStored;
    public int superchargeHits;
    public int deflectTicks;
    public int redirectTicks;
    public int astralFormTicks;
    public int astralBody = -1;
    public int astralTicks;
    /** Cast animation: pose id + remaining ticks, mirrored to every client watching this player. */
    public int castPose;
    public int castPoseTicks;
    public int casterDirtyCooldown;
    public boolean casterDirty = true;
    public boolean dirty = true;
    public int idleSync;

    public float maxMystic = 100f;
    public float maxCosmic = 200f;

    public int cooldown(Ability a) {
        return cooldowns[a.ordinal()];
    }

    public Ability selected(Source s) {
        java.util.List<Ability> list = Ability.of(s);
        return list.get(Math.floorMod(selected[s.ordinal()], list.size()));
    }

    public void select(Ability a) {
        selected[a.source.ordinal()] = Ability.of(a.source).indexOf(a);
        activeSource = a.source;
        dirty = true;
    }

    public void pose(int pose, int ticks) {
        castPose = pose;
        castPoseTicks = ticks;
        casterDirty = true;
    }

    public void releaseHold() {
        if (heldEntity != -1) casterDirty = true;
        heldEntity = -1;
        holdKind = 0;
        holdTicks = 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putFloat("mystic", mystic);
        t.putFloat("cosmic", cosmic);
        t.putFloat("ultimate", ultimate);
        t.putInt("tiers", tiers);
        t.putInt("souls", souls);
        t.putIntArray("selected", selected);
        t.putInt("source", activeSource.ordinal());
        t.putBoolean("cloakFlight", cloakFlight);
        for (int i = 0; i < artifacts.length; i++) {
            if (!artifacts[i].isEmpty()) t.put("artifact" + i, artifacts[i].save(provider));
        }
        if (slingAnchor != null) t.put("sling", slingAnchor.save());
        if (spaceAnchor != null) t.put("space", spaceAnchor.save());
        if (realityAnchor != null) t.put("reality", realityAnchor.save());
        if (mirrorReturn != null) t.put("mirrorReturn", mirrorReturn.save());
        if (savedState != null) t.put("savedState", savedState);
        if (astralReturn != null) t.put("astralReturn", astralReturn.save());
        t.putInt("astralGameMode", astralGameMode);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        mystic = t.getFloat("mystic");
        cosmic = t.getFloat("cosmic");
        ultimate = t.getFloat("ultimate");
        tiers = t.getInt("tiers");
        souls = t.getInt("souls");
        int[] sel = t.getIntArray("selected");
        System.arraycopy(sel, 0, selected, 0, Math.min(sel.length, selected.length));
        activeSource = Source.byId(t.getInt("source"));
        cloakFlight = !t.contains("cloakFlight") || t.getBoolean("cloakFlight");
        for (int i = 0; i < artifacts.length; i++) {
            artifacts[i] = t.contains("artifact" + i, Tag.TAG_COMPOUND)
                    ? ItemStack.parse(provider, t.getCompound("artifact" + i)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        }
        slingAnchor = t.contains("sling") ? Anchor.load(t.getCompound("sling")) : null;
        spaceAnchor = t.contains("space") ? Anchor.load(t.getCompound("space")) : null;
        realityAnchor = t.contains("reality") ? Anchor.load(t.getCompound("reality")) : null;
        mirrorReturn = t.contains("mirrorReturn") ? Anchor.load(t.getCompound("mirrorReturn")) : null;
        savedState = t.contains("savedState") ? t.getCompound("savedState") : null;
        astralReturn = t.contains("astralReturn") ? Anchor.load(t.getCompound("astralReturn")) : null;
        astralGameMode = t.contains("astralGameMode") ? t.getInt("astralGameMode") : -1;
        dirty = true;
        casterDirty = true;
    }
}

package com.wandcraft.entity;

import com.wandcraft.WandCraft;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A timed area effect (storm, rune, vortex...). Not saved with the world; always cleans itself up. */
public class SpellZone extends Entity {
    private static final EntityDataAccessor<String> SPELL = SynchedEntityData.defineId(SpellZone.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpellZone.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<java.util.Optional<UUID>> OWNER = SynchedEntityData.defineId(SpellZone.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> MAX_AGE = SynchedEntityData.defineId(SpellZone.class, EntityDataSerializers.INT);

    public UUID ownerId;
    public float power = 1;
    public float charge;
    public int age;
    /** Free-form counters for behaviours. */
    public final int[] data = new int[4];
    public Vec3 vec = Vec3.ZERO;
    public boolean followOwner;
    public Object extra;

    public SpellZone(EntityType<? extends SpellZone> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public SpellZone(Level level, Spell spell, ServerPlayer owner, Vec3 pos, float radius, int duration) {
        this(WandCraft.SPELL_ZONE.get(), level);
        entityData.set(SPELL, spell.id);
        entityData.set(RADIUS, radius);
        entityData.set(MAX_AGE, duration);
        if (owner != null) {
            ownerId = owner.getUUID();
            entityData.set(OWNER, java.util.Optional.of(ownerId));
        }
        setPos(pos);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SPELL, "");
        b.define(RADIUS, 3f);
        b.define(MAX_AGE, 100);
        b.define(OWNER, java.util.Optional.empty());
    }

    public Spell spell() { return Spells.get(entityData.get(SPELL)); }
    public float radius() { return entityData.get(RADIUS); }
    public void setRadius(float r) { entityData.set(RADIUS, r); }
    public int maxAge() { return entityData.get(MAX_AGE); }
    public void setMaxAge(int a) { entityData.set(MAX_AGE, a); }
    public int remaining() { return maxAge() - age; }

    /** Owner as seen from either side. On the client only the synced id is available, so effects must use this. */
    public net.minecraft.world.entity.player.Player clientOwner() {
        UUID id = ownerId != null ? ownerId : entityData.get(OWNER).orElse(null);
        return id == null ? null : level().getPlayerByUUID(id);
    }

    public ServerPlayer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(ownerId);
    }

    public void credit(int hits) {
        ServerPlayer o = owner();
        Spell s = spell();
        if (o != null && s != null) Magic.credit(o, s, hits);
    }

    @Override
    public void tick() {
        super.tick();
        Spell s = spell();
        if (s == null || s.zone == null) {
            discard();
            return;
        }
        if (level().isClientSide) {
            s.zone.fx(this);
        } else {
            ServerPlayer o = owner();
            if (followOwner) {
                if (o == null || !o.isAlive()) {
                    discard();
                    return;
                }
                setPos(o.getX(), o.getY(), o.getZ());
            }
            if (age == 0) s.zone.start(this);
            if (isRemoved()) return;
            s.zone.tick(this);
            if (age >= maxAge()) {
                s.zone.end(this);
                discard();
                return;
            }
        }
        age++;
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean isPickable() { return false; }
}

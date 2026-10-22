package com.stickyfingers.entity;

import com.stickyfingers.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * A dimensional portal created by D4C. Players and clones can pass through it.
 * Linked portals teleport entities to each other.
 */
public class D4CPortalEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(D4CPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINKED_ID = SynchedEntityData.defineId(D4CPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICKS = SynchedEntityData.defineId(D4CPortalEntity.class, EntityDataSerializers.INT);

    private int lifeTicks;
    private final Set<UUID> cooldown = new HashSet<>();

    public D4CPortalEntity(EntityType<? extends D4CPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static D4CPortalEntity create(Level level, Player owner, Vec3 pos, int lifeTicks) {
        D4CPortalEntity portal = new D4CPortalEntity(ModEntities.D4C_PORTAL.get(), level);
        portal.moveTo(pos.x, pos.y, pos.z);
        portal.entityData.set(OWNER_ID, owner.getId());
        portal.lifeTicks = lifeTicks;
        level.addFreshEntity(portal);
        return portal;
    }

    public void linkTo(D4CPortalEntity other) {
        this.entityData.set(LINKED_ID, other.getId());
        other.entityData.set(LINKED_ID, this.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER_ID, -1);
        b.define(LINKED_ID, -1);
        b.define(LIFE_TICKS, 300);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            lifeTicks--;
            if (lifeTicks <= 0) { discard(); return; }

            // Check for nearby entities to teleport
            int linkedId = entityData.get(LINKED_ID);
            if (linkedId < 0) return;
            Entity linked = level().getEntity(linkedId);
            if (!(linked instanceof D4CPortalEntity destination) || destination.isRemoved()) return;

            var nearby = level().getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(1.5), e -> !cooldown.contains(e.getUUID()));
            for (Player p : nearby) {
                if (p instanceof ServerPlayer sp) {
                    Vec3 dest = destination.position().add(0, 0.1, 0);
                    sp.teleportTo(dest.x, dest.y, dest.z);
                    cooldown.add(sp.getUUID());
                    destination.cooldown.add(sp.getUUID());
                }
            }

            // Clear cooldown after 40 ticks
            if (tickCount % 40 == 0) cooldown.clear();
        }
    }

    public int ownerEntityId() { return entityData.get(OWNER_ID); }
    public int linkedEntityId() { return entityData.get(LINKED_ID); }
    public int remainingTicks() { return lifeTicks; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { lifeTicks = tag.getInt("Life"); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Life", lifeTicks); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double dist) { return dist < 4096; }
}

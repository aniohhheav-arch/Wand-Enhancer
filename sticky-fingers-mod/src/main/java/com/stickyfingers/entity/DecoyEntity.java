package com.stickyfingers.entity;

import com.stickyfingers.registry.ModEntities;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** A zipper-stitched copy of its owner that draws aggro and bursts into zipper teeth when struck. */
public class DecoyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private int life = 200;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, Level level) {
        super(type, level);
        setNoAi(false);
    }

    public static DecoyEntity create(ServerPlayer owner, int life) {
        DecoyEntity d = new DecoyEntity(ModEntities.DECOY.get(), owner.level());
        d.entityData.set(OWNER, Optional.of(owner.getUUID()));
        d.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        d.setYHeadRot(owner.getYHeadRot());
        d.yBodyRot = owner.yBodyRot;
        d.life = life;
        d.setCustomName(owner.getDisplayName());
        d.setCustomNameVisible(false);
        return d;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(OWNER, Optional.empty());
    }

    public Optional<UUID> ownerId() { return entityData.get(OWNER); }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            if (--life <= 0) { pop(sl); return; }
            if (tickCount % 10 == 0) {
                // pull aggro of nearby mobs that were hunting our owner
                ownerId().ifPresent(id -> sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(20), m ->
                        m.getTarget() != null && m.getTarget().getUUID().equals(id)).forEach(m -> m.setTarget(this)));
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel sl && !isRemoved()) pop(sl);
        return false;
    }

    private void pop(ServerLevel sl) {
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.DECOY_POP.get(), SoundSource.PLAYERS, 1f, 1f);
        sl.sendParticles(ModParticles.ZIPPER_TOOTH.get(), getX(), getY(0.5), getZ(), 30, 0.3, 0.6, 0.3, 0.2);
        sl.sendParticles(ModParticles.AFTERIMAGE.get(), getX(), getY(0.5), getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        sl.sendParticles(ModParticles.SEAM_GLOW.get(), getX(), getY(0.5), getZ(), 20, 0.1, 0.9, 0.1, 0.0);
        discard();
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void registerGoals() {}
}

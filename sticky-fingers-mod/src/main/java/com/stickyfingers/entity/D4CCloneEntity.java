package com.stickyfingers.entity;

import com.stickyfingers.registry.ModEntities;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * D4C alternate-self clone. Follows and fights for its owner; limited lifespan.
 */
public class D4CCloneEntity extends Monster {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(D4CCloneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(D4CCloneEntity.class, EntityDataSerializers.INT);

    private int lifeTicks;

    public D4CCloneEntity(EntityType<? extends D4CCloneEntity> type, Level level) {
        super(type, level);
    }

    public static D4CCloneEntity spawn(net.minecraft.server.level.ServerLevel level, Player owner, int lifeTicks) {
        D4CCloneEntity clone = new D4CCloneEntity(ModEntities.D4C_CLONE.get(), level);
        clone.moveTo(owner.getX() + 1.5, owner.getY(), owner.getZ(), owner.getYRot(), 0);
        clone.entityData.set(OWNER_ID, owner.getId());
        clone.lifeTicks = lifeTicks;
        clone.entityData.set(LIFE, lifeTicks);
        level.addFreshEntity(clone);
        return clone;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new FollowMobGoal(this, 1.0, 4, 12));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, true, e ->
                e != this && !(e instanceof Player) && !(e instanceof D4CCloneEntity)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(OWNER_ID, -1);
        b.define(LIFE, 600);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (lifeTicks-- <= 0) discard();
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override public boolean isPickable() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Life", lifeTicks); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); lifeTicks = tag.getInt("Life"); }
}

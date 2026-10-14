package com.stickyfingers.entity;

import com.stickyfingers.registry.ModEntities;
import com.stickyfingers.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * The Sticky Fingers Stand. Floats behind its user's shoulder, lunges forward for attacks
 * and plays server-selected GeckoLib animations.
 */
public class StandEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_SEQ = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int animTicks;
    private int lastSeenSeq = -1;
    @Nullable private Vec3 strikePos;
    private int strikeTicks;
    private int dismissTimer = -1;
    private int age;

    public StandEntity(EntityType<? extends StandEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static StandEntity create(ServerPlayer owner) {
        StandEntity s = new StandEntity(ModEntities.STAND.get(), owner.level());
        s.entityData.set(OWNER, owner.getId());
        Vec3 p = s.restPosition(owner);
        s.moveTo(p.x, p.y, p.z, owner.getYRot(), 0);
        s.play(StandAnim.SUMMON);
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER, -1);
        b.define(ANIM, StandAnim.IDLE.ordinal());
        b.define(ANIM_SEQ, 0);
    }

    @Nullable
    public Player owner() {
        Entity e = level().getEntity(entityData.get(OWNER));
        return e instanceof Player p ? p : null;
    }

    public StandAnim anim() {
        int i = entityData.get(ANIM);
        return i >= 0 && i < StandAnim.VALUES.length ? StandAnim.VALUES[i] : StandAnim.IDLE;
    }

    public void play(StandAnim anim) {
        entityData.set(ANIM, anim.ordinal());
        entityData.set(ANIM_SEQ, entityData.get(ANIM_SEQ) + 1);
        animTicks = anim.length;
    }

    /** Move the Stand to a point (e.g. in front of a target) for a number of ticks. */
    public void strikeAt(Vec3 pos, int ticks) {
        strikePos = pos;
        strikeTicks = ticks;
    }

    public void dismiss() {
        if (dismissTimer < 0) {
            play(StandAnim.DISMISS);
            dismissTimer = 12;
        }
    }

    public boolean isDismissing() { return dismissTimer >= 0; }
    public int age() { return age; }

    private Vec3 restPosition(Player owner) {
        float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(0.6);
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.85);
        return owner.position().add(back).add(right).add(0, 0.35 + Math.sin(age * 0.08) * 0.06, 0);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Player owner = owner();
        if (level().isClientSide) {
            if (random.nextInt(4) == 0) {
                level().addParticle(ModParticles.VOID_MOTE.get(), getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.01, 0);
            }
            return;
        }
        if (owner == null || !owner.isAlive()) { discard(); return; }
        if (dismissTimer >= 0 && --dismissTimer <= 0) { discard(); return; }
        if (animTicks > 0 && --animTicks == 0 && anim() != StandAnim.DISMISS) {
            entityData.set(ANIM, StandAnim.IDLE.ordinal());
        }

        Vec3 target;
        float yaw;
        if (strikePos != null && strikeTicks > 0) {
            strikeTicks--;
            target = strikePos;
            Vec3 face = strikePos.subtract(owner.position());
            yaw = face.horizontalDistanceSqr() > 1e-3 ? (float) Math.toDegrees(Math.atan2(-face.x, face.z)) : owner.getYRot();
        } else {
            strikePos = null;
            target = restPosition(owner);
            yaw = owner.getYRot();
        }
        Vec3 next = position().lerp(target, strikePos != null ? 0.6 : 0.35);
        if (position().distanceToSqr(target) > 64) next = target;
        setPos(next.x, next.y, next.z);
        setYRot(Mth.rotLerp(0.5f, getYRot(), yaw));
        setXRot(owner.getXRot() * 0.4f);
    }

    // GeckoLib -------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> {
            int seq = entityData.get(ANIM_SEQ);
            if (seq != lastSeenSeq) {
                lastSeenSeq = seq;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(anim().raw);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}

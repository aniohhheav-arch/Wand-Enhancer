package com.wandcraft.entity;

import com.wandcraft.WandCraft;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A short-lived summoned fighter (Shadow Clone, Mirror Image decoy, Spectral Legion). It is owned by a player, never
 * attacks its owner or allies, cannot cast, and removes itself when its time runs out.
 */
public class ShadowClone extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ShadowClone.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(ShadowClone.class, EntityDataSerializers.INT);

    public static final int STYLE_SHADOW = 0, STYLE_MIRROR = 1, STYLE_SPECTRAL = 2;

    public int life = 200;
    public float damage = 3f;
    /** Decoys draw attention instead of fighting. */
    public boolean decoy;

    public ShadowClone(EntityType<? extends ShadowClone> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(OWNER, Optional.empty());
        b.define(STYLE, 0);
    }

    public void setOwner(Player p) { entityData.set(OWNER, Optional.of(p.getUUID())); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public int style() { return entityData.get(STYLE); }
    public void setStyle(int s) { entityData.set(STYLE, s); }

    public Player owner() {
        UUID id = ownerId();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true) {
            @Override public boolean canUse() { return !decoy && super.canUse(); }
        });
        goalSelector.addGoal(5, new FollowOwner());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerTargetGoal());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int rgb = style() == STYLE_MIRROR ? 0xD27BFF : style() == STYLE_SPECTRAL ? 0x9FE4FF : 0x7A2BD6;
            if (tickCount % 2 == 0) {
                Fx.c(level(), Fx.wisp(style() == STYLE_SHADOW ? 0x120820 : rgb, 0.9f), getX() + (random.nextDouble() - .5) * .6, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - .5) * .6, 0, 0.02, 0);
            }
            if (tickCount % 4 == 0) Fx.c(level(), Fx.glow(rgb, 0.6f), getX() + (random.nextDouble() - .5) * .5, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - .5) * .5, 0, 0.01, 0);
            return;
        }
        if (--life <= 0 || owner() == null && tickCount > 40) {
            fade();
            return;
        }
    }

    public void fade() {
        if (level() instanceof ServerLevel sl) {
            Fx.burst(sl, Fx.wisp(0x120820, 1.3f), position().add(0, 1, 0), 18, 0.4, 0.05);
            Fx.burst(sl, Fx.spark(0x9A5BFF, 0.8f), position().add(0, 1, 0), 10, 0.4, 0.1);
        }
        discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = target instanceof LivingEntity le && Combat.hurt((ServerLevel) level(), owner(), this, le, damage);
        if (ok) swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return ok;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        Player o = owner();
        return other == o || (o != null && o.isAlliedTo(other)) || super.isAlliedTo(other)
                || (other instanceof ShadowClone c && c.ownerId() != null && c.ownerId().equals(ownerId()));
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() == owner() && owner() != null) return false;
        return super.hurt(src, amount);
    }

    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return true; }
    @Override protected void dropCustomDeathLoot(ServerLevel l, DamageSource s, boolean b) {}
    @Override public boolean canBeLeashed() { return false; }
    @Override public void readAdditionalSaveData(CompoundTag t) {}
    @Override public void addAdditionalSaveData(CompoundTag t) {}

    /** Stay near the owner when there is nothing to fight. */
    private class FollowOwner extends Goal {
        FollowOwner() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            Player o = owner();
            return o != null && getTarget() == null && distanceToSqr(o) > 16;
        }
        @Override public void start() {
            Player o = owner();
            if (o != null) getNavigation().moveTo(o, 1.2);
        }
        @Override public boolean canContinueToUse() { return getTarget() == null && !getNavigation().isDone(); }
    }

    /** Attack whatever the owner is fighting, or the nearest hostile mob. */
    private class OwnerTargetGoal extends TargetGoal {
        OwnerTargetGoal() { super(ShadowClone.this, false); setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() {
            Player o = owner();
            if (o == null) return false;
            LivingEntity t = o.getLastHurtMob();
            if (t == null || !t.isAlive() || o.tickCount - o.getLastHurtMobTimestamp() > 120) t = o.getLastHurtByMob();
            if (t == null || !t.isAlive() || !Combat.canTarget(o, t)) {
                t = null;
                double best = 14 * 14;
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14), e -> Combat.isHostile(o, e) && e != ShadowClone.this)) {
                    double d = e.distanceToSqr(ShadowClone.this);
                    if (d < best) { best = d; t = e; }
                }
            }
            if (t == null) return false;
            targetMob = t;
            return true;
        }
        @Override public void start() { mob.setTarget(targetMob); super.start(); }
    }
}

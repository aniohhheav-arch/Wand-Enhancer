package dev.riftverse.entity.creature;

import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Time and Space Authority field agent. Arrives in a ring of blue light, pursues its assigned offender by skipping
 * through time (short blinks towards them), and its baton shackles the target's timeline (slowness + weakness).
 * Rank 1 is a Temporal Enforcer: tougher, faster blinks. Agents leave on their own once their warrant expires.
 */
public class TsaAgentEntity extends Monster {
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(TsaAgentEntity.class, EntityDataSerializers.INT);
    private int warrant = 20 * 120;

    public TsaAgentEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.ARMOR, 8);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(RANK, 0);
    }

    public int rank() {
        return entityData.get(RANK);
    }

    public void setRank(int r) {
        entityData.set(RANK, r);
        if (r > 0) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9);
            setHealth(80);
        }
    }

    public void setWarrant(int ticks) {
        warrant = ticks;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(4) == 0) level().addParticle(RvParticles.MOTE.get().with(rank() > 0 ? 0xFF4050 : 0x60A0FF, 0.5f, 20),
                    getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            return;
        }
        if (--warrant <= 0) {
            vanish();
            return;
        }
        LivingEntity t = getTarget();
        int every = rank() > 0 ? 50 : 80;
        if (t != null && tickCount % every == 0 && distanceToSqr(t) > 64 && hasLineOfSight(t)) {
            Vec3 to = t.position().subtract(position());
            Vec3 at = position().add(to.scale(0.7));
            flash();
            teleportTo(at.x, Math.max(at.y, t.getY()), at.z);
            flash();
        }
    }

    private void flash() {
        if (level() instanceof ServerLevel s) {
            s.sendParticles(RvParticles.RING.get().with(rank() > 0 ? 0xFF4050 : 0x60A0FF, 1.8f, 12), getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            s.sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 0.8f, 10), getX(), getY() + 1, getZ(), 14, 0.3, 0.8, 0.3, 0.15);
            s.playSound(null, blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.HOSTILE, 0.6f, 2f);
        }
    }

    public void vanish() {
        flash();
        discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, rank() > 0 ? 2 : 1));
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
        return hit;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("rank", rank());
        t.putInt("warrant", warrant);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(RANK, t.getInt("rank"));
        warrant = t.contains("warrant") ? t.getInt("warrant") : 20 * 120;
    }
}

package dev.riftverse.entity.creature;

import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** A towering construct of living crystal. Shrugs off arrows and shatters the ground with its fists. */
public class CrystalSentinelEntity extends Monster {
    private static final EntityDataAccessor<Integer> SLAM = SynchedEntityData.defineId(CrystalSentinelEntity.class, EntityDataSerializers.INT);
    private int slamCooldown = 60;

    public CrystalSentinelEntity(EntityType<? extends CrystalSentinelEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLAM, 0);
    }

    /** Remaining ticks of the slam wind-up animation (client renders the raised fists). */
    public int slamTicks() {
        return entityData.get(SLAM);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        int slam = slamTicks();
        if (slam > 0) {
            entityData.set(SLAM, slam - 1);
            getNavigation().stop();
            if (slam - 1 == 0) slam();
            return;
        }
        LivingEntity target = getTarget();
        if (--slamCooldown <= 0 && target != null && distanceToSqr(target) < 30 && onGround()) {
            entityData.set(SLAM, 18);
            slamCooldown = 100 + random.nextInt(60);
            playSound(RvSounds.WARDEN_CHARGE.get(), 1.0f, 0.6f);
        }
    }

    private void slam() {
        ServerLevel level = (ServerLevel) level();
        Vec3 c = position();
        level.sendParticles(RvParticles.RING.get().with(0x7DF9FF, 6f, 14), c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.SPARK.get().with(0xD69CFF, 0.6f, 20), c.x, c.y + 0.2, c.z, 50, 2.5, 0.2, 2.5, 0.25);
        level.playSound(null, c.x, c.y, c.z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.HOSTILE, 1.4f, 0.6f);
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(5, 2, 5), e -> e instanceof LivingEntity && e != this)) {
            e.hurt(damageSources().mobAttack(this), 8f);
            e.setDeltaMovement(e.getDeltaMovement().add(e.position().subtract(c).normalize().scale(0.8)).add(0, 0.7, 0));
            e.hurtMarked = true;
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) < 24 * 24) PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.5f, 12, 0f, 0));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) amount *= 0.3f;
        return super.hurt(source, amount);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(RvSounds.SENTINEL_STEP.get(), 0.6f, 0.8f + random.nextFloat() * 0.2f);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.SENTINEL_STEP.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.SINGULARITY_IMPLODE.get();
    }
}

package dev.riftverse.entity.creature;

import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.entity.ai.Flight;
import dev.riftverse.entity.ai.OrbitAndFireGoal;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Echo of a traveller lost between realities. Flickers out of phase and hurls rift-bolts. */
public class RiftWraithEntity extends Monster {
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(RiftWraithEntity.class, EntityDataSerializers.BOOLEAN);
    private int phasedTicks;

    public RiftWraithEntity(EntityType<? extends RiftWraithEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 15, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING, false);
    }

    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new OrbitAndFireGoal(this, 9, 4.5, 0.9, 50, 20, RiftWraithEntity::fire,
                (m, c) -> ((RiftWraithEntity) m).entityData.set(CHARGING, c)));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.7));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private static void fire(Mob mob, LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            EnergyBoltEntity bolt = EnergyBoltEntity.create(mob.level(), mob, 0xFF2BD6, 0.45f, 5f);
            Vec3 to = target.getEyePosition().subtract(bolt.position());
            Vec3 side = to.cross(new Vec3(0, 1, 0)).normalize().scale(i * 0.12 * to.length());
            Vec3 aim = to.add(side);
            bolt.shoot(aim.x, aim.y, aim.z, 1.1f, 0.5f);
            mob.level().addFreshEntity(bolt);
        }
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), RvSounds.WRAITH_SCREAM.get(), SoundSource.HOSTILE, 0.7f, 1.3f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phasedTicks > 0) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && random.nextFloat() < 0.3f) {
            phasedTicks = 30;
            if (level() instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(RvParticles.STREAK.get().with(0xFF2BD6, 0.4f, 10), getX(), getY() + 1, getZ(), 12, 0.4, 0.6, 0.4, 0.2);
            }
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (phasedTicks > 0) phasedTicks--;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return Flight.navigation(this, level);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RvSounds.WRAITH_SCREAM.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 220;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.CREATURE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.WRAITH_SCREAM.get();
    }
}

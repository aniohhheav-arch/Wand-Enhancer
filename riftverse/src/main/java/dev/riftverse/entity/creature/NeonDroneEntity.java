package dev.riftverse.entity.creature;

import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.entity.ai.Flight;
import dev.riftverse.entity.ai.OrbitAndFireGoal;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Hovering security drone: circles its prey and charges a neon laser before each shot. */
public class NeonDroneEntity extends Monster {
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(NeonDroneEntity.class, EntityDataSerializers.BOOLEAN);

    public NeonDroneEntity(EntityType<? extends NeonDroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.FLYING_SPEED, 0.55)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
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
        goalSelector.addGoal(2, new OrbitAndFireGoal(this, 7, 3.5, 1.0, 40, 15, NeonDroneEntity::fire,
                (m, c) -> ((NeonDroneEntity) m).entityData.set(CHARGING, c)));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private static void fire(Mob mob, LivingEntity target) {
        int color = mob.getRandom().nextBoolean() ? 0x00F0FF : 0xFF2BD6;
        EnergyBoltEntity bolt = EnergyBoltEntity.create(mob.level(), mob, color, 0.35f, 4f);
        Vec3 to = target.getEyePosition().subtract(bolt.position());
        bolt.shoot(to.x, to.y, to.z, 1.6f, 1.5f);
        mob.level().addFreshEntity(bolt);
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), RvSounds.ENERGY_FIRE.get(), SoundSource.HOSTILE, 0.8f, 1.4f);
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
        return RvSounds.DRONE_HUM.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.ENERGY_HIT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.GLITCH_NOISE.get();
    }
}

package dev.riftverse.entity.creature;

import dev.riftverse.entity.ai.CruiseGoal;
import dev.riftverse.entity.ai.Flight;
import dev.riftverse.registry.RvSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Colossal, gentle sky-whales whose songs carry for hundreds of blocks. */
public class SkyWhaleEntity extends PathfinderMob {
    public SkyWhaleEntity(EntityType<? extends SkyWhaleEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 3, true);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.FLYING_SPEED, 0.12)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new CruiseGoal(this, 0.55, 90, 22));
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
        return RvSounds.WHALE_CALL.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.WHALE_CALL.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.CREATURE_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4.0f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 420;
    }

    @Override
    public float getVoicePitch() {
        return 0.8f + random.nextFloat() * 0.3f;
    }
}

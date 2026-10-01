package dev.riftverse.entity.creature;

import dev.riftverse.entity.ai.CruiseGoal;
import dev.riftverse.entity.ai.Flight;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Drifting, bioluminescent sky-jellyfish. Their bells chime as they pulse through the air. */
public class AstralJellyEntity extends PathfinderMob {
    public AstralJellyEntity(EntityType<? extends AstralJellyEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 6, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.FLYING_SPEED, 0.18)
                .add(Attributes.MOVEMENT_SPEED, 0.12);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        goalSelector.addGoal(3, new CruiseGoal(this, 0.7, 28, 6));
        goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return Flight.navigation(this, level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(4) == 0) {
            float pulse = (float) Math.sin(tickCount * 0.12);
            level().addParticle(RvParticles.MOTE.get().with(pulse > 0 ? 0xFF7AF0 : 0x7DF9FF, 0.2f * getScale(), 40),
                    getX() + (random.nextDouble() - 0.5) * getBbWidth(), getY() + random.nextDouble() * 0.6, getZ() + (random.nextDouble() - 0.5) * getBbWidth(),
                    0, -0.02, 0);
        }
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
        return RvSounds.JELLY_CHIME.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.CREATURE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.CREATURE_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 180;
    }
}

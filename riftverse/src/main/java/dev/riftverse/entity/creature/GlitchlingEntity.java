package dev.riftverse.entity.creature;

import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.phys.Vec3;

/** A shard of corrupted reality wearing a body. It stutters through space in jagged blinks. */
public class GlitchlingEntity extends Monster {
    private int blinkCooldown = 60;

    public GlitchlingEntity(EntityType<? extends GlitchlingEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (--blinkCooldown <= 0 && target != null && distanceToSqr(target) > 16) {
            Vec3 around = target.position().add((random.nextDouble() - 0.5) * 5, 0, (random.nextDouble() - 0.5) * 5);
            blink(around);
            blinkCooldown = 60 + random.nextInt(60);
        }
    }

    public boolean blink(Vec3 near) {
        ServerLevel level = (ServerLevel) level();
        for (int i = 0; i < 10; i++) {
            BlockPos p = BlockPos.containing(near.x + (random.nextDouble() - 0.5) * 3, near.y + random.nextInt(5) - 2, near.z + (random.nextDouble() - 0.5) * 3);
            if (!level.getBlockState(p.below()).isSolid()) continue;
            Vec3 old = position();
            if (randomTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, false)) {
                level.sendParticles(RvParticles.GLITCH.get().with(0x39FF14, 0.4f, 12), old.x, old.y + 1, old.z, 18, 0.3, 0.6, 0.3, 0.05);
                level.sendParticles(RvParticles.GLITCH.get().with(0xFF0055, 0.4f, 12), getX(), getY() + 1, getZ(), 18, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, getX(), getY(), getZ(), RvSounds.GLITCH_NOISE.get(), SoundSource.HOSTILE, 0.8f, 0.7f + random.nextFloat() * 0.6f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && random.nextFloat() < 0.45f) {
            blink(position().add((random.nextDouble() - 0.5) * 12, 0, (random.nextDouble() - 0.5) * 12));
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RvSounds.GLITCH_NOISE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RvSounds.GLITCH_NOISE.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RvSounds.CREATURE_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }
}

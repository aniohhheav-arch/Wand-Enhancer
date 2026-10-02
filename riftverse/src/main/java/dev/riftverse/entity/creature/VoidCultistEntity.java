package dev.riftverse.entity.creature;

import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A robed worshipper of the void. Fights with its hands and blinks through space to reach whoever it hunts. */
public class VoidCultistEntity extends Monster {
    private int blinkCooldown = 60;

    public VoidCultistEntity(EntityType<? extends VoidCultistEntity> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 8 == 0) level.sendParticles(RvParticles.DUST.get().with(0x6A2AC8, 0.6f, 30), getX(), getY() + 1, getZ(), 1, 0.3, 0.6, 0.3, 0.01);
        LivingEntity target = getTarget();
        if (--blinkCooldown > 0 || target == null) return;
        if (distanceToSqr(target) > 36) {
            Vec3 from = position();
            Vec3 dir = from.subtract(target.position()).normalize().scale(2.5);
            if (randomTeleport(target.getX() + dir.x, target.getY(), target.getZ() + dir.z, true)) {
                level.sendParticles(RvParticles.GLITCH.get().with(0x9B30FF, 0.6f, 16), from.x, from.y + 1, from.z, 25, 0.3, 0.8, 0.3, 0.05);
                level.playSound(null, getX(), getY(), getZ(), RvSounds.STALKER_HISS.get(), SoundSource.HOSTILE, 1f, 1.4f);
            }
        }
        blinkCooldown = 80 + random.nextInt(60);
    }
}

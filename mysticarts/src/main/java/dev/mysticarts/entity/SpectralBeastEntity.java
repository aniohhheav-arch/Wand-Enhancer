package dev.mysticarts.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** A spectral wolf-lion bound by the Soul Stone. Fast, fierce, and gone when its bond expires. */
public class SpectralBeastEntity extends SummonEntity {
    public SpectralBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected int glowColor() {
        return 0xFF7A12;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }
}

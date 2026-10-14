package com.stickyfingers.effect;

import com.stickyfingers.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared implementation for all zipper status effects. Freezing effects also cancel horizontal motion. */
public class ZipperEffect extends MobEffect {
    private final boolean freezes;

    public ZipperEffect(MobEffectCategory category, int color, boolean freezes) {
        super(category, color);
        this.freezes = freezes;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (freezes) {
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
            entity.hurtMarked = true;
            if (entity.level() instanceof ServerLevel level && entity.tickCount % 6 == 0) {
                level.sendParticles(ModParticles.SEAM_GLOW.get(), entity.getX(), entity.getY(0.5), entity.getZ(),
                        2, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.0);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}

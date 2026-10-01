package com.infinitemultiverse.abilities.core;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Local time dilation: heavily slows hostile entities and strips most of the velocity from incoming projectiles
 * that the player does not own. The foundation for later temporal abilities (time stop, rewind).
 */
public final class TemporalDragAbility extends Ability {
    private static final int SLOW_AMPLIFIER = 3;
    private static final int FATIGUE_AMPLIFIER = 1;
    private static final double PROJECTILE_DAMPING = 0.15;

    public TemporalDragAbility() {
        super(MultiverseSystem.CORE, ActivationType.INSTANT, true);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.temporalDrag.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.temporalDrag.cooldown();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        double radius = MultiverseConfig.SERVER.temporalDragRadius.get();
        int duration = MultiverseConfig.SERVER.temporalDragDuration.get();

        for (LivingEntity target : AbilityTargeting.hostilesInRadius(player, radius)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, SLOW_AMPLIFIER), player);
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, FATIGUE_AMPLIFIER), player);
        }

        double radiusSqr = radius * radius;
        for (Projectile projectile : ctx.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius),
                p -> p.getOwner() != player && p.distanceToSqr(player) <= radiusSqr)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(PROJECTILE_DAMPING));
            projectile.hurtMarked = true;
        }

        Vec3 center = player.position().add(0.0, 0.1, 0.0);
        MultiverseVfx.sound(ctx.level(), center, ModSounds.TEMPORAL_DRAG, 1.0f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.TEMPORAL_DRAG, center, Vec3.ZERO, (float) radius);
        return true;
    }
}

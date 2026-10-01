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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Radial blast: distance-scaled damage, outward knockback and lift. Never touches blocks. */
public final class ShockwaveAbility extends Ability {
    private static final double EDGE_FALLOFF = 0.6;
    private static final double LIFT = 0.35;

    public ShockwaveAbility() {
        super(MultiverseSystem.CORE, ActivationType.INSTANT, true);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.shockwave.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.shockwave.cooldown();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        double radius = MultiverseConfig.SERVER.shockwaveRadius.get();
        double damage = MultiverseConfig.SERVER.shockwaveDamage.get();
        double knockback = MultiverseConfig.SERVER.shockwaveKnockback.get();

        for (LivingEntity target : AbilityTargeting.hostilesInRadius(player, radius)) {
            double falloff = 1.0 - Math.min(1.0, target.distanceTo(player) / radius) * EDGE_FALLOFF;
            if (damage > 0.0) {
                target.hurt(player.damageSources().playerAttack(player), (float) (damage * falloff));
            }
            double dx = player.getX() - target.getX();
            double dz = player.getZ() - target.getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                dx = player.getRandom().nextDouble() - 0.5;
                dz = player.getRandom().nextDouble() - 0.5;
            }
            target.knockback(knockback * falloff, dx, dz);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, LIFT * falloff, 0.0));
            target.hurtMarked = true;
        }

        Vec3 center = player.position().add(0.0, 0.15, 0.0);
        MultiverseVfx.sound(ctx.level(), center, ModSounds.SHOCKWAVE, 1.2f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.SHOCKWAVE, center, Vec3.ZERO, (float) radius);
        return true;
    }
}

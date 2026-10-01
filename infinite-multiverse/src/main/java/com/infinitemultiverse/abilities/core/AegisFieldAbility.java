package com.infinitemultiverse.abilities.core;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.ability.OwnerDamageInterceptor;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Sustained energy barrier: absorbs a share of incoming damage, paying energy per point absorbed on top of its
 * upkeep. Collapses when the pool runs dry. Damage that bypasses invulnerability (void, /kill) is never absorbed.
 */
public final class AegisFieldAbility extends Ability implements OwnerDamageInterceptor {
    private static final int PULSE_INTERVAL = 8;
    private static final float FIELD_RADIUS = 1.25f;

    public AegisFieldAbility() {
        super(MultiverseSystem.CORE, ActivationType.TOGGLE, Items.SHIELD, true);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.aegisField.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.aegisField.cooldown();
    }

    @Override
    public float upkeepPerSecond() {
        return MultiverseConfig.SERVER.aegisUpkeep.get().floatValue();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        MultiverseVfx.sound(ctx.level(), ctx.player().position(), ModSounds.AEGIS_ACTIVATE, 0.9f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.AEGIS_PULSE, center(ctx.player()), Vec3.ZERO, FIELD_RADIUS);
        return true;
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        if (activeTicks % PULSE_INTERVAL == 0) {
            MultiverseVfx.broadcast(ctx.level(), VfxIds.AEGIS_PULSE, center(ctx.player()), Vec3.ZERO, FIELD_RADIUS);
        }
    }

    @Override
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
        if (reason == DeactivationReason.LOGGED_OUT) {
            return;
        }
        MultiverseVfx.sound(ctx.level(), ctx.player().position(), ModSounds.AEGIS_DEACTIVATE, 0.9f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.AEGIS_COLLAPSE, center(ctx.player()), Vec3.ZERO, FIELD_RADIUS);
    }

    @Override
    public void onOwnerIncomingDamage(AbilityContext ctx, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getAmount() <= 0f) {
            return;
        }
        ServerPlayer player = ctx.player();
        float absorbed = event.getAmount() * MultiverseConfig.SERVER.aegisDamageReduction.get().floatValue();
        if (absorbed <= 0f) {
            return;
        }
        float cost = absorbed * MultiverseConfig.SERVER.aegisEnergyPerDamage.get().floatValue();
        if (!AbilityManager.isEnergyFree(player) && !ctx.data().drain(cost)) {
            AbilityManager.deactivate(player, ctx.data(), this, DeactivationReason.ENERGY_DEPLETED);
            return;
        }
        event.setAmount(event.getAmount() - absorbed);

        Vec3 center = center(player);
        Vec3 sourcePos = event.getSource().getSourcePosition();
        Vec3 direction = sourcePos == null ? player.getLookAngle() : sourcePos.subtract(center);
        if (direction.lengthSqr() < 1.0E-6) {
            direction = player.getLookAngle();
        }
        direction = direction.normalize();
        MultiverseVfx.sound(ctx.level(), center, ModSounds.AEGIS_IMPACT, 0.8f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.AEGIS_IMPACT, center.add(direction.scale(FIELD_RADIUS)), direction, 1f);
    }

    private static Vec3 center(ServerPlayer player) {
        return player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
    }
}

package com.infinitemultiverse.abilities.core;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Launches the player along the look direction with guaranteed lift, and cancels the next fall's damage. */
public final class KineticLeapAbility extends Ability {
    private static final double BASE_LIFT = 0.45;
    private static final int FALL_PROTECTION_TICKS = 120;

    public KineticLeapAbility() {
        super(MultiverseSystem.CORE, ActivationType.INSTANT, Items.RABBIT_FOOT, true);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.kineticLeap.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.kineticLeap.cooldown();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        if (player.isPassenger()) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.cannot_while_riding"));
            return false;
        }
        double strength = MultiverseConfig.SERVER.kineticLeapStrength.get();
        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x * strength, Math.max(look.y * strength, 0.0) + BASE_LIFT, look.z * strength);

        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.resetFallDistance();
        ctx.data().grantFallProtection(FALL_PROTECTION_TICKS);

        MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.KINETIC_LEAP, 1.0f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.KINETIC_LEAP, player.position().add(0.0, 0.1, 0.0), velocity, 1f);
        return true;
    }
}

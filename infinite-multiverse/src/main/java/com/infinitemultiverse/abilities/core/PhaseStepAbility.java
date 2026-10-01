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
import com.infinitemultiverse.core.world.SafeTeleport;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Short-range blink along the look direction to the nearest validated safe position. */
public final class PhaseStepAbility extends Ability {
    private static final double MIN_DISTANCE_SQR = 1.5 * 1.5;

    public PhaseStepAbility() {
        super(MultiverseSystem.CORE, ActivationType.INSTANT, true);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.phaseStep.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.phaseStep.cooldown();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        if (player.isPassenger()) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.cannot_while_riding"));
            return false;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(MultiverseConfig.SERVER.phaseStepRange.get()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        Vec3 desiredFeet;
        if (hit.getType() == HitResult.Type.MISS) {
            desiredFeet = end.subtract(0.0, player.getEyeHeight(), 0.0);
        } else if (hit.getDirection() == Direction.UP) {
            desiredFeet = hit.getLocation();
        } else {
            desiredFeet = hit.getLocation().subtract(look.scale(0.6)).subtract(0.0, player.getEyeHeight() * 0.5, 0.0);
        }

        Optional<Vec3> destination = SafeTeleport.findSafeSpot(level, player, desiredFeet, 2, 3);
        Vec3 from = player.position();
        if (destination.isEmpty() || destination.get().distanceToSqr(from) < MIN_DISTANCE_SQR) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_safe_destination"));
            return false;
        }
        Vec3 to = destination.get();

        MultiverseVfx.sound(level, from, ModSounds.PHASE_STEP_DEPART, 0.9f, 1.0f);
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        ctx.data().grantFallProtection(20);
        MultiverseVfx.sound(level, to, ModSounds.PHASE_STEP_ARRIVE, 0.9f, 1.0f);

        Vec3 chestOffset = new Vec3(0.0, player.getBbHeight() * 0.55, 0.0);
        MultiverseVfx.broadcast(level, VfxIds.PHASE_STEP, from.add(chestOffset), to.subtract(from), 1f);
        return true;
    }
}

package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A pulse of life energy every second for 8 seconds, healing you and every non-hostile creature within 6 blocks. */
public final class HealingFieldAbility extends StandAbility {
    private static final double RADIUS = 6.0;
    private static final float HEAL = 2f;
    private static final int PULSES = 8;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        StandScheduler.repeat(1, 20, PULSES, index -> {
            if (!player.isAlive() || player.level() != level) {
                return false;
            }
            Vec3 center = player.position();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
                    e -> e.isAlive() && e.distanceToSqr(player) <= RADIUS * RADIUS && (e == player || !AbilityTargeting.isHostileTarget(player, e)))) {
                entity.heal(HEAL);
            }
            MultiverseVfx.broadcast(level, VfxIds.HEAL_PULSE, center.add(0.0, 0.1, 0.0), Vec3.ZERO, (float) RADIUS);
            if (index % 2 == 0) {
                MultiverseVfx.sound(level, center, ModSounds.STAND_HEAL, 0.6f, 1.0f);
            }
            return true;
        });
        return true;
    }
}

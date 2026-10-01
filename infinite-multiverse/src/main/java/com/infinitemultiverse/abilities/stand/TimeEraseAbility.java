package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.core.world.SafeTeleport;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * King Crimson erases 2.5 seconds: you become untouchable and unseen while enemies lose track of you, then you
 * reappear behind the nearest one.
 */
public final class TimeEraseAbility extends StandAbility {
    private static final int DURATION = 50;
    private static final double SEARCH = 16.0;
    private static final int COLOR = 0xE0284A;
    private static final Set<UUID> ERASED = new HashSet<>();

    public static boolean isErased(LivingEntity entity) {
        return ERASED.contains(entity.getUUID());
    }

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        ERASED.add(player.getUUID());
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0, false, false));
        MultiverseVfx.sound(level, player.position(), ModSounds.STAND_TIME_ERASE, 1.0f, 1.0f);
        MultiverseVfx.broadcast(level, VfxIds.TIME_ERASE, player.position().add(0, 1, 0), Vec3.ZERO, COLOR);
        MultiverseVfx.shout(level, player.position(), Component.translatable("message.infinitemultiverse.shout.time_erase")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 24.0);

        StandScheduler.repeat(1, 5, DURATION / 5, index -> {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SEARCH), m -> m.getTarget() == player)) {
                mob.setTarget(null);
            }
            return player.isAlive();
        });
        StandScheduler.later(DURATION, () -> {
            ERASED.remove(player.getUUID());
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            LivingEntity target = AbilityTargeting.hostilesInRadius(player, SEARCH).stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
            if (target != null) {
                Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.6, 0.0, 1.6));
                SafeTeleport.findSafeSpot(level, player, behind, 1, 2).ifPresent(spot -> {
                    player.teleportTo(spot.x, spot.y, spot.z);
                    player.resetFallDistance();
                });
            }
            MultiverseVfx.broadcast(level, VfxIds.TIME_ERASE, player.position().add(0, 1, 0), Vec3.ZERO, COLOR);
        });
        return true;
    }
}

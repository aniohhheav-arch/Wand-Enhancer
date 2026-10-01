package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gold Experience gives life to the ground you look at: three temporary bees burst out and attack the nearest hostile.
 * They never turn on their creator and fade away after 20 seconds.
 */
public final class LifeGiverAbility extends StandAbility {
    public static final String CREATOR_TAG = "infinitemultiverse_life_creator";
    private static final int LIFETIME = 400;
    private static final int COLOR = 0x8BE36A;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(8.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 origin = hit.getType() == HitResult.Type.MISS
                ? player.position().add(player.getLookAngle().multiply(3.0, 0.0, 3.0)).add(0.0, 1.0, 0.0)
                : hit.getLocation().add(0.0, 0.6, 0.0);

        LivingEntity target = AbilityTargeting.hostilesInRadius(player, 16.0).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        for (int i = 0; i < 3; i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }
            bee.moveTo(origin.x + (i - 1) * 0.5, origin.y, origin.z, player.getYRot(), 0f);
            bee.getPersistentData().putUUID(CREATOR_TAG, player.getUUID());
            if (target != null) {
                bee.setTarget(target);
                bee.setPersistentAngerTarget(target.getUUID());
                bee.startPersistentAngerTimer();
            }
            level.addFreshEntity(bee);
            StandScheduler.later(LIFETIME, () -> {
                if (bee.isAlive()) {
                    MultiverseVfx.broadcast(level, VfxIds.STAND_DISMISS, bee.position(), Vec3.ZERO, COLOR);
                    bee.discard();
                }
            });
        }
        MultiverseVfx.sound(level, origin, ModSounds.STAND_LIFE, 1.0f, 1.0f);
        MultiverseVfx.broadcast(level, VfxIds.STAND_AWAKEN, origin.subtract(0.0, 0.6, 0.0), Vec3.ZERO, COLOR);
        MultiverseVfx.shout(level, player.position(), Component.translatable("message.infinitemultiverse.shout.life")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 24.0);
        return true;
    }
}

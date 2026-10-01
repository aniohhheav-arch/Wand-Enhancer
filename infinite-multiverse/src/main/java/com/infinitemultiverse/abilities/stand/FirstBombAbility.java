package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Killer Queen touches what you look at (creature or block, 6 blocks) and turns it into a bomb. */
public final class FirstBombAbility extends StandAbility {
    private static final double REACH = 6.0;
    private static final int COLOR = 0xFF8EC7;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult blockHit = ctx.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB sweep = player.getBoundingBox().expandTowards(limit.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(ctx.level(), player, eye, limit, sweep,
                entity -> entity instanceof LivingEntity && entity != player && entity.isAlive());

        Vec3 at;
        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            KillerQueenBombs.set(player.getUUID(), new KillerQueenBombs.Bomb(ctx.level().dimension(), target.getUUID(), null));
            at = target.getBoundingBox().getCenter();
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            KillerQueenBombs.set(player.getUUID(), new KillerQueenBombs.Bomb(ctx.level().dimension(), null, blockHit.getBlockPos()));
            at = Vec3.atCenterOf(blockHit.getBlockPos());
        } else {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
            return false;
        }
        MultiverseVfx.sound(ctx.level(), at, ModSounds.STAND_BOMB_MARK, 1.0f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.KQ_MARK, at, Vec3.ZERO, COLOR);
        player.displayClientMessage(Component.translatable("message.infinitemultiverse.bomb_set").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }
}

package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.stand.StandScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Cursed Technique Lapse: Blue. A point of attraction 10 blocks ahead drags creatures, items and projectiles into it for 2 seconds. */
public final class BlueAbility extends PowerAbility {
    private static final double DISTANCE = 10;
    private static final double PULL_RADIUS = 7;
    private static final int DURATION = 40;
    private static final int COLOR = 0x3D7BFF;

    public BlueAbility() {
        super(MultiverseSystem.CURSED_TECHNIQUES);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(DISTANCE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(DISTANCE)) : hit.getLocation().subtract(player.getLookAngle());
        MultiverseVfx.shout(level, player.position(), Component.translatable("message.infinitemultiverse.shout.blue").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), 32);
        MultiverseVfx.sound(level, point, ModSounds.TIME_STOP, 0.8f, 1.6f);
        StandScheduler.repeat(1, 2, DURATION / 2, index -> {
            if (!player.isAlive()) {
                return false;
            }
            for (Entity entity : level.getEntities(player, new AABB(point, point).inflate(PULL_RADIUS))) {
                boolean pullable = entity instanceof ItemEntity || entity instanceof Projectile
                        || (entity instanceof LivingEntity && AbilityTargeting.isHostileTarget(player, entity));
                if (!pullable) {
                    continue;
                }
                Vec3 toward = point.subtract(entity.position());
                double distance = toward.length();
                if (distance < 0.6) {
                    entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2));
                    if (entity instanceof LivingEntity living && index % 5 == 0) {
                        living.invulnerableTime = 0;
                        living.hurt(player.damageSources().indirectMagic(player, player), 2.5f);
                    }
                } else {
                    entity.setDeltaMovement(toward.normalize().scale(Math.min(0.9, 0.25 + distance * 0.08)));
                }
                entity.hurtMarked = true;
            }
            if (index % 2 == 0) {
                MultiverseVfx.fx(level, VfxIds.ORB, point, new Vec3(1.4, 0, 0), COLOR);
            }
            return true;
        });
        return true;
    }
}

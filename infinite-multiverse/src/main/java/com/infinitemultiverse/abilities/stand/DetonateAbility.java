package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * "Killer Queen has already touched that." Detonates the current bomb. Blocks are never destroyed unless the
 * server enables terrain modification.
 */
public final class DetonateAbility extends StandAbility {
    private static final float POWER = 3.0f;
    private static final float DIRECT_DAMAGE = 8f;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        KillerQueenBombs.Bomb bomb = KillerQueenBombs.take(player.getUUID());
        ServerLevel level = bomb == null ? null : player.server.getLevel(bomb.dimension());
        if (bomb == null || level == null) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_bomb"));
            return false;
        }
        Vec3 at;
        if (bomb.entity() != null) {
            Entity target = level.getEntity(bomb.entity());
            if (target == null || !target.isAlive()) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_bomb"));
                return false;
            }
            at = target.getBoundingBox().getCenter();
            if (target instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                living.hurt(player.damageSources().explosion(player, player), DIRECT_DAMAGE);
            }
        } else {
            at = Vec3.atCenterOf(bomb.block());
        }
        Level.ExplosionInteraction interaction = MultiverseConfig.SERVER.allowTerrainModification.get()
                ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(player, at.x, at.y, at.z, POWER, interaction);
        MultiverseVfx.broadcast(level, VfxIds.STAND_HEAVY, at, Vec3.ZERO, 1f);
        MultiverseVfx.shout(level, player.position(), Component.translatable("message.infinitemultiverse.shout.detonate")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), 24.0);
        return true;
    }
}

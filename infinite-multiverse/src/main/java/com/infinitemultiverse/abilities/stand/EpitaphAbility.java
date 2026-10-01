package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Epitaph sees a few seconds ahead: every hostile within 32 blocks glows through walls and shows where it is heading. */
public final class EpitaphAbility extends StandAbility {
    private static final double RADIUS = 32.0;
    private static final int DURATION = 160;
    private static final int LOOKAHEAD_TICKS = 30;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        int seen = 0;
        for (LivingEntity target : AbilityTargeting.hostilesInRadius(ctx.player(), RADIUS)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, DURATION, 0, false, false), ctx.player());
            Vec3 future = target.position().add(target.getDeltaMovement().multiply(LOOKAHEAD_TICKS, 0.0, LOOKAHEAD_TICKS));
            MultiverseVfx.broadcast(ctx.level(), VfxIds.EPITAPH, future, target.position().subtract(future), target.getBbHeight());
            seen++;
        }
        MultiverseVfx.sound(ctx.level(), ctx.player().position(), ModSounds.STAND_EPITAPH, 0.8f, 1.0f);
        ctx.player().displayClientMessage(Component.translatable("message.infinitemultiverse.epitaph", seen)
                .withStyle(ChatFormatting.RED), true);
        return true;
    }
}

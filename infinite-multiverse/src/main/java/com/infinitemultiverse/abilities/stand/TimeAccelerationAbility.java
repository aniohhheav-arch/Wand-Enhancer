package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Made in Heaven accelerates time itself for 10 seconds: the sun and moon race across the sky (one day every ~13 s). */
public final class TimeAccelerationAbility extends StandAbility {
    private static final int DURATION = 200;
    private static final long TIME_PER_TICK = 90;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerLevel level = ctx.level();
        if (level.dimensionType().hasFixedTime()) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.no_sky"));
            return false;
        }
        StandScheduler.repeat(1, 1, DURATION, index -> {
            level.setDayTime(level.getDayTime() + TIME_PER_TICK);
            if (index % 10 == 0) {
                MultiverseVfx.broadcast(level, VfxIds.AFTERIMAGE, ctx.player().position().add(0, 2.5, 0), new Vec3(0, 0.3, 0), 0xFFF6C0);
            }
            return true;
        });
        MultiverseVfx.sound(level, ctx.player().position(), ModSounds.STAND_ACCELERATE, 1.2f, 0.6f);
        MultiverseVfx.shout(level, ctx.player().position(), Component.translatable("message.infinitemultiverse.shout.heaven")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 64.0);
        return true;
    }
}

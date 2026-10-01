package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.core.world.SafeTeleport;
import com.infinitemultiverse.stand.RewindTracker;
import com.infinitemultiverse.stand.StandEntity;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Localized time loop: you return to where you stood about five seconds ago, with your health from then if it was higher. */
public final class BitesTheDustAbility extends StandAbility {
    private static final int COLOR = 0xFF8EC7;

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        ServerPlayer player = ctx.player();
        RewindTracker.Snapshot snapshot = RewindTracker.oldest(player);
        if (snapshot == null) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.nothing_to_rewind"));
            return false;
        }
        Optional<Vec3> spot = SafeTeleport.findSafeSpot(ctx.level(), player, snapshot.position(), 1, 2);
        if (spot.isEmpty()) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_safe_destination"));
            return false;
        }
        Vec3 from = player.position();
        Vec3 to = spot.get();
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        player.clearFire();
        player.setHealth(Math.max(player.getHealth(), snapshot.health()));
        RewindTracker.clear(player);
        MultiverseVfx.sound(ctx.level(), to, ModSounds.STAND_REWIND, 1.0f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.REWIND, from.add(0, 1, 0), Vec3.ZERO, COLOR);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.REWIND, to.add(0, 1, 0), Vec3.ZERO, COLOR);
        MultiverseVfx.shout(ctx.level(), to, Component.translatable("message.infinitemultiverse.shout.bites_the_dust")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), 24.0);
        return true;
    }
}

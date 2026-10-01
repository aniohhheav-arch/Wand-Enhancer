package com.infinitemultiverse.core.ability;

import com.infinitemultiverse.core.data.PlayerMultiverseData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record AbilityContext(ServerPlayer player, ServerLevel level, PlayerMultiverseData data) {
    public static AbilityContext of(ServerPlayer player) {
        return new AbilityContext(player, player.serverLevel(), AbilityManager.data(player));
    }
}

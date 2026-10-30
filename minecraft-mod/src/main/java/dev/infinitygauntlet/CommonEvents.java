package dev.infinitygauntlet;

import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.command.GauntletCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class CommonEvents {
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        GauntletCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityCaster.syncCooldowns(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityCaster.syncCooldowns(player);
        }
    }

    private CommonEvents() {
    }
}

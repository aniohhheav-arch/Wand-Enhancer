package com.infinitemultiverse.client;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.client.hud.AbilityHudLayer;
import com.infinitemultiverse.client.screen.MultiverseMenuScreen;
import com.infinitemultiverse.client.vfx.EnergyParticle;
import com.infinitemultiverse.core.network.ActivateAbilityPayload;
import com.infinitemultiverse.core.network.ToggleStandPayload;
import com.infinitemultiverse.client.stand.StandRenderer;
import com.infinitemultiverse.core.registry.ModEntities;
import com.infinitemultiverse.core.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEvents {
    private ClientEvents() {
    }

    @EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            MultiverseKeyMappings.register(event);
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, AbilityHudLayer.ID, new AbilityHudLayer());
            event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ClientTimeStop.LAYER_ID, ClientTimeStop::renderOverlay);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.STAND.get(), StandRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.ENERGY_MOTE.get(), EnergyParticle.Provider::new);
            event.registerSpriteSet(ModParticles.SPARK.get(), EnergyParticle.Provider::new);
        }
    }

    @EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientMultiverseState.tick();
            ClientTimeStop.tick();
            if (minecraft.player == null) {
                return;
            }
            while (MultiverseKeyMappings.OPEN_MENU.get().consumeClick()) {
                minecraft.setScreen(new MultiverseMenuScreen());
            }
            while (MultiverseKeyMappings.TOGGLE_STAND.get().consumeClick()) {
                PacketDistributor.sendToServer(ToggleStandPayload.INSTANCE);
            }
            for (int slot = 0; slot < MultiverseKeyMappings.ABILITY_SLOTS.size(); slot++) {
                while (MultiverseKeyMappings.ABILITY_SLOTS.get(slot).get().consumeClick()) {
                    PacketDistributor.sendToServer(new ActivateAbilityPayload(slot));
                }
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientMultiverseState.reset();
            ClientTimeStop.reset();
        }
    }
}

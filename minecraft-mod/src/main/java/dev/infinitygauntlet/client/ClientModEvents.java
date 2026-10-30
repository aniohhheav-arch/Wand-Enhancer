package dev.infinitygauntlet.client;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.gui.AbilityHud;
import dev.infinitygauntlet.client.gui.ScreenEffectsLayer;
import dev.infinitygauntlet.client.particle.EnergyParticle;
import dev.infinitygauntlet.client.render.MiniPortalRenderer;
import dev.infinitygauntlet.client.render.RupturePortalRenderer;
import dev.infinitygauntlet.client.render.SpaceBoltRenderer;
import dev.infinitygauntlet.client.render.SpatialCompressionRenderer;
import dev.infinitygauntlet.client.render.SpatialPrisonRenderer;
import dev.infinitygauntlet.client.render.SpatialRiftRenderer;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RUPTURE_PORTAL.get(), RupturePortalRenderer::new);
        event.registerEntityRenderer(ModEntities.SPATIAL_RIFT.get(), SpatialRiftRenderer::new);
        event.registerEntityRenderer(ModEntities.SPATIAL_COMPRESSION.get(), SpatialCompressionRenderer::new);
        event.registerEntityRenderer(ModEntities.SPATIAL_PRISON.get(), SpatialPrisonRenderer::new);
        event.registerEntityRenderer(ModEntities.MINI_PORTAL.get(), MiniPortalRenderer::new);
        event.registerEntityRenderer(ModEntities.SPACE_BOLT.get(), SpaceBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.TIME_FIELD.get(), dev.infinitygauntlet.client.render.TimeFieldRenderer::new);
        event.registerEntityRenderer(ModEntities.TEMPORAL_LOOP.get(), dev.infinitygauntlet.client.render.TemporalLoopRenderer::new);
        event.registerEntityRenderer(ModEntities.CHRONO_BARRIER.get(), dev.infinitygauntlet.client.render.ChronoBarrierRenderer::new);
        event.registerEntityRenderer(ModEntities.TIME_FRACTURE.get(), dev.infinitygauntlet.client.render.TimeFractureRenderer::new);
        event.registerEntityRenderer(ModEntities.TEMPORAL_ECHO.get(), dev.infinitygauntlet.client.render.TemporalEchoRenderer::new);
        event.registerEntityRenderer(ModEntities.DIMENSIONAL_RIFT.get(), dev.infinitygauntlet.client.breach.RiftRenderer::new);
        event.registerEntityRenderer(ModEntities.OBSERVATION_PANEL.get(), dev.infinitygauntlet.client.breach.PanelRenderer::new);
        event.registerEntityRenderer(ModEntities.SPECIMEN.get(), dev.infinitygauntlet.client.breach.SpecimenRenderer::new);
        event.registerBlockEntityRenderer(dev.infinitygauntlet.registry.ModBlockEntities.CONSCIOUSNESS_NEXUS.get(),
                dev.infinitygauntlet.client.nexus.NexusRenderer::new);
    }

    @SubscribeEvent
    static void onDimensionEffects(net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(InfinityGauntlet.id("observation_chamber"), new dev.infinitygauntlet.client.breach.ChamberSpecialEffects());
    }

    @SubscribeEvent
    static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ENERGY_GLOW.get(), sprites -> new EnergyParticle.Provider(sprites, false, 14));
        event.registerSpriteSet(ModParticles.ENERGY_SPARK.get(), sprites -> new EnergyParticle.Provider(sprites, false, 12));
        event.registerSpriteSet(ModParticles.ENERGY_MOTE.get(), sprites -> new EnergyParticle.Provider(sprites, false, 20));
        event.registerSpriteSet(ModParticles.DIMENSIONAL_SMOKE.get(), sprites -> new EnergyParticle.Provider(sprites, true, 30));
        event.registerSpriteSet(ModParticles.ENERGY_SHARD.get(), sprites -> new EnergyParticle.Provider(sprites, false, 16));
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(Keybinds.NEXT_ABILITY);
        event.register(Keybinds.PREVIOUS_ABILITY);
        event.register(Keybinds.NEXT_STONE);
        event.register(Keybinds.ABILITY_WHEEL);
        event.register(Keybinds.REMOTE_BLINK);
        event.register(Keybinds.NEURAL_OVERRIDE);
        event.register(Keybinds.PORTAL_EXTRACTION);
        event.register(Keybinds.SWITCH_FEED);
        event.register(Keybinds.EMERGENCY_DISCONNECT);
    }

    @SubscribeEvent
    static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, InfinityGauntlet.id("ability_hud"), AbilityHud::render);
        event.registerAboveAll(InfinityGauntlet.id("screen_effects"), ScreenEffectsLayer::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, InfinityGauntlet.id("remote_control"), dev.infinitygauntlet.client.nexus.RemoteControlHud::render);
    }

    private ClientModEvents() {
    }
}

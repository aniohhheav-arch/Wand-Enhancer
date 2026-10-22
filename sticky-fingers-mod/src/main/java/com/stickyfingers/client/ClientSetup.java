package com.stickyfingers.client;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.client.fx.CameraFx;
import com.stickyfingers.client.fx.ZipperParticles;
import com.stickyfingers.client.gui.HudOverlay;
import com.stickyfingers.client.render.*;
import com.stickyfingers.registry.ModBlocks;
import com.stickyfingers.registry.ModEntities;
import com.stickyfingers.registry.ModParticles;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = StickyFingersMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.ZIPPER_PLATFORM.get(), RenderType.translucent()));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STAND.get(), StandRenderer::new);
        event.registerEntityRenderer(ModEntities.ZIPPER.get(), ZipperRenderer::new);
        event.registerEntityRenderer(ModEntities.ZIPPER_PROJECTILE.get(), ZipperProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.DECOY.get(), DecoyRenderer::new);
        event.registerEntityRenderer(ModEntities.D4C_CLONE.get(), D4CCloneRenderer::new);
        event.registerEntityRenderer(ModEntities.D4C_PORTAL.get(), D4CPortalRenderer::new);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (var type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer r) {
                r.addLayer(new ZipperOverlayLayer(r));
            }
        }
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer r) {
                r.addLayer(new ZipperOverlayLayer(r));
            }
        }
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ZIPPER_SPARK.get(), ZipperParticles.Spark::new);
        event.registerSpriteSet(ModParticles.ZIPPER_TOOTH.get(), ZipperParticles.Tooth::new);
        event.registerSpriteSet(ModParticles.VOID_MOTE.get(), ZipperParticles.Mote::new);
        event.registerSpriteSet(ModParticles.SEAM_GLOW.get(), ZipperParticles.Seam::new);
        event.registerSpriteSet(ModParticles.PUNCH_IMPACT.get(), ZipperParticles.Impact::new);
        event.registerSpriteSet(ModParticles.AFTERIMAGE.get(), ZipperParticles.Afterimage::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(Keybinds.SUMMON);
        event.register(Keybinds.WHEEL);
        event.register(Keybinds.USE);
        event.register(Keybinds.NETWORK);
        event.register(Keybinds.ORIENT);
        event.register(Keybinds.CLOSE_ALL);
        event.register(Keybinds.TIME_STOP);
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(StickyFingersMod.id("screen_fx"), CameraFx::renderOverlay);
        event.registerAboveAll(StickyFingersMod.id("hud"), HudOverlay::render);
    }

    private ClientSetup() {}
}

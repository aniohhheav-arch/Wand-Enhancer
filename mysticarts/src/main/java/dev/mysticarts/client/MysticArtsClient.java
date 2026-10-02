package dev.mysticarts.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mysticarts.MaClientConfig;
import dev.mysticarts.MysticArts;
import dev.mysticarts.client.hud.MysticHud;
import dev.mysticarts.client.particle.GlowParticle;
import dev.mysticarts.client.render.ArtifactLayer;
import dev.mysticarts.client.render.CloakLayer;
import dev.mysticarts.client.render.GauntletRenderer;
import dev.mysticarts.client.render.MaShaders;
import dev.mysticarts.client.render.ScreenFx;
import dev.mysticarts.client.render.SpellWorldRenderer;
import dev.mysticarts.client.render.WarpFx;
import dev.mysticarts.client.render.block.RelicPedestalRenderer;
import dev.mysticarts.client.render.block.RitualAltarRenderer;
import dev.mysticarts.client.render.entity.MysticBoltRenderer;
import dev.mysticarts.client.render.entity.MysticCloneRenderer;
import dev.mysticarts.client.render.entity.ShockwaveRenderer;
import dev.mysticarts.client.render.entity.SlingPortalRenderer;
import dev.mysticarts.client.render.entity.SoulWispRenderer;
import dev.mysticarts.client.render.entity.SpectralBeastRenderer;
import dev.mysticarts.client.render.entity.SpellFieldRenderer;
import dev.mysticarts.registry.MaBlockEntities;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaItems;
import dev.mysticarts.registry.MaParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Matrix4f;

@Mod(value = MysticArts.MODID, dist = Dist.CLIENT)
public final class MysticArtsClient {
    public MysticArtsClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, MaClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(MaShaders::register);
        modBus.addListener(MysticArtsClient::registerRenderers);
        modBus.addListener(MysticArtsClient::addLayers);
        modBus.addListener(MysticArtsClient::registerParticles);
        modBus.addListener(MysticArtsClient::registerGuiLayers);
        modBus.addListener(MysticArtsClient::registerExtensions);
        modBus.addListener(KeyBindings::register);
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(MaBlocks.MANDALA_GLASS.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(MaBlocks.MIRROR_SHARD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(MaBlocks.SPELL_BARRIER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(MaBlocks.SPELL_ILLUSION.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(MaBlocks.SPELL_HAZARD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(MaBlocks.RIFT_FISSURE.get(), RenderType.translucent());
        }));

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(MysticArtsClient::onClientTick);
        game.addListener((RenderFrameEvent.Pre e) -> MaShaders.updateTime());
        game.addListener(MysticArtsClient::onRenderStage);
        game.addListener(MysticArtsClient::onCameraAngles);
        game.addListener(MysticArtsClient::onEntityTick);
        game.addListener((RenderPlayerEvent.Pre e) -> GauntletRenderer.rendering = e.getEntity());
        game.addListener((RenderPlayerEvent.Post e) -> GauntletRenderer.rendering = null);
        game.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientPower.reset();
            CasterStates.clear();
            ClientMarks.clear();
            ClientFx.clear();
            WarpFx.clear();
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(MaEntities.SLING_PORTAL.get(), SlingPortalRenderer::new);
        e.registerEntityRenderer(MaEntities.MYSTIC_BOLT.get(), MysticBoltRenderer::new);
        e.registerEntityRenderer(MaEntities.SHOCKWAVE.get(), ShockwaveRenderer::new);
        e.registerEntityRenderer(MaEntities.SPELL_FIELD.get(), SpellFieldRenderer::new);
        e.registerEntityRenderer(MaEntities.SOUL_WISP.get(), SoulWispRenderer::new);
        e.registerEntityRenderer(MaEntities.MYSTIC_CLONE.get(), MysticCloneRenderer::new);
        e.registerEntityRenderer(MaEntities.SPECTRAL_BEAST.get(), SpectralBeastRenderer::new);
        e.registerBlockEntityRenderer(MaBlockEntities.RITUAL_ALTAR.get(), RitualAltarRenderer::new);
        e.registerBlockEntityRenderer(MaBlockEntities.RELIC_PEDESTAL.get(), RelicPedestalRenderer::new);
    }

    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model model : e.getSkins()) {
            if (e.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new CloakLayer(renderer));
                renderer.addLayer(new ArtifactLayer(renderer));
                renderer.addLayer(new GauntletRenderer.ArmLayer(renderer));
            }
        }
    }

    private static void registerParticles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(MaParticles.SPARK.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.SPARK));
        e.registerSpriteSet(MaParticles.MOTE.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.MOTE));
        e.registerSpriteSet(MaParticles.STREAK.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.STREAK));
        e.registerSpriteSet(MaParticles.RING.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.RING));
        e.registerSpriteSet(MaParticles.RUNE.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.RUNE));
        e.registerSpriteSet(MaParticles.ASH.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.ASH));
        e.registerSpriteSet(MaParticles.INFALL.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.INFALL));
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(MysticArts.id("hud"), MysticHud::render);
    }

    private static void registerExtensions(RegisterClientExtensionsEvent e) {
        e.registerItem(new IClientItemExtensions() {
            private GauntletRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new GauntletRenderer();
                return renderer;
            }
        }, MaItems.INFINITY_GAUNTLET.get());
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.isPaused()) {
            ClientPower.tick();
            CasterStates.tick();
            ClientMarks.tick();
            ClientFx.tick();
        }
        KeyBindings.tick();
    }

    /** Frozen and slowed entities stop ticking on the client too, so they hang perfectly still. */
    private static void onEntityTick(EntityTickEvent.Pre e) {
        var entity = e.getEntity();
        if (!entity.level().isClientSide || entity == Minecraft.getInstance().player) return;
        if (ClientMarks.suppress(entity, entity.level().getGameTime())) e.setCanceled(true);
    }

    private static void onRenderStage(RenderLevelStageEvent e) {
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = e.getCamera().getPosition();
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            PoseStack ps = e.getPoseStack();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            ps.pushPose();
            SpellWorldRenderer.render(ps, buffers, cam, partial);
            ClientFx.render(ps, buffers, cam, partial);
            ps.popPose();
            buffers.endBatch();
            RenderSystem.defaultBlendFunc();
            return;
        }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Matrix4f view = new Matrix4f(e.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(e.getProjectionMatrix());
        WarpFx.render(cam, view, proj);
        ClientFx.compose(partial, new Matrix4f(proj).mul(view), cam);
        ScreenFx.render();
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        float shake = ClientFx.shake();
        if (shake < 0.001f) return;
        float t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + (float) e.getPartialTick();
        e.setYaw(e.getYaw() + (float) (Math.sin(t * 1.7) + Math.sin(t * 3.1) * 0.5) * shake * 1.2f);
        e.setPitch(e.getPitch() + (float) (Math.cos(t * 2.3) + Math.sin(t * 4.7) * 0.5) * shake);
        e.setRoll(e.getRoll() + Mth.sin(t * 2.9f) * shake * 1.5f);
    }
}

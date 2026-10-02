package dev.riftverse.client;

import dev.riftverse.Riftverse;
import dev.riftverse.block.PortalFieldBlock;
import dev.riftverse.block.RiftBlock;
import dev.riftverse.client.cinematic.CameraRig;
import dev.riftverse.client.cinematic.CinematicDirector;
import dev.riftverse.client.hud.CinematicHud;
import dev.riftverse.client.particle.GlowParticle;
import dev.riftverse.client.render.ArmorAuraLayer;
import dev.riftverse.client.render.RvShaders;
import dev.riftverse.client.render.ScreenFx;
import dev.riftverse.client.render.SpatialFx;
import dev.riftverse.client.render.UniverseDimensionEffects;
import dev.riftverse.client.render.block.GravityLiftRenderer;
import dev.riftverse.client.render.block.PortalFieldRenderer;
import dev.riftverse.client.render.block.RiftRenderer;
import dev.riftverse.client.render.entity.AbyssalLeviathanRenderer;
import dev.riftverse.client.render.entity.AstralJellyRenderer;
import dev.riftverse.client.render.entity.BlackHoleRenderer;
import dev.riftverse.client.render.entity.BoltRenderer;
import dev.riftverse.client.render.entity.CrystalSentinelRenderer;
import dev.riftverse.client.render.entity.GlitchlingRenderer;
import dev.riftverse.client.render.entity.GravityWellRenderer;
import dev.riftverse.client.render.entity.LumenStriderRenderer;
import dev.riftverse.client.render.entity.NeonDroneRenderer;
import dev.riftverse.client.render.entity.PortalRenderer;
import dev.riftverse.client.render.entity.RiftWardenRenderer;
import dev.riftverse.client.render.entity.RiftWraithRenderer;
import dev.riftverse.client.render.entity.SkyWhaleRenderer;
import dev.riftverse.client.render.entity.VoidStalkerRenderer;
import dev.riftverse.registry.RvBlockEntities;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

@Mod(value = Riftverse.MODID, dist = Dist.CLIENT)
public final class RiftverseClient {
    public RiftverseClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(RvShaders::register);
        modBus.addListener(RiftverseClient::registerRenderers);
        modBus.addListener(RiftverseClient::addLayers);
        modBus.addListener(RiftverseClient::registerParticles);
        modBus.addListener(RiftverseClient::registerDimensionEffects);
        modBus.addListener(RiftverseClient::registerGuiLayers);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item e) -> e.register(
                (stack, layer) -> layer == 1 ? (0xFF000000 | dev.riftverse.item.relic.Relics.color(stack)) : 0xFFFFFFFF,
                dev.riftverse.registry.RvItems.RELIC_BLADE.get(), dev.riftverse.registry.RvItems.RELIC_BLASTER.get()));
        modBus.addListener(KeyBindings::register);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(RiftverseClient::onClientTick);
        game.addListener(RiftverseClient::onRenderFrame);
        game.addListener(RiftverseClient::onRenderStage);
        game.addListener(RiftverseClient::onFov);
        game.addListener(RiftverseClient::onCameraAngles);
        game.addListener(RiftverseClient::onFogColor);
        game.addListener(RiftverseClient::onRenderFog);
        game.addListener(RiftverseClient::onMovementInput);
        game.addListener(RiftverseClient::onPlayerTurn);
        game.addListener(RiftverseClient::onHighlight);
        game.addListener(RiftverseClient::onGuiLayer);
        game.addListener((SelectMusicEvent e) -> UniverseAmbience.selectMusic(e));
        game.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientUniverseState.reset();
            CinematicDirector.reset();
            SpatialFx.clear();
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(RvEntities.BLACK_HOLE.get(), BlackHoleRenderer::new);
        e.registerEntityRenderer(RvEntities.PORTAL.get(), PortalRenderer::new);
        e.registerEntityRenderer(RvEntities.PORTAL_BOLT.get(), BoltRenderer.PortalBolt::new);
        e.registerEntityRenderer(RvEntities.ENERGY_BOLT.get(), BoltRenderer.Energy::new);
        e.registerEntityRenderer(RvEntities.SINGULARITY_GRENADE.get(), ThrownItemRenderer::new);
        e.registerEntityRenderer(RvEntities.GRAVITY_WELL.get(), GravityWellRenderer::new);
        e.registerEntityRenderer(RvEntities.ASTRAL_JELLY.get(), AstralJellyRenderer::new);
        e.registerEntityRenderer(RvEntities.SKY_WHALE.get(), SkyWhaleRenderer::new);
        e.registerEntityRenderer(RvEntities.LUMEN_STRIDER.get(), LumenStriderRenderer::new);
        e.registerEntityRenderer(RvEntities.NEON_DRONE.get(), NeonDroneRenderer::new);
        e.registerEntityRenderer(RvEntities.GLITCHLING.get(), GlitchlingRenderer::new);
        e.registerEntityRenderer(RvEntities.VOID_STALKER.get(), VoidStalkerRenderer::new);
        e.registerEntityRenderer(RvEntities.CRYSTAL_SENTINEL.get(), CrystalSentinelRenderer::new);
        e.registerEntityRenderer(RvEntities.RIFT_WRAITH.get(), RiftWraithRenderer::new);
        e.registerEntityRenderer(RvEntities.RIFT_WARDEN.get(), RiftWardenRenderer::new);
        e.registerEntityRenderer(RvEntities.ABYSSAL_LEVIATHAN.get(), AbyssalLeviathanRenderer::new);
        e.registerEntityRenderer(RvEntities.VOID_CULTIST.get(), dev.riftverse.client.render.entity.CreatureSkins.VoidCultist::new);
        e.registerEntityRenderer(RvEntities.CRYSTAL_SPIDER.get(), dev.riftverse.client.render.entity.CreatureSkins.CrystalSpider::new);
        e.registerEntityRenderer(RvEntities.STAR_MOTH.get(), dev.riftverse.client.render.entity.CreatureSkins.StarMoth::new);
        e.registerEntityRenderer(RvEntities.LUNAR_GOLEM.get(), dev.riftverse.client.render.entity.CreatureSkins.LunarGolem::new);
        e.registerEntityRenderer(RvEntities.DENIZEN.get(), dev.riftverse.client.render.entity.CreatureSkins.Denizen::new);
        e.registerEntityRenderer(RvEntities.COSMIC_DEITY.get(), dev.riftverse.client.render.entity.CosmicDeityRenderer::new);
        e.registerBlockEntityRenderer(RvBlockEntities.RIFT.get(), RiftRenderer::new);
        e.registerBlockEntityRenderer(RvBlockEntities.PORTAL_FIELD.get(), PortalFieldRenderer::new);
        e.registerBlockEntityRenderer(RvBlockEntities.GRAVITY_LIFT.get(), GravityLiftRenderer::new);
    }

    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model model : e.getSkins()) {
            if (e.getSkin(model) instanceof PlayerRenderer renderer) renderer.addLayer(new ArmorAuraLayer(renderer));
        }
    }

    private static void registerParticles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(RvParticles.SPARK.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.SPARK));
        e.registerSpriteSet(RvParticles.MOTE.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.MOTE));
        e.registerSpriteSet(RvParticles.STREAK.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.STREAK));
        e.registerSpriteSet(RvParticles.RING.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.RING));
        e.registerSpriteSet(RvParticles.GLITCH.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.GLITCH));
        e.registerSpriteSet(RvParticles.DUST.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.DUST));
        e.registerSpriteSet(RvParticles.INFALL.get(), s -> new GlowParticle.Provider(s, GlowParticle.Behavior.INFALL));
    }

    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent e) {
        UniverseDimensionEffects effects = new UniverseDimensionEffects();
        e.register(RvWorldgen.EXPANSE.location(), effects);
        e.register(RvWorldgen.NEXUS.location(), effects);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(Riftverse.id("cinematic"), CinematicHud::render);
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.isPaused()) {
            CinematicDirector.tick();
            ClientEffects.tick();
            UniverseAmbience.tick();
        }
        KeyBindings.tick();
    }

    private static void onRenderFrame(RenderFrameEvent.Pre e) {
        RvShaders.updateTime();
        if (Minecraft.getInstance().level != null) CinematicDirector.updateRig(e.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    private static void onRenderStage(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            return;
        }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f view = new Matrix4f(e.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(e.getProjectionMatrix());
        SpatialFx.render(cam, view, proj);
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        ClientEffects.compose(partial, new Matrix4f(proj).mul(view), cam);
        ScreenFx.render();
    }

    private static void onFov(ViewportEvent.ComputeFov e) {
        float partial = (float) e.getPartialTick();
        double add = CinematicDirector.fovOffset(partial) + ClientEffects.ambientFov();
        if (add != 0) e.setFOV(Mth.clamp(e.getFOV() + add, 20.0, 165.0));
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (CameraRig.current() != null) return;
        float partial = (float) e.getPartialTick();
        float shake = CinematicDirector.shakeAmount(partial) + ClientEffects.ambientShake();
        float t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;
        if (shake > 0.001f) {
            e.setYaw(e.getYaw() + (float) (Math.sin(t * 1.7) + Math.sin(t * 3.1) * 0.5) * shake * 1.2f);
            e.setPitch(e.getPitch() + (float) (Math.cos(t * 2.3) + Math.sin(t * 4.7) * 0.5) * shake * 1.0f);
        }
        e.setRoll(e.getRoll() + CinematicDirector.roll(partial) + (float) Math.sin(t * 2.9) * shake * 1.5f);
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor e) {
        if (!ClientUniverseState.active() || e.getCamera().getFluidInCamera() != FogType.NONE) return;
        var spec = ClientUniverseState.spec();
        if (spec == null) return;
        Vec3 fog = Minecraft.getInstance().level.effects().getBrightnessDependentFogColor(new Vec3(e.getRed(), e.getGreen(), e.getBlue()), 1f);
        e.setRed((float) fog.x);
        e.setGreen((float) fog.y);
        e.setBlue((float) fog.z);
    }

    private static void onRenderFog(ViewportEvent.RenderFog e) {
        if (!ClientUniverseState.active() || e.getType() != FogType.NONE) return;
        var spec = ClientUniverseState.spec();
        if (spec == null) return;
        float density = spec.fogDensity;
        if (density <= 0.05f) return;
        e.scaleFarPlaneDistance(Mth.lerp(density, 1f, 0.3f));
        e.scaleNearPlaneDistance(Mth.lerp(density, 1f, 0.05f));
        e.setCanceled(true);
    }

    private static void onMovementInput(MovementInputUpdateEvent e) {
        if (!CinematicDirector.inputLocked()) return;
        Input input = e.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    private static void onPlayerTurn(CalculatePlayerTurnEvent e) {
        if (CinematicDirector.inputLocked()) e.setMouseSensitivity(-1.0 / 3.0);
    }

    private static void onHighlight(RenderHighlightEvent.Block e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState state = mc.level.getBlockState(e.getTarget().getBlockPos());
        if (state.getBlock() instanceof RiftBlock || state.getBlock() instanceof PortalFieldBlock) e.setCanceled(true);
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre e) {
        if (e.getName().getNamespace().equals(Riftverse.MODID)) return;
        boolean cinematic = CinematicDirector.inJourney() || CameraRig.current() != null;
        if (cinematic && !e.getName().equals(VanillaGuiLayers.CHAT) && !e.getName().equals(VanillaGuiLayers.SUBTITLE_OVERLAY)) e.setCanceled(true);
    }
}

package dev.finalframe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.finalframe.FinalFrame;
import dev.finalframe.client.camera.CinematicCamera;
import dev.finalframe.client.choreo.Choreographies;
import dev.finalframe.client.choreo.SheriffChoreography;
import dev.finalframe.client.dev.AutoDirector;
import dev.finalframe.client.render.CinematicHud;
import dev.finalframe.client.render.ColorGrade;
import dev.finalframe.client.render.EntityPoses;
import dev.finalframe.client.render.HolsterLayer;
import dev.finalframe.client.render.RevolverRenderer;
import dev.finalframe.client.render.WorldFx;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.network.FFNetwork;
import dev.finalframe.registry.FFRegistry;
import dev.finalframe.sheriff.SheriffFinisher;
import dev.finalframe.sheriff.SheriffsLastWordItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point: wires the cinematic engine, renderers, HUD and input lock. */
@Mod(value = FinalFrame.MOD_ID, dist = Dist.CLIENT)
public final class FinalFrameClient {
    public FinalFrameClient(IEventBus modBus, ModContainer container) {
        Choreographies.register(SheriffFinisher.INSTANCE.id(), SheriffChoreography.INSTANCE);
        FFNetwork.clientSink = ClientFinisherManager.INSTANCE;
        SheriffsLastWordItem.clientListener = RevolverAnimator::onUse;
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(FinalFrameClient::registerExtensions);
        modBus.addListener(FinalFrameClient::registerModels);
        modBus.addListener(FinalFrameClient::addLayers);
        modBus.addListener(FinalFrameClient::registerGui);
        modBus.addListener(FinalFrameClient::registerReload);

        IEventBus game = NeoForge.EVENT_BUS;
        game.register(EntityPoses.class);
        game.addListener(FinalFrameClient::onClientTickPre);
        game.addListener(FinalFrameClient::onClientTickPost);
        game.addListener(FinalFrameClient::onLogout);
        game.addListener(FinalFrameClient::onMovementInput);
        game.addListener(FinalFrameClient::onInteractionKey);
        game.addListener(FinalFrameClient::onScroll);
        game.addListener(FinalFrameClient::onRenderHand);
        game.addListener(FinalFrameClient::onFov);
        game.addListener(WorldFx::onRenderStage);
        game.addListener(CinematicHud::onLayer);
        game.addListener(AudioDucker::onPlaySound);
        game.addListener(FinalFrameClient::onToast);
        AutoDirector.install(game);
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RevolverRenderer.get();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                                   float partialTick, float equipProcess, float swingProcess) {
                return RevolverAnimator.applyHandTransform(poseStack, player, arm, itemInHand, partialTick, equipProcess, swingProcess);
            }
        }, FFRegistry.SHERIFFS_LAST_WORD.get());
    }

    private static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(RevolverRenderer.FRAME);
        event.register(RevolverRenderer.CYLINDER);
        event.register(RevolverRenderer.HAMMER);
        event.register(RevolverRenderer.HOLSTER);
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new HolsterLayer(renderer));
            }
        }
    }

    private static void registerGui(RegisterGuiLayersEvent event) {
        event.registerAboveAll(CinematicHud.LAYER, CinematicHud::render);
    }

    private static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> ColorGrade.onReload());
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientFinisherManager.INSTANCE.localLocked()) {
            return;
        }
        for (KeyMapping key : mc.options.keyHotbarSlots) {
            while (key.consumeClick()) {
                // swallowed: the weapon stays in hand for the whole sequence
            }
        }
        for (KeyMapping key : new KeyMapping[] {mc.options.keyDrop, mc.options.keySwapOffhand, mc.options.keyInventory,
            mc.options.keyTogglePerspective}) {
            while (key.consumeClick()) {
                // swallowed
            }
        }
    }

    /** Toasts (advancements, recipes) wait until the cinematic hands the screen back. */
    private static final java.util.List<net.minecraft.client.gui.components.toasts.Toast> HELD_TOASTS = new java.util.ArrayList<>();

    private static void onToast(net.neoforged.neoforge.client.event.ToastAddEvent event) {
        if (ClientFinisherManager.INSTANCE.local() != null) {
            HELD_TOASTS.add(event.getToast());
            event.setCanceled(true);
        }
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        RevolverAnimator.tick();
        if (!HELD_TOASTS.isEmpty() && ClientFinisherManager.INSTANCE.local() == null) {
            var toasts = new java.util.ArrayList<>(HELD_TOASTS);
            HELD_TOASTS.clear();
            toasts.forEach(mc.getToasts()::addToast);
        }
        if (mc.level == null) {
            ClientFinisherManager.INSTANCE.clear();
            return;
        }
        if (mc.isPaused() || !mc.level.tickRateManager().runsNormally()) {
            return;
        }
        ClientFinisherManager.INSTANCE.tick();
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFinisherManager.INSTANCE.clear();
        ColorGrade.update(dev.finalframe.client.choreo.Grade.NEUTRAL);
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (ClientFinisherManager.INSTANCE.localLocked()) {
            var in = event.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientFinisherManager.INSTANCE.localLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (ClientFinisherManager.INSTANCE.localLocked()) {
            event.setCanceled(true);
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (CinematicCamera.isDetached()) {
            event.setCanceled(true);
        }
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        if (CinematicCamera.isActive() && event.usedConfiguredFov()) {
            event.setFOV(CinematicCamera.fov());
        }
    }
}

package com.stickyfingers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.client.fx.CameraFx;
import com.stickyfingers.client.gui.AbilityWheelScreen;
import com.stickyfingers.network.StandActionPayload;
import com.stickyfingers.network.UseAbilityPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StickyFingersMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final String[] ORIENT_KEYS = {"auto", "horizontal", "vertical", "diagonal", "anti_diagonal"};

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientState.tick();
        CameraFx.tick();
        if (mc.player == null) return;
        if (mc.screen == null && ClientState.hasStand) {
            while (Keybinds.SUMMON.consumeClick()) PacketDistributor.sendToServer(StandActionPayload.of(StandActionPayload.Action.TOGGLE_STAND));
            while (Keybinds.USE.consumeClick()) {
                PacketDistributor.sendToServer(new UseAbilityPayload(ClientState.selected, mc.player.isShiftKeyDown()));
            }
            while (Keybinds.NETWORK.consumeClick()) PacketDistributor.sendToServer(StandActionPayload.of(StandActionPayload.Action.REQUEST_NETWORK));
            while (Keybinds.CLOSE_ALL.consumeClick()) PacketDistributor.sendToServer(StandActionPayload.of(StandActionPayload.Action.CLOSE_ALL));
            while (Keybinds.ORIENT.consumeClick()) {
                ClientState.orientation = (ClientState.orientation + 1) % ORIENT_KEYS.length;
                PacketDistributor.sendToServer(new StandActionPayload(StandActionPayload.Action.SET_ORIENT, ClientState.orientation, -1));
                mc.player.displayClientMessage(Component.translatable("message.stickyfingers.orientation",
                        Component.translatable("orientation.stickyfingers." + ORIENT_KEYS[ClientState.orientation])).withStyle(ChatFormatting.AQUA), true);
            }
            if (Keybinds.WHEEL.isDown()) mc.setScreen(new AbilityWheelScreen());
        } else {
            // drain clicks so they don't fire when a screen closes
            while (Keybinds.SUMMON.consumeClick()) {}
            while (Keybinds.USE.consumeClick()) {}
            while (Keybinds.NETWORK.consumeClick()) {}
        }
    }

    public static String orientationKey() {
        return ORIENT_KEYS[ClientState.orientation];
    }

    // ---- unzipped entities: stylised distortion instead of anything graphic

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (!ClientState.isUnzipped(e.getId())) return;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float t = (e.tickCount + event.getPartialTick()) * 0.9f;
        float jitter = 0.035f;
        ps.translate(Mth.sin(t * 3.1f) * jitter, Mth.sin(t * 2.3f) * jitter * 0.5f, Mth.cos(t * 2.7f) * jitter);
        float stretch = 1f + Mth.sin(t * 0.7f) * 0.05f;
        ps.scale(1f / stretch, stretch, 1f / stretch);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (ClientState.isUnzipped(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    // ---- zipper body: part of the body is unzipped away

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ClientState.isBody(event.getEntity().getId())) return;
        PlayerModel<?> model = event.getRenderer().getModel();
        boolean phase = (event.getEntity().tickCount / 20) % 2 == 0;
        model.leftArm.visible = !phase;
        model.leftSleeve.visible = !phase;
        model.rightLeg.visible = phase;
        model.rightPants.visible = phase;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        PlayerModel<?> model = event.getRenderer().getModel();
        model.leftArm.visible = true;
        model.leftSleeve.visible = true;
        model.rightLeg.visible = true;
        model.rightPants.visible = true;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        CameraFx.applyAngles(event);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        event.setFOV(event.getFOV() * CameraFx.fovMultiplier((float) event.getPartialTick()));
    }

    private ClientEvents() {}
}

package dev.telephoneorder.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.telephoneorder.ModBlocks;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.ModEntities;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.Identifier;

public class TelephoneOrderClient implements ClientModInitializer {
    public static KeyMapping RELOAD, FIRE_MODE, AIM;
    private static boolean lastAim;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("telephoneorder", "main"));
        RELOAD = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.telephoneorder.reload", InputConstants.KEY_R, cat));
        FIRE_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.telephoneorder.firemode", InputConstants.KEY_B, cat));
        AIM = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.telephoneorder.aim", InputConstants.KEY_LALT, cat));

        EntityRendererRegistry.register(ModEntities.DELIVERY_ACTOR, DeliveryActorRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUN_PROJECTILE, ctx -> new ThrownItemRenderer<>(ctx, 1.5f, true));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientState.tick(mc);
            if (mc.player == null) return;
            while (RELOAD.consumeClick()) if (mc.gui.screen() == null) ClientPlayNetworking.send(new Payloads.Reload());
            while (FIRE_MODE.consumeClick()) if (mc.gui.screen() == null) ClientPlayNetworking.send(new Payloads.FireMode());
            boolean aim = AIM.isDown() && mc.gui.screen() == null;
            if (aim != lastAim) { lastAim = aim; ClientState.aiming = aim; ClientPlayNetworking.send(new Payloads.Aim(aim)); }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> { ClientState.phone = null; ClientState.aiming = false; lastAim = false; ClientState.aimBlend = 0; });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.Recoil.TYPE, (p, ctx) -> ClientState.addRecoil(p.pitch(), p.yaw()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Shake.TYPE, (p, ctx) -> ClientState.addShake(p.strength(), p.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.ScreenFlash.TYPE, (p, ctx) -> { if (ModConfig.get().screenShake && !ModConfig.get().reduceCameraMotion) ClientState.flash(p.rgb(), p.ticks()); });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.HitMarker.TYPE, (p, ctx) -> { ClientState.hitMarkerTicks = 8; ClientState.hitMarkerKind = p.kind(); });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.LockState.TYPE, (p, ctx) -> { ClientState.lockEntity = p.entityId(); ClientState.lockProgress = p.progress(); ClientState.lockLocked = p.locked(); });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Notify.TYPE, (p, ctx) -> ClientState.toast(p.text(), p.color()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PhoneOpen.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            ClientState.phoneModel = p.model();
            mc.gui.setScreen(new PhoneScreen(p.model()));
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PhoneState.TYPE, (p, ctx) -> {
            ClientState.phone = p.data();
            if (Minecraft.getInstance().gui.screen() instanceof PhoneScreen ps) ps.onState(p.data());
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.PhoneClose.TYPE, (p, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof PhoneScreen) mc.gui.setScreen(null);
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.WorkbenchOpen.TYPE, (p, ctx) -> Minecraft.getInstance().gui.setScreen(new WorkbenchScreen()));

        if (System.getProperty("telephoneorder.uishot") != null) UiShot.install();
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> GunOrientCommand.register(dispatcher));
        HudElementRegistry.addLast(ModItems.id("hud"), new GunHud());
        // the gun HUD draws its own crosshair; hide vanilla's while a gun is held
        HudElementRegistry.replaceElement(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR, orig -> (g, dt) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof dev.telephoneorder.weapon.GunItem
                    && dev.telephoneorder.ModConfig.get().hud) return;
            orig.extractRenderState(g, dt);
        });
    }
}

package dev.infinitygauntlet.client.breach;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.breach.ObservationPanelEntity;
import dev.infinitygauntlet.breach.PanelControl;
import dev.infinitygauntlet.network.PanelPressPayload;
import dev.infinitygauntlet.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Aim at a button on a floating panel and click (attack or use) to press it. */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class PanelInteraction {
    private static final double REACH = 5.5;

    private static int hoveredPanel = -1;
    @Nullable
    private static PanelControl hovered;
    private static int flashPanel = -1;
    @Nullable
    private static PanelControl flashControl;
    private static int flashTicks;

    @Nullable
    public static PanelControl hovered(ObservationPanelEntity panel) {
        return hoveredPanel == panel.getId() ? hovered : null;
    }

    /** 0..1 press flash for a button. */
    public static float flash(ObservationPanelEntity panel, PanelControl control) {
        return flashPanel == panel.getId() && flashControl == control ? flashTicks / 6f : 0f;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        hovered = null;
        hoveredPanel = -1;
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (mc.level == null || mc.player == null || !RealityDistortion.inChamber() || mc.screen != null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ObservationPanelEntity panel && panel.position().distanceTo(eye) < REACH + 2) {
                PanelControl c = panel.layout(1f).hit(eye, look, REACH);
                if (c != null) {
                    hovered = c;
                    hoveredPanel = panel.getId();
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (hovered == null || !(event.isAttack() || event.isUseItem())) {
            return;
        }
        PacketDistributor.sendToServer(new PanelPressPayload(hoveredPanel, hovered.ordinal()));
        flashPanel = hoveredPanel;
        flashControl = hovered;
        flashTicks = 6;
        Minecraft.getInstance().player.playSound(ModSounds.PANEL_PRESS.get(), 0.4f, 1.6f);
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    private PanelInteraction() {
    }
}

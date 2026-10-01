package com.infinitemultiverse.client;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.network.TimeStopPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Screen-space presentation of a nearby time stop: a cold tint and vignette that eases in and out.
 * Frozen observers get the full effect; the user sees a lighter tint so they can still act clearly.
 */
public final class ClientTimeStop {
    public static final ResourceLocation LAYER_ID = InfiniteMultiverse.id("time_stop_overlay");
    private static final int EASE_TICKS = 6;
    private static final int TINT = 0x14244F;

    private static boolean active;
    private static boolean localIsOwner;
    private static int ticks;
    private static int fadeOut;
    private static Vec3 center = Vec3.ZERO;
    private static float radius;

    private ClientTimeStop() {
    }

    static void apply(TimeStopPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.active()) {
            active = true;
            ticks = 0;
            center = payload.center();
            radius = payload.radius();
            localIsOwner = minecraft.player != null && minecraft.player.getId() == payload.ownerEntityId();
        } else if (active) {
            active = false;
            fadeOut = EASE_TICKS;
        }
    }

    static void tick() {
        if (active) {
            ticks++;
        } else if (fadeOut > 0) {
            fadeOut--;
        }
    }

    static void reset() {
        active = false;
        fadeOut = 0;
    }

    static void renderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || (!active && fadeOut <= 0)) {
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float intensity = active
                ? Mth.clamp((ticks + partial) / EASE_TICKS, 0f, 1f)
                : Mth.clamp((fadeOut - partial) / EASE_TICKS, 0f, 1f);
        double distance = minecraft.player.position().distanceTo(center);
        intensity *= (float) Mth.clamp(1.5 - distance / Math.max(1.0, radius), 0.0, 1.0);
        if (intensity <= 0.01f) {
            return;
        }
        float strength = localIsOwner ? 0.32f : 0.55f;
        int alpha = (int) (255 * strength * intensity);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, (alpha << 24) | TINT);
        int edge = Math.min(255, alpha * 2) << 24;
        graphics.fillGradient(0, 0, width, height / 4, edge | 0x05091A, 0x0005091A);
        graphics.fillGradient(0, height * 3 / 4, width, height, 0x0005091A, edge | 0x05091A);
    }
}

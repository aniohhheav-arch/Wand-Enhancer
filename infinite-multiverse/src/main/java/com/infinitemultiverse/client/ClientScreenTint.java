package com.infinitemultiverse.client;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.network.ScreenTintPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Timed full-screen colour wash with edge vignette, used by domains and dimensions. */
public final class ClientScreenTint {
    public static final ResourceLocation LAYER_ID = InfiniteMultiverse.id("screen_tint");
    private static final int EASE = 8;

    private static int color;
    private static float strength;
    private static int remaining;
    private static int total;

    private ClientScreenTint() {
    }

    static void apply(ScreenTintPayload payload) {
        if (payload.durationTicks() <= 0) {
            remaining = Math.min(remaining, EASE);
            return;
        }
        color = payload.color() & 0xFFFFFF;
        strength = payload.strength();
        remaining = payload.durationTicks();
        total = payload.durationTicks();
    }

    static void tick() {
        if (remaining > 0) {
            remaining--;
        }
    }

    static void reset() {
        remaining = 0;
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (remaining <= 0) {
            return;
        }
        float elapsed = total - remaining;
        float intensity = Math.min(Mth.clamp(elapsed / EASE, 0f, 1f), Mth.clamp(remaining / (float) EASE, 0f, 1f));
        int alpha = (int) (255 * strength * intensity);
        if (alpha <= 2) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        graphics.fill(0, 0, w, h, (alpha << 24) | color);
        int edge = Math.min(255, alpha * 2) << 24;
        graphics.fillGradient(0, 0, w, h / 4, edge, 0);
        graphics.fillGradient(0, h * 3 / 4, w, h, 0, edge);
    }
}

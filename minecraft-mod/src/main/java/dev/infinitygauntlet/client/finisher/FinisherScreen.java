package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Screen-space layers of a finisher: the stone-coloured grade with a vignette, and full-screen impact frames. */
public final class FinisherScreen {
    public static void grade(GuiGraphics g, DeltaTracker delta) {
        int argb = ClientFinishers.grade(delta.getGameTimeDeltaPartialTick(true));
        if (argb >>> 24 == 0) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float a = (argb >>> 24) / 255f;
        g.fill(0, 0, w, h, argb);
        RenderSystem.enableBlend();
        g.setColor(((argb >> 16) & 255) / 255f * 0.35f, ((argb >> 8) & 255) / 255f * 0.2f, (argb & 255) / 255f * 0.35f, Math.min(1f, a * 2.2f));
        g.blit(ModRenderTypes.VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    public static void impact(GuiGraphics g, DeltaTracker delta) {
        int argb = ClientFinishers.impactFrame(delta.getGameTimeDeltaPartialTick(true));
        if (argb >>> 24 != 0) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb);
        }
    }

    private FinisherScreen() {
    }
}

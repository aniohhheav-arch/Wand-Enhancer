package dev.infinitygauntlet.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.vfx.CameraEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Full-screen cinematic layer: the dimensional travel overlay (swirling smoke that briefly hides the world, then
 * clears to reveal the destination) and colour flashes.
 */
public final class ScreenEffectsLayer {
    private static final ResourceLocation TRANSITION = InfinityGauntlet.id("textures/gui/dimensional_transition.png");

    public static void render(GuiGraphics g, DeltaTracker delta) {
        float pt = delta.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth();
        int h = g.guiHeight();

        float p = CameraEffects.transitionProgress(pt);
        if (p >= 0) {
            // ramp in fast, hold opaque (world hidden) while the teleport resolves, then dissolve outward
            float cover = p < 0.25f ? p / 0.25f : p < 0.5f ? 1f : 1 - (p - 0.5f) / 0.5f;
            cover = Mth.clamp(cover, 0, 1);
            int color = CameraEffects.transitionColor();
            int dark = (int) (cover * cover * 235) << 24 | (color >> 2 & 0x3F3F3F);
            g.fill(0, 0, w, h, dark);
            float size = Math.max(w, h) * (1.6f + (p > 0.5f ? (p - 0.5f) * 2.5f : 0));
            RenderSystem.enableBlend();
            for (int layer = 0; layer < 2; layer++) {
                g.pose().pushPose();
                g.pose().translate(w / 2f, h / 2f, 0);
                g.pose().mulPose(Axis.ZP.rotation((layer == 0 ? 1 : -1.6f) * (p * 6f + layer)));
                float s = size * (layer == 0 ? 1f : 0.8f);
                g.setColor(1, 1, 1, cover * (layer == 0 ? 0.95f : 0.6f));
                g.blit(TRANSITION, (int) (-s / 2), (int) (-s / 2), (int) s, (int) s, 0, 0, 256, 256, 256, 256);
                g.pose().popPose();
            }
            g.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }

        int flash = CameraEffects.flashArgb(pt);
        if (flash >>> 24 != 0) {
            g.fill(0, 0, w, h, flash);
        }
    }

    private ScreenEffectsLayer() {
    }
}

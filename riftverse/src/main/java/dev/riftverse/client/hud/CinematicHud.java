package dev.riftverse.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftverse.client.cinematic.CinematicDirector;
import dev.riftverse.client.cinematic.RealityCinematics;
import dev.riftverse.client.render.Fullscreen;
import dev.riftverse.client.render.RvShaders;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;

/** Everything drawn over the screen during journeys: the wormhole, flashes, letterbox bars and the title card. */
public final class CinematicHud {
    private CinematicHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth();
        int h = g.guiHeight();

        float overlay = CinematicDirector.overlay(partial);
        ShaderInstance sh = RvShaders.wormhole;
        if (overlay > 0.002f && sh != null) {
            g.flush();
            int a = CinematicDirector.colorA;
            int b = CinematicDirector.colorB;
            sh.safeGetUniform("ColorA").set(ColorUtil.r(a), ColorUtil.g(a), ColorUtil.b(a));
            sh.safeGetUniform("ColorB").set(ColorUtil.r(b), ColorUtil.g(b), ColorUtil.b(b));
            float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
            float progress = CinematicDirector.wormholeProgress(partial);
            sh.safeGetUniform("WormParams").set(aspect, progress, CinematicDirector.blackHoleMode() ? 1f : 0f, overlay);
            sh.safeGetUniform("WormExtra").set(0f, CinematicDirector.surgeProgress(partial), CinematicDirector.roll(partial) * Mth.DEG_TO_RAD * 0.5f, 1.7f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            Fullscreen.draw(sh);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        }

        RealityCinematics.renderOverlay(g, partial);

        float flash = CinematicDirector.flash();
        if (flash > 0.01f) {
            int alpha = (int) (Mth.clamp(flash, 0f, 1f) * 255);
            g.fill(0, 0, w, h, (alpha << 24) | (CinematicDirector.flashColor() & 0xFFFFFF));
        }

        float bars = CinematicDirector.letterbox(partial);
        if (bars > 0.002f) {
            int bar = (int) (h * 0.105f * bars);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }

        float titleAlpha = CinematicDirector.titleAlpha(partial);
        String title = CinematicDirector.title();
        if (titleAlpha > 0.03f && title != null) drawTitle(g, mc.font, title, CinematicDirector.subtitle(), CinematicDirector.titleColor(), titleAlpha,
                CinematicDirector.titleTime(partial), w, h);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(5, (int) (Mth.clamp(alpha, 0f, 1f) * 255));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void drawTitle(GuiGraphics g, Font font, String title, String subtitle, int color, float alpha, float time, int w, int h) {
        String upper = title.toUpperCase();
        float spacing = Mth.lerp(Math.min(1f, time / 60f), 7f, 2f);
        float scale = Math.min(3.0f, (w * 0.8f) / Math.max(1f, font.width(upper) + spacing * upper.length()));
        float total = 0f;
        for (int i = 0; i < upper.length(); i++) total += font.width(String.valueOf(upper.charAt(i))) + spacing;
        float cy = h * 0.6f;

        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0f);
        g.pose().scale(scale, scale, 1f);
        float x = -total / 2f;
        int glow = ColorUtil.lerp(color, 0xFFFFFF, 0.65f);
        for (int i = 0; i < upper.length(); i++) {
            String ch = String.valueOf(upper.charAt(i));
            float reveal = Mth.clamp((time - i * 1.2f) / 10f, 0f, 1f);
            if (reveal > 0f) g.drawString(font, ch, (int) x, (int) ((1f - reveal) * 6f), argb(alpha * reveal, glow), true);
            x += font.width(ch) + spacing;
        }
        g.pose().popPose();

        float lineW = Math.min(w * 0.5f, total * scale) * Mth.clamp(time / 30f, 0f, 1f);
        int lineY = (int) (cy + 10 * scale + 4);
        g.fill((int) (w / 2f - lineW / 2f), lineY, (int) (w / 2f + lineW / 2f), lineY + 1, argb(alpha * 0.9f, color));

        if (!subtitle.isEmpty()) {
            float subAlpha = alpha * Mth.clamp((time - 20f) / 20f, 0f, 1f);
            if (subAlpha > 0.03f) {
                g.pose().pushPose();
                g.pose().translate(w / 2f, lineY + 6, 0f);
                float s = Math.min(1.25f, (w * 0.9f) / Math.max(1, font.width(subtitle)));
                g.pose().scale(s, s, 1f);
                g.drawString(font, subtitle, -font.width(subtitle) / 2, 0, argb(subAlpha, 0xDDDDEE), true);
                g.pose().popPose();
            }
        }
    }
}

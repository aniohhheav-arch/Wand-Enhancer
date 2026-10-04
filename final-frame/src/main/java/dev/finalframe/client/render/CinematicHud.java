package dev.finalframe.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import dev.finalframe.client.choreo.Grade;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.FinisherRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/** Letterbox bars, the title card, the shader-less fallback vignette and the activation prompt. */
public final class CinematicHud {
    public static final ResourceLocation LAYER = FinalFrame.id("cinematic");
    private static final ResourceLocation VIGNETTE = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    private static final int GOLD = 0xE8B04A;

    private CinematicHud() {
    }

    /** While the cinematic owns the screen, every other HUD layer stays hidden. */
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(LAYER) && hudHidden()) {
            event.setCanceled(true);
        }
    }

    private static boolean hudHidden() {
        ClientSession s = ClientFinisherManager.INSTANCE.local();
        if (s == null) {
            return false;
        }
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        return s.choreography().grade(s, s.time(pt)).letterbox() > 0.02f && !s.isAborting();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        ClientSession s = ClientFinisherManager.INSTANCE.local();
        if (s == null) {
            renderPrompt(g, mc);
            return;
        }
        float pt = delta.getGameTimeDeltaPartialTick(false);
        Grade grade = s.choreography().grade(s, s.time(pt));
        float fade = s.isAborting() ? 1f - s.abortProgress(pt) : 1f;
        int w = g.guiWidth();
        int h = g.guiHeight();

        if (!ColorGrade.isShaderActive() && grade.vignette() > 0) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
            float v = grade.vignette() * fade;
            g.setColor(v, v, v, 1f);
            g.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            if (grade.flash() > 0) {
                g.fill(0, 0, w, h, FastColor.ARGB32.color((int) (grade.flash() * fade * 200), 255, 246, 220));
            }
        }

        if (FFConfig.get(FFConfig.LETTERBOX) && grade.letterbox() > 0) {
            int bar = Math.round(h * 0.115f * grade.letterbox() * fade);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }

        if (FFConfig.get(FFConfig.TITLE_CARD) && grade.title() > 0.02f) {
            renderTitle(g, mc.font, w, h, grade.title() * fade);
        }
    }

    private static void renderTitle(GuiGraphics g, Font font, int w, int h, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 5, 255);
        String main = Component.translatable("finalframe.title.main").getString();
        float scale = 3.0f;
        int spacing = 3;
        int width = 0;
        for (char c : main.toCharArray()) {
            width += font.width(String.valueOf(c)) + spacing;
        }
        width -= spacing;
        float rise = (1 - alpha) * 6f;
        g.pose().pushPose();
        g.pose().translate(w / 2f - width * scale / 2f, h * 0.62f + rise, 0);
        g.pose().scale(scale, scale, 1f);
        int x = 0;
        for (char c : main.toCharArray()) {
            String ch = String.valueOf(c);
            g.drawString(font, ch, x + 1, 1, FastColor.ARGB32.color(a / 2, 0, 0, 0), false);
            g.drawString(font, ch, x, 0, FastColor.ARGB32.color(a, (GOLD >> 16) & 0xFF, (GOLD >> 8) & 0xFF, GOLD & 0xFF), false);
            x += font.width(ch) + spacing;
        }
        g.pose().popPose();
        int lineW = (int) (width * scale * 0.55f * alpha);
        int y = (int) (h * 0.62f + 9 * scale + 6 + rise);
        g.fill(w / 2 - lineW / 2, y, w / 2 + lineW / 2, y + 1, FastColor.ARGB32.color((int) (a * 0.8f), 232, 176, 74));
        Component sub = Component.translatable("finalframe.title.sub");
        g.drawCenteredString(font, sub, w / 2, y + 5, FastColor.ARGB32.color(a, 220, 210, 190));
    }

    private static void renderPrompt(GuiGraphics g, Minecraft mc) {
        if (!FFConfig.get(FFConfig.SHOW_PROMPT) || mc.player == null || mc.screen != null) {
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        FinisherRules.Verdict v = FinisherRules.check(mc.player, target, mc.player.getMainHandItem());
        String key = switch (v) {
            case OK -> FFConfig.get(FFConfig.REQUIRE_SNEAK) ? "finalframe.prompt.ready_sneak" : "finalframe.prompt.ready";
            case NEEDS_SNEAK -> "finalframe.prompt.needs_sneak";
            case NOT_BEHIND -> "finalframe.prompt.not_behind";
            case TOO_FAR -> "finalframe.prompt.too_far";
            case COOLDOWN -> "finalframe.prompt.cooldown";
            default -> null;
        };
        if (key == null) {
            return;
        }
        boolean ready = v == FinisherRules.Verdict.OK;
        int color = ready ? 0xFFE8B04A : 0xFFB8B0A0;
        Component text = Component.literal(ready ? "◆ " : "◇ ").append(Component.translatable(key));
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 14;
        int tw = mc.font.width(text);
        g.fill(x - tw / 2 - 4, y - 3, x + tw / 2 + 4, y + 11, 0x88000000);
        g.drawCenteredString(mc.font, text, x, y, color);
    }
}

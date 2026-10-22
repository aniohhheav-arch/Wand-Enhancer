package com.stickyfingers.client.gui;

import com.stickyfingers.ability.AbilityType;
import com.stickyfingers.client.ClientEvents;
import com.stickyfingers.client.ClientState;
import com.stickyfingers.client.Keybinds;
import com.stickyfingers.config.SFConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Bottom-right Stand HUD: selected ability with cooldown sweep, energy bar and key hints. */
public final class HudOverlay {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientState.hasStand) return;
        if (!SFConfig.safe(SFConfig.SHOW_HUD::get, true) || mc.screen instanceof AbilityWheelScreen) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        AbilityType sel = ClientState.selectedAbility();

        int x = w - 156, y = h - 50;
        // panel
        g.fill(x - 4, y - 4, w - 6, h - 6, 0x8A0E0A24);
        g.fill(x - 4, y - 4, w - 6, y - 3, 0xFFE8A93B);

        // icon with cooldown
        UiDraw.abilityIcon(g, sel, x, y, 32, ClientState.cooldown(sel), SFConfig.cooldown(sel));
        int tx = x + 38;
        g.drawString(font, sel.displayName(), tx, y + 1, 0xFFFFFFFF, true);
        int cd = ClientState.cooldown(sel);
        Component status = cd > 0
                ? Component.literal(String.format("%.1fs", cd / 20f)).withColor(0xFFFF6B6B)
                : Component.translatable("hud.stickyfingers.ready").withColor(0xFF7FFFD4);
        g.drawString(font, status, tx, y + 12, 0xFFFFFFFF, true);
        g.drawString(font, Component.literal("⚡" + SFConfig.cost(sel)).withColor(0xFF8FD3FF), tx + 44, y + 12, 0xFFFFFFFF, true);

        // energy bar
        float max = (float) (double) SFConfig.safe(SFConfig.MAX_ENERGY::get, 100.0);
        float frac = Math.max(0, Math.min(1, ClientState.displayEnergy / max));
        int bx = tx, by = y + 24, bw = 86, bh = 6;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fillGradient(bx, by, bx + (int) (bw * frac), by + bh, 0xFF8F7BFF, 0xFF3B2BC4);
        for (int i = 1; i < 5; i++) g.fill(bx + bw * i / 5, by, bx + bw * i / 5 + 1, by + bh, 0x55000000);
        g.drawString(font, Component.literal((int) ClientState.displayEnergy + ""), bx + bw + 3, by - 1, 0xFFB9A8FF, true);

        // hints
        String hint = Keybinds.WHEEL.getTranslatedKeyMessage().getString() + " wheel · " + Keybinds.USE.getTranslatedKeyMessage().getString()
                + " use · " + Keybinds.ORIENT.getTranslatedKeyMessage().getString() + " " + Component.translatable("orientation.stickyfingers." + ClientEvents.orientationKey()).getString();
        g.pose().pushPose();
        g.pose().translate(x - 2, y - 13, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(font, hint, 0, 0, 0xFFAEB7CF, true);
        g.pose().popPose();

        if (ClientState.hidden) {
            Component text = Component.translatable("hud.stickyfingers.hidden");
            g.drawCenteredString(font, text, w / 2, h / 2 + 20, 0xFFB9A8FF);
        }

        // Time Stop cinematic banner
        if (ClientState.timeStopActive) {
            int cx = w / 2;
            int cy = 28;
            // "ZA WARUDO" title
            Component zaWarudo = Component.literal("ZA WARUDO").withColor(0xFF8FD3FF);
            int tw = font.width(zaWarudo);
            // dark backing pill
            g.fill(cx - tw / 2 - 8, cy - 3, cx + tw / 2 + 8, cy + 10, 0xCC0D0924);
            g.fill(cx - tw / 2 - 8, cy - 4, cx + tw / 2 + 8, cy - 3, 0xFF4A6BC8);
            g.drawCenteredString(font, zaWarudo, cx, cy, 0xFF8FD3FF);

            // "TIME STOPPED" subtitle with remaining ticks
            int rem = ClientState.timeStopTicks;
            String timeText = rem > 0
                    ? String.format("TIME STOPPED  %.1fs", rem / 20f)
                    : "TIME STOPPED";
            Component sub = Component.literal(timeText).withColor(0xFFB9D8FF);
            int sw = font.width(sub);
            g.fill(cx - sw / 2 - 6, cy + 12, cx + sw / 2 + 6, cy + 22, 0x99000000);
            g.pose().pushPose();
            g.pose().translate(cx - sw * 0.5f * 0.8f, cy + 12, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, sub, 0, 0, 0xFFB9D8FF, false);
            g.pose().popPose();
        }
    }

    private HudOverlay() {}
}

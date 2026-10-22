package com.stickyfingers.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stickyfingers.ability.AbilityType;
import com.stickyfingers.ability.StandType;
import com.stickyfingers.client.ClientState;
import com.stickyfingers.client.Keybinds;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.network.StandActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

/**
 * Hold-to-open radial ability wheel. Inner ring: the eleven signature abilities; outer ring: advanced techniques.
 * Release the key (or click) over a slot to select it. Shows only abilities for the player's active Stand.
 */
public class AbilityWheelScreen extends net.minecraft.client.gui.screens.Screen {
    private final StandType standType = ClientState.standType != null ? ClientState.standType : StandType.STICKY_FINGERS;
    private final List<AbilityType> INNER = Arrays.stream(AbilityType.VALUES)
            .filter(t -> t.stand == standType && t.primary).toList();
    private final List<AbilityType> OUTER = Arrays.stream(AbilityType.VALUES)
            .filter(t -> t.stand == standType && !t.primary).toList();
    private static final int INNER_R = 72, OUTER_R = 128, SLOT = 26;

    private AbilityType hovered;
    private float openAnim;

    public AbilityWheelScreen() {
        super(Component.translatable("screen.stickyfingers.wheel"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        openAnim = Math.min(1f, openAnim + 0.25f);
        InputConstants.Key key = Keybinds.WHEEL.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean down = key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == 1
                : InputConstants.isKeyDown(window, key.getValue());
        if (!down) confirm();
    }

    private void confirm() {
        if (hovered != null) {
            PacketDistributor.sendToServer(new StandActionPayload(StandActionPayload.Action.SELECT, hovered.ordinal(), -1));
            ClientState.selected = hovered.ordinal();
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        confirm();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0x90080414, 0xB0100830);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2, cy = height / 2;
        float scale = Mth.lerp(Math.min(1f, openAnim + partial * 0.25f), 0.6f, 1f);
        double dx = mouseX - cx, dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);
        double ang = Math.atan2(dy, dx);
        hovered = null;
        if (dist > 30) {
            List<AbilityType> ring = dist < (INNER_R + OUTER_R) / 2.0 ? INNER : OUTER;
            int idx = (int) Math.round(norm(ang + Math.PI / 2) / (Math.PI * 2) * ring.size()) % ring.size();
            hovered = ring.get(idx);
        }

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        ring(g, 0, 0, INNER_R + 18, 0x40FFFFFF);
        ring(g, 0, 0, OUTER_R + 18, 0x30FFFFFF);
        drawRing(g, INNER, INNER_R);
        drawRing(g, OUTER, OUTER_R);
        g.pose().popPose();

        // centre info panel
        AbilityType info = hovered != null ? hovered : ClientState.selectedAbility();
        int pw = 108;
        g.fill(cx - pw / 2, cy - 34, cx + pw / 2, cy + 46, 0xD0120A26);
        g.fill(cx - pw / 2, cy - 34, cx + pw / 2, cy - 33, 0xFFE8A93B);
        g.drawCenteredString(font, info.displayName(), cx, cy - 30, 0xFFFFFFFF);
        int cd = ClientState.cooldown(info);
        String stats = "⚡" + SFConfig.cost(info) + "  ⌛" + String.format("%.1fs", SFConfig.cooldown(info) / 20f);
        g.drawCenteredString(font, stats, cx, cy - 20, cd > 0 ? 0xFFFF6B6B : 0xFF8FD3FF);
        List<FormattedCharSequence> lines = font.split(info.description(), pw - 8);
        g.pose().pushPose();
        g.pose().translate(cx, cy - 8, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        for (int i = 0; i < Math.min(lines.size(), 6); i++) {
            g.drawCenteredString(font, lines.get(i), 0, i * 10, 0xFFCBD3EA);
        }
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("screen.stickyfingers.wheel.hint"), cx, height - 20, 0xFF9AA3C0);
    }

    private void drawRing(GuiGraphics g, List<AbilityType> ring, int radius) {
        for (int i = 0; i < ring.size(); i++) {
            AbilityType t = ring.get(i);
            double a = -Math.PI / 2 + i * Math.PI * 2 / ring.size();
            int x = (int) (Math.cos(a) * radius) - SLOT / 2;
            int y = (int) (Math.sin(a) * radius) - SLOT / 2;
            boolean sel = t.ordinal() == ClientState.selected;
            boolean hov = t == hovered;
            int border = hov ? 0xFFFFFFFF : sel ? 0xFFE8A93B : 0xFF3B2BC4;
            int pad = hov ? 4 : 2;
            g.fill(x - pad, y - pad, x + SLOT + pad, y + SLOT + pad, border);
            g.fill(x - pad + 1, y - pad + 1, x + SLOT + pad - 1, y + SLOT + pad - 1, 0xFF120A26);
            UiDraw.abilityIcon(g, t, x, y, SLOT, ClientState.cooldown(t), SFConfig.cooldown(t));
            if (!SFConfig.enabled(t)) g.fill(x, y, x + SLOT, y + SLOT, 0xC0300000);
            if (ClientState.energy < SFConfig.cost(t)) g.fill(x, y + SLOT - 3, x + SLOT, y + SLOT, 0xFF6B4BFF);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI * 2 / 96;
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, i % 2 == 0 ? color : 0x40E8A93B);
        }
    }

    private static double norm(double a) {
        a %= Math.PI * 2;
        return a < 0 ? a + Math.PI * 2 : a;
    }
}

package com.wandcraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class PlayerHint {
    private PlayerHint() {}

    static void draw(GuiGraphics g, Font font, int w, int h) {
        String s = "Release to select  ·  Scroll / ← → change school  ·  [B] Spellbook";
        g.drawCenteredString(font, s, w / 2, 4, 0xFF8890A0);
    }
}

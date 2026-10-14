package com.stickyfingers.client.gui;

import com.stickyfingers.ability.AbilityType;
import net.minecraft.client.gui.GuiGraphics;

final class UiDraw {
    /** Icon with a radial-ish cooldown shade that drains top-down. */
    static void abilityIcon(GuiGraphics g, AbilityType type, int x, int y, int size, int cooldown, int maxCooldown) {
        g.blit(type.icon(), x, y, 0, 0, size, size, size, size);
        if (cooldown > 0 && maxCooldown > 0) {
            float f = Math.min(1f, cooldown / (float) maxCooldown);
            int shade = (int) (size * f);
            g.fill(x, y + size - shade, x + size, y + size, 0xB0000000);
            g.fill(x, y + size - shade, x + size, y + size - shade + 1, 0xFFE8A93B);
        }
    }

    private UiDraw() {}
}

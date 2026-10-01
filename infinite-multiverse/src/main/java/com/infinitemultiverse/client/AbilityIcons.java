package com.infinitemultiverse.client;

import com.infinitemultiverse.core.ability.Ability;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

public final class AbilityIcons {
    private static final int TEXTURE_SIZE = 64;

    private AbilityIcons() {
    }

    /** Draws the ability's 64px icon scaled down to {@code size} GUI pixels. */
    public static void draw(GuiGraphics graphics, Ability ability, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ability.iconTexture(), x, y, size, size, 0f, 0f, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        RenderSystem.disableBlend();
    }
}

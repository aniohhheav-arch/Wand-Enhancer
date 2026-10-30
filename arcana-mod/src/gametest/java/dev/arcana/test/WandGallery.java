package dev.arcana.test;

import dev.arcana.item.WandItem;
import dev.arcana.magic.School;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Dev-only: draws every wand large so the 3D models can be inspected in one screenshot. */
public class WandGallery extends Screen {
    private final int stage;

    public WandGallery(int stage) {
        super(Component.literal("gallery"));
        this.stage = stage;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF14101F);
        School[] all = School.values();
        for (int i = 0; i < all.length; i++) {
            ItemStack s = new ItemStack(ModItems.WANDS.get(all[i]).get());
            s.set(ModComponents.STAGE.get(), stage);
            int col = i % 5, row = i / 5;
            int x = 40 + col * (width - 80) / 5, y = 10 + row * (height / 2);
            g.pose().pushPose();
            g.pose().translate(x + 22, y + 55, 0);
            g.pose().scale(6.5f, 6.5f, 1f);
            g.renderItem(s, -8, -8);
            g.pose().popPose();
            g.drawCenteredString(font, all[i].id, x + 22, y + height / 2 - 18, 0xFFFFFFFF);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
}

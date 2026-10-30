package dev.infinitygauntlet.client.gui;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.client.ClientCooldowns;
import dev.infinitygauntlet.client.HeldGauntlet;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * HUD shown while a gauntlet is held: the six-stone energy display (installed stones lit, selected one pulsing),
 * the selected ability with its cooldown bar, and neighbouring abilities for quick cycling.
 */
public final class AbilityHud {
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || ClientConfig.SPEC.isLoaded() && !ClientConfig.SHOW_HUD.get()) {
            return;
        }
        ItemStack stack = HeldGauntlet.find(mc.player);
        if (stack.isEmpty()) {
            return;
        }
        GauntletState state = InfinityGauntletItem.state(stack);
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float time = mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true);

        // six-stone energy display
        int x0 = w - 8 - 6 * 14;
        int y0 = h - 58;
        g.fill(x0 - 4, y0 - 4, w - 4, y0 + 16, 0x80000000);
        for (InfinityStone stone : InfinityStone.values()) {
            int x = x0 + stone.ordinal() * 14;
            ResourceLocation icon = InfinityGauntlet.id("textures/item/" + stone.getSerializedName() + "_stone.png");
            boolean installed = state.has(stone);
            boolean selected = installed && stone == state.selected();
            if (selected) {
                int pulse = (int) (90 + 70 * Mth.sin(time * 0.25f));
                g.fill(x - 2, y0 - 2, x + 14, y0 + 14, pulse << 24 | stone.color());
            }
            float a = installed ? 1f : 0.18f;
            g.setColor(1, 1, 1, a);
            g.blit(icon, x, y0, 0, 0, 12, 12, 12, 12);
            g.setColor(1, 1, 1, 1);
        }

        Ability ability = AbilityCaster.selectedAbility(state);
        int y = y0 - 26;
        if (ability == null) {
            Component msg = Component.translatable(state.stones().isEmpty() ? "hud.infinitygauntlet.empty" : "hud.infinitygauntlet.dormant",
                    state.selected().displayName());
            g.drawString(font, msg, w - 8 - font.width(msg), y + 8, 0xFFAAAAAA);
            return;
        }
        InfinityStone stone = ability.stone();
        List<Ability> list = Abilities.forStone(stone);
        int index = list.indexOf(ability);
        Component name = ability.displayName().copy().withStyle(stone.format());
        int nameW = font.width(name);
        int boxW = Math.max(nameW, 110) + 12;
        int bx = w - 4 - boxW;
        g.fill(bx, y - 2, w - 4, y + 20, 0x90000000);
        g.fill(bx, y - 2, bx + 2, y + 20, 0xFF000000 | stone.color());
        g.drawString(font, name, w - 8 - nameW, y + 1, 0xFFFFFFFF);
        Component counter = Component.literal((index + 1) + "/" + list.size());
        g.drawString(font, counter, bx + 6, y + 1, 0xFF888888);

        float cd = ClientCooldowns.fraction(ability.id());
        int barX = bx + 6;
        int barW = boxW - 12;
        g.fill(barX, y + 13, barX + barW, y + 16, 0xFF202020);
        if (cd > 0) {
            g.fill(barX, y + 13, barX + (int) (barW * (1 - cd)), y + 16, 0xFF000000 | darker(stone.color()));
            String secs = String.format("%.1fs", ClientCooldowns.remaining(ability.id()) / 20f);
            g.drawString(font, secs, barX + barW - font.width(secs), y + 1, 0xFFFF6666);
        } else {
            int glow = (int) (180 + 75 * Mth.sin(time * 0.3f));
            g.fill(barX, y + 13, barX + barW, y + 16, glow << 24 | stone.highlight());
        }
    }

    private static int darker(int rgb) {
        return (rgb >> 1) & 0x7F7F7F;
    }

    private AbilityHud() {
    }
}

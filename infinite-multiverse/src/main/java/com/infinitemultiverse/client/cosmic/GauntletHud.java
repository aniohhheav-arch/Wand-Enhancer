package com.infinitemultiverse.client.cosmic;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.client.AbilityIcons;
import com.infinitemultiverse.client.ClientMultiverseState;
import com.infinitemultiverse.client.cinematic.SceneManager;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.network.GauntletScrollPayload;
import com.infinitemultiverse.cosmic.gauntlet.Gauntlet;
import com.infinitemultiverse.cosmic.gauntlet.InfinityStone;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Holding the Infinity Gauntlet: the mouse wheel cycles stones (sneak + wheel cycles that stone's techniques) and a
 * selector above the hotbar shows the six sockets, the selected page's techniques and their cooldowns.
 */
@EventBusSubscriber(modid = InfiniteMultiverse.MOD_ID, value = Dist.CLIENT)
public final class GauntletHud {
    public static final ResourceLocation ID = InfiniteMultiverse.id("gauntlet_hud");
    private static float flash;

    private GauntletHud() {
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || Gauntlet.held(mc.player).isEmpty() || event.getScrollDeltaY() == 0) {
            return;
        }
        int delta = event.getScrollDeltaY() > 0 ? -1 : 1;
        boolean sneak = mc.player.isShiftKeyDown();
        PacketDistributor.sendToServer(new GauntletScrollPayload(sneak ? 0 : delta, sneak ? delta : 0));
        flash = 1f;
        event.setCanceled(true);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || SceneManager.inCutscene()) {
            return;
        }
        ItemStack gauntlet = Gauntlet.held(mc.player);
        if (gauntlet.isEmpty()) {
            return;
        }
        flash = Math.max(0, flash - 0.02f);
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() - 92;
        int page = Gauntlet.page(gauntlet);
        float time = (mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20f;

        // sockets
        int spacing = 18;
        int left = cx - spacing * 7 / 2 + spacing / 2;
        g.fill(left - 12, y - 11, left + spacing * 7 - 6, y + 11, 0x90080B14);
        for (int i = 0; i <= Gauntlet.ALL_PAGE; i++) {
            int x = left + i * spacing;
            boolean selected = i == page;
            if (i < 6) {
                InfinityStone stone = InfinityStone.values()[i];
                boolean set = Gauntlet.has(gauntlet, stone);
                gem(g, x, y, selected ? 7 : 5, set ? stone.color() : 0x2A2F3A, set ? (selected ? 1f : 0.75f) : 0.4f, time + i);
            } else {
                boolean ok = Gauntlet.pageAvailable(gauntlet, i);
                g.drawCenteredString(font, "∞", x, y - 4, ok ? (selected ? 0xFFFFE27A : 0xFFB0A060) : 0xFF404040);
            }
            if (selected) {
                g.renderOutline(x - 9, y - 9, 18, 18, 0xC0FFFFFF);
            }
        }
        Component title = page < 6 ? InfinityStone.values()[page].displayName() : Component.translatable("stone.infinitemultiverse.all");
        int titleColor = page < 6 ? InfinityStone.values()[page].color() : 0xFFE27A;
        g.drawCenteredString(font, title.getString().toUpperCase(Locale.ROOT), cx, y - 22, 0xFF000000 | titleColor);

        // techniques of the selected page
        if (!Gauntlet.pageAvailable(gauntlet, page)) {
            g.drawCenteredString(font, Component.translatable("message.infinitemultiverse.gauntlet_empty"), cx, y + 16, 0xFF909090);
            return;
        }
        List<Supplier<? extends Ability>> abilities = Gauntlet.pageAbilities(page);
        int slot = Math.min(Gauntlet.slot(gauntlet), abilities.size() - 1);
        int rowW = 118;
        int rx = cx - rowW * abilities.size() / 2;
        for (int i = 0; i < abilities.size(); i++) {
            Ability a = abilities.get(i).get();
            int x = rx + i * rowW;
            boolean sel = i == slot;
            g.fill(x + 2, y + 14, x + rowW - 2, y + 32, sel ? 0xC0201A30 : 0x80080B14);
            if (sel) {
                int pulse = (int) (150 + 100 * Math.sin(time * 6));
                g.renderOutline(x + 2, y + 14, rowW - 4, 18, (pulse << 24) | (titleColor & 0xFFFFFF));
            }
            AbilityIcons.draw(g, a, x + 4, y + 15, 16);
            int cd = ClientMultiverseState.cooldown(a.id());
            String cdText = cd > 0 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(cd / 20f)) : "";
            String name = font.plainSubstrByWidth(a.displayName().getString(), rowW - 28 - (cd > 0 ? font.width(cdText) + 3 : 0));
            g.drawString(font, name, x + 23, y + 19, cd > 0 ? 0xFF707888 : sel ? 0xFFFFFFFF : 0xFFA8B4C8, false);
            if (cd > 0) {
                g.drawString(font, cdText, x + rowW - 4 - font.width(cdText), y + 19, 0xFFFF8080, false);
            }
        }
        if (flash > 0) {
            g.drawCenteredString(font, Component.translatable("hud.infinitemultiverse.gauntlet_hint"), cx, y + 36, ((int) (Mth.clamp(flash * 2, 0, 1) * 200) << 24) | 0xC0C8D8);
        }
    }

    private static void gem(GuiGraphics g, int x, int y, int r, int color, float bright, float t) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy) * (1 - Math.abs(dy) / (float) (r * 2)));
            float shade = 1 - (dy + r) / (float) (2 * r) * 0.5f;
            int c = scale(color, bright * shade);
            g.fill(x - w, y + dy, x + w + 1, y + dy + 1, 0xFF000000 | c);
        }
        g.fill(x - r / 3, y - r / 2, x - r / 3 + 2, y - r / 2 + 2, 0xC0FFFFFF);
    }

    private static int scale(int c, float k) {
        int r = Mth.clamp((int) ((c >> 16 & 255) * k), 0, 255), gg = Mth.clamp((int) ((c >> 8 & 255) * k), 0, 255), b = Mth.clamp((int) ((c & 255) * k), 0, 255);
        return r << 16 | gg << 8 | b;
    }
}

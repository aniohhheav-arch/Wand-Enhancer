package dev.mysticarts.client.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.mysticarts.MaClientConfig;
import dev.mysticarts.MysticArts;
import dev.mysticarts.client.AbilityInfo;
import dev.mysticarts.client.ClientFx;
import dev.mysticarts.client.ClientPower;
import dev.mysticarts.client.KeyBindings;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.PowerManager;
import dev.mysticarts.power.Source;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Energy, ultimate charge, socketed stones, the selected spell and its cooldown, and active-effect chips. */
public final class MysticHud {
    private static final ResourceLocation GLOW = MysticArts.id("textures/gui/radial_glow.png");
    private static float shownMystic = -1, shownCosmic = -1;

    private MysticHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientPower.ready() || mc.player.isSpectator() && !ClientPower.has(Payloads.PowerSync.F_ASTRAL)) return;
        if (ClientFx.snapActive() && ClientFx.snapProgress(delta.getGameTimeDeltaPartialTick(true)) > 0.3f) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + delta.getGameTimeDeltaPartialTick(true);
        boolean minimal = MaClientConfig.MINIMAL_HUD.get();
        boolean gauntlet = !InfinityGauntletItem.held(mc.player).isEmpty();
        Source src = KeyBindings.currentSource();
        Ability sel = ClientPower.selected(src);

        float mystic = ClientPower.mystic(), cosmic = ClientPower.cosmic();
        shownMystic = shownMystic < 0 ? mystic : Mth.lerp(0.25f, shownMystic, mystic);
        shownCosmic = shownCosmic < 0 ? cosmic : Mth.lerp(0.25f, shownCosmic, cosmic);
        boolean showCosmic = gauntlet || cosmic < ClientPower.maxCosmic() - 0.5f;

        // energy panel, bottom left
        int x = 8, barW = minimal ? 70 : 96;
        int y = h - (minimal ? 18 : 30) - (showCosmic ? 9 : 0) - (gauntlet && !minimal ? 14 : 0);
        if (!minimal) g.fill(x - 4, y - 4, x + barW + 44, h - 4, 0x66000000);
        bar(g, font, x, y, barW, shownMystic, ClientPower.maxMystic(), Source.MYSTIC.color, 0xFFD27A, minimal ? null : Component.translatable("energy.mysticarts.mystic"), time);
        y += 9;
        if (showCosmic) {
            bar(g, font, x, y, barW, shownCosmic, ClientPower.maxCosmic(), 0x9B2CFF, 0xFFC94D, minimal ? null : Component.translatable("energy.mysticarts.cosmic"), time);
            y += 9;
        }
        float ult = ClientPower.ultimate() / PowerManager.ULTIMATE_MAX;
        int uw = (int) (barW * ult);
        g.fill(x, y, x + barW, y + 3, 0xAA101010);
        int ultCol = ult >= 1f ? ColorUtil.lerp(0xFFD27A, 0xFFFFFF, 0.5f + 0.5f * Mth.sin(time * 0.3f)) : 0xC8A050;
        g.fill(x, y, x + uw, y + 3, 0xFF000000 | ultCol);
        if (ult >= 1f && !minimal) g.drawString(font, Component.translatable("hud.mysticarts.ultimate_ready", KeyBindings.ULTIMATE.getTranslatedKeyMessage()), x + barW + 4, y - 3, ultCol);
        y += 6;
        if (gauntlet && !minimal) {
            int stones = InfinityGauntletItem.heldStones(mc.player);
            for (int i = 0; i < 6; i++) {
                Source s = Source.STONES[i];
                boolean in = (stones & s.bit()) != 0;
                int sx = x + i * 14;
                if (s == src) g.fill(sx - 1, y - 1, sx + 12, y + 12, 0xFF000000 | s.color);
                RenderSystem.setShaderColor(in ? 1f : 0.25f, in ? 1f : 0.25f, in ? 1f : 0.25f, 1f);
                g.blit(MysticArts.id("textures/gui/source/" + s.id + ".png"), sx, y, 11, 11, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            if ((stones & Source.SOUL.bit()) != 0) g.drawString(font, Component.translatable("hud.mysticarts.souls", ClientPower.souls()), x + 88, y + 2, Source.SOUL.color);
        }

        // selected spell, right of the hotbar
        int ix = w / 2 + 98, iy = h - 26, size = minimal ? 16 : 22;
        if (minimal) iy = h - 20;
        boolean ready = AbilityInfo.ready(sel);
        float flash = ClientPower.READY_FLASH[sel.ordinal()];
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(ColorUtil.r(sel.color()), ColorUtil.g(sel.color()), ColorUtil.b(sel.color()), (ready ? 0.45f : 0.15f) + flash * 0.8f);
        g.blit(GLOW, ix - size / 2, iy - size / 2, size * 2, size * 2, 0, 0, 64, 64, 64, 64);
        RenderSystem.defaultBlendFunc();
        float dim = AbilityInfo.locked(sel) != null ? 0.35f : 1f;
        RenderSystem.setShaderColor(dim, dim, dim, 1f);
        g.blit(AbilityInfo.icon(sel), ix, iy, size, size, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        float cd = ClientPower.cooldownFraction(sel);
        if (cd > 0) {
            g.fill(ix, iy + (int) (size * (1 - cd)), ix + size, iy + size, 0xAA000000);
            g.drawCenteredString(font, String.format("%.0f", Math.ceil(ClientPower.cooldown(sel) / 20f)), ix + size / 2, iy + size / 2 - 4, 0xFFFFFF);
        }
        if (!minimal) {
            g.drawString(font, Component.translatable(sel.translationKey()).withColor(sel.color()), ix + size + 4, iy + 2, 0xFFFFFF);
            g.drawString(font, Component.literal("[").append(KeyBindings.CAST.getTranslatedKeyMessage()).append("]"), ix + size + 4, iy + 12, 0x909090);
        }

        // charging and wind-ups, above the crosshair
        int charge = ClientPower.charging();
        if (charge >= 0) {
            float k = Math.min(1f, charge / (float) PowerManager.MAX_CHARGE_TICKS);
            int cw = 64, cx = w / 2 - cw / 2, cy = h / 2 + 14;
            g.fill(cx - 1, cy - 1, cx + cw + 1, cy + 5, 0x88000000);
            g.fill(cx, cy, cx + (int) (cw * k), cy + 4, 0xFF000000 | ColorUtil.lerp(sel.color(), 0xFFFFFF, k * 0.5f));
        }
        int windup = ClientPower.windupAbility();
        if (windup >= 0) {
            Ability a = Ability.byId(windup);
            g.drawCenteredString(font, Component.translatable(a.translationKey()).withColor(a.color()), w / 2, h / 2 - 30, 0xFFFFFF);
        }

        // active effect chips, top left
        if (!minimal) {
            int cy = 6;
            cy = chip(g, font, Payloads.PowerSync.F_SHIELD, "hud.mysticarts.shield", Ability.ELDRITCH_SHIELD.color(), cy);
            cy = chip(g, font, Payloads.PowerSync.F_ABSORB, "hud.mysticarts.absorbing", Ability.ENERGY_ABSORPTION.color(), cy);
            cy = chip(g, font, Payloads.PowerSync.F_KINETIC, "hud.mysticarts.kinetic", Source.POWER.color, cy);
            cy = chip(g, font, Payloads.PowerSync.F_DEFLECT, "hud.mysticarts.deflecting", Ability.DEFLECTION.color(), cy);
            cy = chip(g, font, Payloads.PowerSync.F_SUPERCHARGE, "hud.mysticarts.supercharged", Source.POWER.color, cy);
            cy = chip(g, font, Payloads.PowerSync.F_ASTRAL, "hud.mysticarts.astral", 0x9FD4FF, cy);
            cy = chip(g, font, Payloads.PowerSync.F_ASTRAL_FORM, "hud.mysticarts.astral_form", 0x9FD4FF, cy);
            chip(g, font, Payloads.PowerSync.F_MIRROR, "hud.mysticarts.mirror", 0x9FE8FF, cy);
        }
    }

    private static int chip(GuiGraphics g, Font font, int flag, String key, int color, int y) {
        if (!ClientPower.has(flag)) return y;
        Component c = Component.translatable(key);
        int wdt = font.width(c) + 8;
        g.fill(4, y, 4 + wdt, y + 11, 0x88000000);
        g.fill(4, y, 6, y + 11, 0xFF000000 | color);
        g.drawString(font, c, 9, y + 2, color);
        return y + 13;
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int w, float value, float max, int c0, int c1, Component label, float time) {
        float f = max <= 0 ? 0 : Mth.clamp(value / max, 0f, 1f);
        g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xCC000000);
        int fw = (int) (w * f);
        for (int i = 0; i < fw; i += 2) {
            float t = i / (float) w;
            float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.25f - i * 0.15f);
            g.fill(x + i, y, x + Math.min(fw, i + 2), y + 5, 0xFF000000 | ColorUtil.scale(ColorUtil.lerp(c0, c1, t), shimmer));
        }
        g.fill(x, y, x + fw, y + 1, 0x55FFFFFF);
        if (label != null) {
            g.drawString(font, Component.literal(String.format("%.0f", value)), x + w + 4, y - 1, c0);
        }
    }
}

package com.wandcraft.client;

import com.wandcraft.item.WandItem;
import com.wandcraft.magic.CastMode;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Rank;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/** Compact HUD: selected spell, mana bar, cooldown and a charge ring around the crosshair. */
final class Hud {
    private Hud() {}

    static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !(p.getMainHandItem().getItem() instanceof WandItem)) return;
        PlayerMagic m = SpellView.magic();
        Spell s = Spells.get(m.selected);
        int w = g.guiWidth(), h = g.guiHeight();

        // mana bar bottom-right of the hotbar
        int bw = 80, bx = w / 2 + 98, by = h - 14;
        float frac = Math.min(1f, m.mana / Math.max(1f, m.maxMana()));
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xCC05060E);
        g.fill(bx, by, bx + (int) (bw * frac), by + 6, 0xFF3F7BFF);
        g.fill(bx, by, bx + (int) (bw * frac), by + 2, 0xFF8FB4FF);
        g.drawString(mc.font, (int) m.mana + "/" + (int) m.maxMana(), bx + 2, by - 10, 0xFF9CC0FF, true);

        if (s != null) {
            int ix = bx - 30, iy = h - 26;
            int c = s.category.color;
            Draw.disc(g, ix + 12, iy + 12, 14, Draw.argb(0x07080F, 220));
            Draw.ring(g, ix + 12, iy + 12, 14, Draw.argb(c, 230));
            Draw.glyph(g, s.icon, ix, iy, 2, c, 255);
            float cd = SpellView.cooldownFraction(s);
            if (cd > 0) g.fill(ix + 2, iy + 24 - (int) (22 * cd), ix + 22, iy + 24, 0x99000000);
            Rank r = SpellView.rank(s);
            g.drawString(mc.font, s.name, bx, by - 21, Draw.argb(c, 255), true);
            for (int k = 0; k < 3; k++) g.fill(bx + bw - 14 + k * 5, by - 19, bx + bw - 11 + k * 5, by - 16, k <= r.ordinal() ? 0xFFFFD84A : 0xFF444444);
        }

        // charge ring around the crosshair
        if (s != null && p.isUsingItem() && p.getUseItem().getItem() instanceof WandItem && s.mode == CastMode.CHARGE) {
            float f = Math.min(1f, p.getTicksUsingItem() / (float) s.chargeTicks);
            int cx = w / 2, cy = h / 2;
            int dots = 48;
            for (int i = 0; i < dots; i++) {
                if (i / (float) dots > f) break;
                double a = -Math.PI / 2 + Math.PI * 2 * i / dots;
                int x = cx + (int) Math.round(Math.cos(a) * 13), y = cy + (int) Math.round(Math.sin(a) * 13);
                g.fill(x, y, x + 2, y + 2, Draw.argb(f >= 1f ? 0xFFFFFF : s.category.color, 240));
            }
        } else if (s != null && s.mode == CastMode.CHANNEL && p.isUsingItem() && p.getUseItem().getItem() instanceof WandItem) {
            int cx = w / 2, cy = h / 2;
            Draw.ring(g, cx, cy, 12, Draw.argb(s.category.color, 160));
        }
    }
}

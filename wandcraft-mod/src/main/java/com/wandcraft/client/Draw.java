package com.wandcraft.client;

import com.wandcraft.magic.Glyph;
import net.minecraft.client.gui.GuiGraphics;

/** Shared immediate-mode drawing helpers for the wheel, spellbook and HUD. */
final class Draw {
    private Draw() {}

    static int argb(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Draws a 12x12 glyph with its top-left at (x, y); each cell is {@code px} pixels. */
    static void glyph(GuiGraphics g, Glyph glyph, int x, int y, int px, int rgb, int alpha) {
        int light = mix(rgb, 0xFFFFFF, 0.55f);
        int dark = mix(rgb, 0x000000, 0.65f);
        // soft drop shadow
        for (int r = 0; r < glyph.rows.length; r++) {
            String row = glyph.rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '#' || ch == '+') g.fill(x + c * px + 1, y + r * px + 1, x + c * px + px + 1, y + r * px + px + 1, argb(dark, alpha / 2));
            }
        }
        for (int r = 0; r < glyph.rows.length; r++) {
            String row = glyph.rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '#') g.fill(x + c * px, y + r * px, x + c * px + px, y + r * px + px, argb(rgb, alpha));
                else if (ch == '+') g.fill(x + c * px, y + r * px, x + c * px + px, y + r * px + px, argb(light, alpha));
            }
        }
    }

    /** Filled disc approximated by horizontal scanlines. */
    static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, argb);
        }
    }

    static void ring(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int i = 0; i < 360; i += 4) {
            double a = Math.toRadians(i);
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    static void padlock(GuiGraphics g, int cx, int cy, int argb) {
        g.fill(cx - 3, cy - 1, cx + 4, cy + 4, argb);
        g.fill(cx - 2, cy - 4, cx - 1, cy - 1, argb);
        g.fill(cx + 2, cy - 4, cx + 3, cy - 1, argb);
        g.fill(cx - 2, cy - 5, cx + 3, cy - 4, argb);
    }
}

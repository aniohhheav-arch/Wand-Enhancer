package com.wandcraft.client;

import com.wandcraft.magic.Category;
import com.wandcraft.magic.CastMode;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.Rank;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Radial spell picker. Hold the wheel key, point at a spell, release to select it. */
public class WheelScreen extends Screen {
    private final List<Category> cats = Spells.activeCategories();
    private int catIdx;
    private List<Spell> spells;
    private int hovered = -1;
    private final long opened = Util.getMillis();
    private int cx, cy, radius;

    public WheelScreen() {
        super(Component.literal("Spell Wheel"));
        Spell sel = Spells.get(SpellView.magic().selected);
        if (sel != null && cats.contains(sel.category)) catIdx = cats.indexOf(sel.category);
        refresh();
    }

    private void refresh() {
        spells = Spells.in(cats.get(catIdx));
        hovered = -1;
        radius = Math.max(84, (int) Math.ceil(spells.size() * 38 / (Math.PI * 2)));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        cx = width / 2;
        cy = height / 2;
    }

    private float ease() {
        float t = Mth.clamp((Util.getMillis() - opened) / 160f, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private int nodeX(int i, float e) { return cx + (int) Math.round(Math.cos(angle(i)) * radius * e); }
    private int nodeY(int i, float e) { return cy + (int) Math.round(Math.sin(angle(i)) * radius * e); }
    private double angle(int i) { return -Math.PI / 2 + Math.PI * 2 * i / spells.size(); }

    private int tabX() { return cx - radius - 84; }
    private int tabY(int i) { return cy - cats.size() * 14 + i * 28; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x66000010, 0x99000010);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        float e = ease();
        Category cat = cats.get(catIdx);

        // hover detection by angle
        double dx = mx - cx, dy = my - cy;
        hovered = -1;
        if (Math.sqrt(dx * dx + dy * dy) > 38 && !spells.isEmpty()) {
            double a = Math.atan2(dy, dx) + Math.PI / 2;
            if (a < 0) a += Math.PI * 2;
            hovered = (int) Math.round(a / (Math.PI * 2 / spells.size())) % spells.size();
        }

        // ring backdrop
        Draw.disc(g, cx, cy, (int) (radius * e) + 22, Draw.argb(0x05060F, 150));
        Draw.ring(g, cx, cy, (int) (radius * e) + 22, Draw.argb(cat.color, 170));
        Draw.ring(g, cx, cy, (int) (radius * e) - 22, Draw.argb(cat.color, 60));

        String selId = SpellView.magic().selected;
        for (int i = 0; i < spells.size(); i++) {
            Spell s = spells.get(i);
            int x = nodeX(i, e), y = nodeY(i, e);
            boolean unlocked = SpellView.unlocked(s);
            boolean hov = i == hovered;
            int r = hov ? 17 : 14;
            int base = unlocked ? cat.color : 0x555560;
            Draw.disc(g, x, y, r + 2, Draw.argb(hov ? 0xFFFFFF : base, hov ? 230 : 120));
            Draw.disc(g, x, y, r, Draw.argb(0x0B0C18, 235));
            int px = hov ? 2 : 2;
            Draw.glyph(g, s.icon, x - 12, y - 12, px, unlocked ? cat.color : 0x6A6A74, unlocked ? 255 : 130);
            if (!unlocked) Draw.padlock(g, x + 8, y + 8, 0xFFCCCCCC);
            if (s.id.equals(selId)) Draw.ring(g, x, y, r + 4, Draw.argb(0xFFFFFF, 230));
            // cooldown sweep from the bottom
            float cd = SpellView.cooldownFraction(s);
            if (cd > 0) g.fill(x - r + 2, y + r - 2 - (int) ((2 * r - 4) * cd), x + r - 2, y + r - 2, 0x99000000);
            // mastery dots
            if (unlocked) {
                Rank rank = SpellView.rank(s);
                for (int k = 0; k < 3; k++) {
                    boolean on = k <= rank.ordinal();
                    g.fill(x - 6 + k * 5, y + r + 3, x - 3 + k * 5, y + r + 5, on ? Draw.argb(0xFFD84A, 255) : Draw.argb(0x444444, 200));
                }
                if (rank == Rank.MASTER && SpellView.hasEvolution(s)) g.fill(x + r - 3, y - r - 1, x + r + 1, y - r + 3, 0xFFFFE070);
            }
        }

        // centre readout
        if (hovered >= 0) infoPanel(g, spells.get(hovered), cat);
        else {
            Spell sel = Spells.get(selId);
            g.drawCenteredString(font, cat.display, cx, cy - 12, Draw.argb(cat.color, 255));
            g.drawCenteredString(font, Component.literal(sel == null ? "" : "Selected: " + sel.name), cx, cy, 0xFFE0E0E0);
        }

        // category tabs
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            int x = tabX(), y = tabY(i);
            boolean on = i == catIdx;
            g.fill(x - 4, y - 3, x + 80, y + 21, Draw.argb(on ? c.color : 0x101018, on ? 90 : 160));
            if (on) g.fill(x - 4, y - 3, x - 2, y + 21, Draw.argb(c.color, 255));
            Draw.glyph(g, c.icon, x, y, 2, c.color, 255);
            g.drawString(font, c.display, x + 28, y + 8, on ? 0xFFFFFFFF : 0xFFB0B0B8, false);
        }

        // footer
        PlayerHint.draw(g, font, width, height);
    }

    private void infoPanel(GuiGraphics g, Spell s, Category cat) {
        boolean unlocked = SpellView.unlocked(s);
        Rank rank = SpellView.rank(s);
        int w = 150;
        int y = cy - 52;
        g.drawCenteredString(font, s.name, cx, y, Draw.argb(unlocked ? cat.color : 0xAAAAAA, 255));
        y += 11;
        String sub = s.mode == CastMode.INSTANT ? "Instant" : s.mode == CastMode.CHARGE ? "Charge" : "Channel";
        g.drawCenteredString(font, unlocked ? sub + " · " + rank.display : "Locked", cx, y, 0xFF9AA0B0);
        y += 12;
        for (FormattedCharSequence line : font.split(Component.literal(s.desc), w)) {
            g.drawCenteredString(font, line, cx, y, 0xFFE8E8F0);
            y += 9;
        }
        y += 3;
        if (unlocked) {
            String cost = s.mode == CastMode.CHANNEL ? SpellView.cost(s) + " mana/s" : SpellView.cost(s) + " mana";
            g.drawCenteredString(font, cost + "  ·  " + String.format("%.1fs", s.cooldown * rank.cooldownMul / 20f) + " cd", cx, y, 0xFF8FC8FF);
            y += 11;
            if (rank != Rank.MASTER) {
                int bw = 90;
                g.fill(cx - bw / 2, y, cx + bw / 2, y + 3, 0xFF22232C);
                g.fill(cx - bw / 2, y, cx - bw / 2 + (int) (bw * SpellView.rankProgress(s)), y + 3, Draw.argb(0xFFD84A, 255));
                y += 7;
                g.drawCenteredString(font, "Next: " + (rank == Rank.NOVICE ? s.adeptPerk : s.masterPerk), cx, y, 0xFFB0A070);
            } else if (SpellView.hasEvolution(s)) {
                g.drawCenteredString(font, "Evolution unlocked!", cx, y, 0xFFFFE070);
            }
        } else {
            g.drawCenteredString(font, Magic.lockReason(SpellView.player(), s), cx, y, 0xFFFF8080);
        }
    }

    private void choose() {
        if (hovered >= 0 && hovered < spells.size()) SpellView.select(spells.get(hovered));
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (ClientSetup.WHEEL_KEY.matches(key, scan)) {
            choose();
            onClose();
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263) { switchCat(-1); return true; }
        if (key == 262) { switchCat(1); return true; }
        if (ClientSetup.BOOK_KEY.matches(key, scan)) {
            minecraft.setScreen(new SpellbookScreen());
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        switchCat(sy > 0 ? -1 : 1);
        return true;
    }

    private void switchCat(int d) {
        catIdx = Math.floorMod(catIdx + d, cats.size());
        refresh();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < cats.size(); i++) {
            if (mx >= tabX() - 4 && mx <= tabX() + 80 && my >= tabY(i) - 3 && my <= tabY(i) + 21) {
                catIdx = i;
                refresh();
                return true;
            }
        }
        if (hovered >= 0) {
            choose();
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}

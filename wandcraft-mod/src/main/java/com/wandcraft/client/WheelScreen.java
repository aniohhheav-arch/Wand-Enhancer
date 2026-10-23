package com.wandcraft.client;

import com.wandcraft.magic.CastMode;
import com.wandcraft.magic.Category;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.Rank;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Radial spell picker. Hold the wheel key, point at a spell, release to select it. Schools with many spells use two
 * rings, the school tabs sit to the left and full details appear in a side panel so the wheel itself stays uncluttered.
 */
public class WheelScreen extends Screen {
    private record Node(Spell spell, int x, int y, int r) {}

    private final List<Category> cats = Spells.activeCategories();
    private int catIdx;
    private List<Spell> spells;
    private final List<Node> nodes = new ArrayList<>();
    private int hovered = -1;
    private final long opened = Util.getMillis();
    private int cx, cy, radius, nodeR, tabX, panelX, panelW, tabsW;
    private boolean twoRings;

    public WheelScreen() {
        super(Component.literal("Spell Wheel"));
        Spell sel = Spells.get(SpellView.magic().selected);
        if (sel != null && cats.contains(sel.category)) catIdx = cats.indexOf(sel.category);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        refresh();
    }

    private void refresh() {
        spells = Spells.in(cats.get(catIdx));
        hovered = -1;
        int n = spells.size();
        twoRings = n > 11;
        tabsW = width < 520 ? 84 : 100;
        int gap = width < 520 ? 8 : 14, panelWant = width < 520 ? 150 : 186;
        int availH = Math.min(height / 2 - 48, 150);
        int availW = (width - tabsW - panelWant - gap * 3) / 2;
        radius = Mth.clamp(Math.min(availH, availW), 48, 150);
        int outer = twoRings ? (int) Math.ceil(n * 0.62) : n;
        nodeR = Mth.clamp((int) (Math.PI * 2 * radius / Math.max(outer, 1) * 0.38), 13, 22);
        if (twoRings) nodeR = Math.min(nodeR, (int) (radius * 0.2));
        int ringSpan = radius + nodeR + 10;
        int total = tabsW + gap + ringSpan * 2 + gap + panelWant;
        int x0 = Math.max(4, (width - total) / 2);
        tabX = x0;
        cx = x0 + tabsW + gap + ringSpan;
        cy = height / 2;
        panelX = cx + ringSpan + gap;
        panelW = Math.min(panelWant, width - panelX - 6);
        nodes.clear();
        int idx = 0;
        for (int i = 0; i < outer; i++, idx++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / outer;
            nodes.add(new Node(spells.get(idx), cx + (int) Math.round(Math.cos(a) * radius), cy + (int) Math.round(Math.sin(a) * radius), nodeR));
        }
        int inner = n - outer;
        for (int i = 0; i < inner; i++, idx++) {
            double a = -Math.PI / 2 + Math.PI * 2 * (i + 0.5) / inner;
            double ri = radius * 0.6;
            nodes.add(new Node(spells.get(idx), cx + (int) Math.round(Math.cos(a) * ri), cy + (int) Math.round(Math.sin(a) * ri), nodeR));
        }
    }

    private float ease() {
        float t = Mth.clamp((Util.getMillis() - opened) / 170f, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private int tabStep() { return Math.min(30, (height - 24) / cats.size()); }

    private int tabY(int i) { return cy - cats.size() * tabStep() / 2 + i * tabStep() + 3; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x55000010, 0x88000010);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        float e = ease();
        Category cat = cats.get(catIdx);

        hovered = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            Node nd = nodes.get(i);
            double d = Math.hypot(mx - nd.x, my - nd.y);
            if (d <= nd.r * 1.3 + 4 && d < best) {
                best = d;
                hovered = i;
            }
        }

        // backdrop
        int rr = (int) ((radius + nodeR + 8) * e);
        Draw.disc(g, cx, cy, rr, Draw.argb(0x05060F, 165));
        Draw.ring(g, cx, cy, rr, Draw.argb(cat.color, 190));
        if (twoRings) Draw.ring(g, cx, cy, (int) (radius * 0.6 * e) + nodeR + 6, Draw.argb(cat.color, 55));
        Draw.ring(g, cx, cy, (int) (radius * 0.3 * e), Draw.argb(cat.color, 35));

        String selId = SpellView.magic().selected;
        for (int i = 0; i < nodes.size(); i++) {
            Node nd = nodes.get(i);
            Spell s = nd.spell;
            int x = cx + (int) ((nd.x - cx) * e), y = cy + (int) ((nd.y - cy) * e);
            boolean unlocked = SpellView.unlocked(s), hov = i == hovered, sel = s.id.equals(selId);
            int r = hov ? (int) (nd.r * 1.28) : nd.r;
            Rank rank = unlocked ? SpellView.rank(s) : Rank.NOVICE;
            int rim = !unlocked ? 0x555560 : rank == Rank.MASTER ? 0xFFD84A : rank == Rank.ADEPT ? Draw.mix(cat.color, 0xFFFFFF, 0.35f) : cat.color;
            if (hov) Draw.disc(g, x, y, r + 5, Draw.argb(rim, 90));
            Draw.disc(g, x, y, r + 2, Draw.argb(hov ? 0xFFFFFF : rim, hov ? 235 : 150));
            Draw.disc(g, x, y, r, Draw.argb(0x0B0C18, 240));
            Draw.spellIcon(g, s, x, y, (int) (r * 1.55), !unlocked, 1f);
            if (!unlocked) Draw.padlock(g, x + r * 2 / 3, y + r * 2 / 3, 0xFFDDDDDD);
            if (sel) Draw.ring(g, x, y, r + 5, Draw.argb(0xFFFFFF, 240));
            float cd = SpellView.cooldownFraction(s);
            if (cd > 0) g.fill(x - r + 3, y + r - 3 - (int) ((2 * r - 6) * cd), x + r - 3, y + r - 3, 0x88000000);
            if (unlocked && rank == Rank.MASTER && SpellView.hasEvolution(s)) g.fill(x + r - 3, y - r, x + r + 2, y - r + 5, 0xFFFFE070);
        }

        // centre: big icon of the hovered (or selected) spell
        Spell focus = hovered >= 0 ? nodes.get(hovered).spell : Spells.get(selId);
        if (focus != null && cats.contains(focus.category) == (focus.category == cat)) {
            int big = twoRings ? Math.max(20, (int) (radius * 0.3)) : Math.max(24, (int) (radius * 0.4));
            Draw.disc(g, cx, cy, big / 2 + 7, Draw.argb(0x0B0C18, 235));
            Draw.ring(g, cx, cy, big / 2 + 7, Draw.argb(focus.category.color, 220));
            Draw.spellIcon(g, focus, cx, cy, big, !SpellView.unlocked(focus), 1f);
            if (!twoRings) g.drawCenteredString(font, focus.name, cx, cy + big / 2 + 12, Draw.argb(focus.category.color, 255));
        } else {
            Draw.disc(g, cx, cy, 24, Draw.argb(0x0B0C18, 235));
            Draw.glyph(g, cat.icon, cx - 12, cy - 12, 2, cat.color, 255);
        }

        // school tabs
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            int x = tabX, y = tabY(i);
            boolean on = i == catIdx;
            int th = tabStep() - 4;
            g.fill(x, y - 3, x + tabsW, y - 3 + th, Draw.argb(on ? c.color : 0x101018, on ? 95 : 170));
            if (on) g.fill(x, y - 3, x + 3, y - 3 + th, Draw.argb(c.color, 255));
            Draw.glyph(g, c.icon, x + 6, y + (th - 6 - 16) / 2, 2, c.color, 255);
            g.drawString(font, c.display, x + 30, y - 3 + (th - 8) / 2, on ? 0xFFFFFFFF : 0xFFB0B0B8, false);
        }

        if (focus != null && focus.category == cat) detailPanel(g, focus, cat);
        PlayerHint.draw(g, font, width, height);
    }

    private void detailPanel(GuiGraphics g, Spell s, Category cat) {
        boolean unlocked = SpellView.unlocked(s);
        Rank rank = SpellView.rank(s);
        int x = panelX, w = panelW, y = Math.max(12, cy - 78);
        g.fill(x - 6, y - 8, x + w + 4, y + 164, Draw.argb(0x06070F, 215));
        g.fill(x - 6, y - 8, x - 3, y + 164, Draw.argb(cat.color, 255));
        g.drawString(font, s.name, x + 2, y, Draw.argb(unlocked ? cat.color : 0xAAAAAA, 255), true);
        y += 12;
        String mode = s.mode == CastMode.INSTANT ? "Instant" : s.mode == CastMode.CHARGE ? "Charge" : "Channel";
        g.drawString(font, unlocked ? mode + " · " + rank.display : "Locked", x + 2, y, 0xFF9AA0B0, false);
        y += 13;
        for (FormattedCharSequence line : font.split(Component.literal(s.desc), w - 6)) {
            g.drawString(font, line, x + 2, y, 0xFFE8E8F0, false);
            y += 9;
        }
        y += 4;
        if (unlocked) {
            String cost = s.mode == CastMode.CHANNEL ? SpellView.cost(s) + " mana/s" : SpellView.cost(s) + " mana";
            g.drawString(font, cost, x + 2, y, 0xFF8FC8FF, false);
            String rc = String.format("%.1fs recharge", s.cooldownTicks() * rank.cooldownMul / 20f);
            if (font.width(cost) + font.width(rc) + 10 > w - 6) { y += 10; g.drawString(font, rc, x + 2, y, 0xFF8FC8FF, false); }
            else g.drawString(font, rc, x + 2 + font.width(cost) + 8, y, 0xFF8FC8FF, false);
            y += 12;
            if (rank != Rank.MASTER) {
                g.fill(x + 2, y, x + w - 4, y + 3, 0xFF22232C);
                g.fill(x + 2, y, x + 2 + (int) ((w - 6) * SpellView.rankProgress(s)), y + 3, Draw.argb(0xFFD84A, 255));
                y += 7;
                for (FormattedCharSequence line : font.split(Component.literal("Next: " + (rank == Rank.NOVICE ? s.adeptPerk : s.masterPerk)), w - 6)) {
                    g.drawString(font, line, x + 2, y, 0xFFB0A070, false);
                    y += 9;
                }
            } else if (SpellView.hasEvolution(s)) {
                g.drawString(font, "Evolution unlocked!", x + 2, y, 0xFFFFE070, false);
            }
        } else {
            for (FormattedCharSequence line : font.split(Component.literal(Magic.lockReason(SpellView.player(), s)), w - 6)) {
                g.drawString(font, line, x + 2, y, 0xFFFF8080, false);
                y += 9;
            }
        }
    }

    private void choose() {
        if (hovered >= 0 && hovered < nodes.size()) SpellView.select(nodes.get(hovered).spell);
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
            if (mx >= tabX && mx <= tabX + tabsW && my >= tabY(i) - 3 && my <= tabY(i) - 3 + tabStep()) {
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

package com.wandcraft.client;

import com.wandcraft.magic.CastMode;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.Rank;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Searchable list of every spell, with unlock requirements and mastery progress. */
public class SpellbookScreen extends Screen {
    private static final int ROW = 30;
    private EditBox search;
    private List<Spell> shown = new ArrayList<>();
    private int scroll;
    private int left, top, panelW, panelH;

    public SpellbookScreen() { super(Component.literal("Spellbook")); }

    @Override
    protected void init() {
        panelW = Math.min(width - 20, 340);
        panelH = height - 40;
        left = (width - panelW) / 2;
        top = 20;
        search = new EditBox(font, left + 8, top + 18, panelW - 16, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search spells, schools, effects..."));
        search.setResponder(t -> { scroll = 0; refilter(); });
        addRenderableWidget(search);
        setInitialFocus(search);
        refilter();
    }

    private void refilter() {
        String q = search.getValue().toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (Spell s : Spells.all()) {
            if (q.isEmpty() || s.name.toLowerCase(Locale.ROOT).contains(q) || s.category.display.toLowerCase(Locale.ROOT).contains(q)
                    || s.desc.toLowerCase(Locale.ROOT).contains(q)) shown.add(s);
        }
    }

    private int listTop() { return top + 40; }
    private int visibleRows() { return (panelH - 48) / ROW; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(left, top, left + panelW, top + panelH, 0xE0080912);
        g.renderOutline(left, top, panelW, panelH, 0xFF3A3F66);
        g.drawString(font, "Spellbook", left + 8, top + 6, 0xFFE8E8FF, false);
        g.drawString(font, "Level " + SpellView.magic().level() + "  ·  " + (int) SpellView.magic().mana + "/" + (int) SpellView.magic().maxMana() + " mana",
                left + panelW - 8 - font.width("Level 00  ·  000/000 mana"), top + 6, 0xFF8FC8FF, false);
        super.render(g, mx, my, pt);

        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - visibleRows()));
        String sel = SpellView.magic().selected;
        for (int i = 0; i < visibleRows() && scroll + i < shown.size(); i++) {
            Spell s = shown.get(scroll + i);
            int y = listTop() + i * ROW;
            boolean unlocked = SpellView.unlocked(s);
            boolean hov = mx >= left + 4 && mx <= left + panelW - 4 && my >= y && my < y + ROW - 2;
            int c = s.category.color;
            g.fill(left + 4, y, left + panelW - 4, y + ROW - 2, Draw.argb(hov ? c : 0x151625, hov ? 70 : 220));
            if (s.id.equals(sel)) g.renderOutline(left + 4, y, panelW - 8, ROW - 2, Draw.argb(0xFFFFFF, 220));
            Draw.glyph(g, s.icon, left + 8, y + 3, 2, unlocked ? c : 0x66666E, unlocked ? 255 : 130);
            g.drawString(font, s.name, left + 38, y + 3, unlocked ? 0xFFFFFFFF : 0xFF8A8A92, false);
            String meta = s.category.display + " · " + (s.mode == CastMode.INSTANT ? "Instant" : s.mode == CastMode.CHARGE ? "Charge" : "Channel");
            g.drawString(font, meta, left + 38, y + 13, Draw.argb(c, 255), false);
            if (unlocked) {
                Rank r = SpellView.rank(s);
                String right = SpellView.cost(s) + (s.mode == CastMode.CHANNEL ? "/s" : "") + " mana · " + r.display;
                g.drawString(font, right, left + panelW - 10 - font.width(right), y + 3, 0xFF8FC8FF, false);
                int bw = 60;
                g.fill(left + panelW - 10 - bw, y + 15, left + panelW - 10, y + 18, 0xFF22232C);
                g.fill(left + panelW - 10 - bw, y + 15, left + panelW - 10 - bw + (int) (bw * SpellView.rankProgress(s)), y + 18, 0xFFFFD84A);
            } else {
                String r = Magic.lockReason(SpellView.player(), s);
                g.drawString(font, r, left + panelW - 10 - font.width(r), y + 3, 0xFFFF8080, false);
            }
        }
        if (shown.isEmpty()) g.drawCenteredString(font, "No spells match", width / 2, listTop() + 20, 0xFF9AA0B0);

        // tooltip with the description of the hovered row
        int idx = (my - listTop()) / ROW;
        if (mx >= left && mx <= left + panelW && my >= listTop() && idx >= 0 && idx < visibleRows() && scroll + idx < shown.size()) {
            Spell s = shown.get(scroll + idx);
            g.renderTooltip(font, font.split(Component.literal(s.desc), 200), mx, my);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= (int) Math.signum(sy) * 2;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int idx = (int) ((my - listTop()) / ROW);
        if (mx >= left && mx <= left + panelW && my >= listTop() && idx >= 0 && idx < visibleRows() && scroll + idx < shown.size()) {
            Spell s = shown.get(scroll + idx);
            if (SpellView.unlocked(s)) {
                SpellView.select(s);
                onClose();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!search.isFocused() && ClientSetup.BOOK_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

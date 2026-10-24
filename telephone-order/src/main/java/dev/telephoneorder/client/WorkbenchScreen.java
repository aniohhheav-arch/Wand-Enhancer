package dev.telephoneorder.client;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.ModSounds;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.weapon.GunData;
import dev.telephoneorder.weapon.GunItem;
import dev.telephoneorder.weapon.GunStats;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Weapons workbench: inspect the held gun in a rotating hologram, fit / remove attachments, preview the stat changes. */
public class WorkbenchScreen extends Screen {
    private static final int W = 344, H = 214;
    private int tick;
    private float spin;
    private boolean dragging;
    private String previewSlot = "", previewAtt = "";
    private record Region(int x, int y, int w, int h, String id) {}
    private final List<Region> regions = new ArrayList<>();

    public WorkbenchScreen() { super(Component.literal("Weapons Workbench")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { tick++; if (!dragging) spin += 1.6f; }

    private ItemStack gun() { return Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem(); }

    private void sound(String n, float p) { if (ModSounds.has(n)) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get(n), p, 0.7f)); }

    private boolean btn(GuiGraphicsExtractor g, String id, String label, int x, int y, int w, int h, boolean enabled, int color, int mx, int my) {
        Region r = new Region(x, y, w, h, id);
        if (enabled) regions.add(r);
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, enabled ? (hov ? 0xFF2A3440 : 0xFF1D242D) : 0xFF14181D);
        g.outline(x, y, w, h, enabled ? (hov ? color : (color & 0xFFFFFF) | 0x77000000) : 0xFF22272E);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, enabled ? (hov ? 0xFFFFFFFF : color) : 0xFF4A525C, false);
        return hov;
    }

    private int owned(String attId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        var item = ModItems.ATTACHMENTS.get(attId);
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) { ItemStack s = mc.player.getInventory().getItem(i); if (s.getItem() == item) n += s.getCount(); }
        return n;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx0, int my0, float partial) {
        regions.clear();
        int ox = (width - W) / 2, oy = (height - H) / 2;
        int mx = mx0 - ox, my = my0 - oy;
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(ox - 3, oy - 3, ox + W + 3, oy + H + 3, 0xFF3A404A);
        g.fill(ox - 1, oy - 1, ox + W + 1, oy + H + 1, 0xFFCDA03E);
        g.fill(ox, oy, ox + W, oy + H, 0xFF0C0E12);
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.fill(0, 0, W, 22, 0xFF11151A); g.fill(0, 21, W, 22, 0xFFCDA03E);
        g.text(font, "WEAPONS WORKBENCH", 8, 7, 0xFFFFB84A, true);
        ItemStack stack = gun();
        if (!(stack.getItem() instanceof GunItem gi)) {
            g.text(font, "Hold a weapon in your main hand and use the workbench again.", 20, 100, 0xFFFF7070, false);
            g.pose().popMatrix();
            return;
        }
        GunDef def = gi.def;
        GunStats cur = GunStats.of(def, stack);
        g.text(font, def.name().toUpperCase() + "   •   " + def.cat().toUpperCase(), W - 8 - font.width(def.name().toUpperCase() + "   •   " + def.cat().toUpperCase()), 7, 0xFFB0B8C2, false);

        // ---- viewer
        int vx = 8, vy = 30, vw = 130, vh = 96;
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF05090D);
        Region vr = new Region(vx, vy, vw, vh, "viewer"); regions.add(vr);
        for (int i = 0; i <= 10; i++) g.fill(vx + i * vw / 10, vy + vh - 26, vx + i * vw / 10 + 1, vy + vh, 0x22406070);
        for (int j = 0; j < 5; j++) g.fill(vx, vy + vh - 26 + j * 6, vx + vw, vy + vh - 25 + j * 6, 0x22406070);
        g.fillGradient(vx + 20, vy + 4, vx + vw - 20, vy + vh - 12, 0x0000D8FF, 0x2200D8FF);
        float ang = (float) Math.toRadians(spin + partial * 1.6f);
        float cs = (float) Math.cos(ang);
        g.pose().pushMatrix();
        g.pose().translate(vx + vw / 2f, vy + vh / 2f - 4);
        g.pose().scale(Math.max(0.12f, Math.abs(cs)) * 5.0f * (cs < 0 ? -1 : 1), 5.0f);
        g.item(stack, -8, -8);
        g.pose().popMatrix();
        g.outline(vx, vy, vw, vh, 0xFF1F5A6A);
        g.text(font, "DRAG TO ROTATE", vx + 4, vy + vh - 10, 0xAA00D8FF, false);

        // ---- stats
        GunStats prev = cur;
        if (!previewAtt.isEmpty()) {
            ItemStack copy = stack.copy();
            GunData.edit(copy, t -> { var at = t.getCompoundOrEmpty("att"); at.putString(previewSlot, previewAtt); t.put("att", at); });
            prev = GunStats.of(def, copy);
        }
        int sy = vy + vh + 8;
        g.text(font, previewAtt.isEmpty() ? "CURRENT STATS" : "PREVIEW: " + Catalog.ATT_BY_ID.get(previewAtt).name().toUpperCase(), 8, sy, previewAtt.isEmpty() ? 0xFFFFB84A : 0xFF6BFFB4, false);
        String[][] rows = new String[][]{
                {"DAMAGE", fmt(def.dmg() * cur.dmg), fmt(def.dmg() * prev.dmg), "+"}, {"SPREAD", fmt(def.spread() * cur.spread), fmt(def.spread() * prev.spread), "-"},
                {"RECOIL", fmt(def.rp() * cur.recoil), fmt(def.rp() * prev.recoil), "-"}, {"MAGAZINE", cur.mag + "", prev.mag + "", "+"},
                {"RELOAD", fmt(def.reload() * cur.reload / 20f) + "s", fmt(def.reload() * prev.reload / 20f) + "s", "-"}, {"RANGE", Math.round(def.range() * cur.range) + "m", Math.round(def.range() * prev.range) + "m", "+"},
                {"NOISE", fmt(cur.noise * 100) + "%", fmt(prev.noise * 100) + "%", "-"}};
        for (int i = 0; i < rows.length; i++) {
            int y = sy + 12 + i * 10;
            g.text(font, rows[i][0], 8, y, 0xFF6F7A86, false);
            g.text(font, rows[i][1], 62, y, 0xFFE0E6EC, false);
            if (!rows[i][1].equals(rows[i][2])) {
                float c0 = parse(rows[i][1]), c1 = parse(rows[i][2]);
                boolean better = rows[i][3].equals("+") ? c1 > c0 : c1 < c0;
                g.text(font, "→ " + rows[i][2], 92, y, better ? 0xFF6BFFB4 : 0xFFFF7070, false);
            }
        }

        // ---- slots
        int sx = 148, y = 30;
        g.text(font, "ATTACHMENT SLOTS", sx, y - 8 + 0, 0xFFFFB84A, false);
        for (String slot : def.slots()) {
            String curId = GunData.attachment(stack, slot);
            AttDef curAtt = Catalog.ATT_BY_ID.get(curId);
            g.fill(sx, y, W - 8, y + 42, 0xFF11161C);
            g.outline(sx, y, W - 8 - sx, 42, 0xFF262C34);
            g.text(font, slot.toUpperCase(), sx + 5, y + 4, 0xFF8A94A0, false);
            g.text(font, curAtt == null ? "- empty -" : curAtt.name(), sx + 52, y + 4, curAtt == null ? 0xFF55606C : 0xFF6BFFB4, false);
            if (curAtt != null) btn(g, "detach:" + slot, "REMOVE", W - 8 - 52, y + 2, 48, 12, true, 0xFFFF7070, mx, my);
            int ax = sx + 5, ay = y + 18;
            int shown = 0;
            for (AttDef a : Catalog.ATTS) {
                if (!a.slot().equals(slot) || !Catalog.fits(a, def)) continue;
                int have = owned(a.id());
                boolean eq = a.id().equals(curId);
                Region r = new Region(ax, ay, 20, 20, "attach:" + slot + ":" + a.id());
                boolean enabled = have > 0 && !eq;
                if (enabled) regions.add(r);
                boolean hov = mx >= r.x && mx < r.x + 20 && my >= r.y && my < r.y + 20;
                g.fill(ax, ay, ax + 20, ay + 20, eq ? 0xFF1E3A2C : (hov && enabled ? 0xFF2A3440 : 0xFF1A2028));
                g.outline(ax, ay, 20, 20, eq ? 0xFF6BFFB4 : (have > 0 ? 0xFF5A6674 : 0xFF2A3038));
                g.pose().pushMatrix(); g.pose().translate(ax + 2, ay + 2); g.item(new ItemStack(ModItems.ATTACHMENTS.get(a.id())), 0, 0); g.pose().popMatrix();
                if (have == 0) g.fill(ax + 1, ay + 1, ax + 19, ay + 19, 0xAA0C0E12);
                else g.text(font, "" + have, ax + 14, ay + 11, 0xFFFFFFFF, true);
                if (hov) {
                    previewSlot = slot; previewAtt = a.id();
                    g.setTooltipForNextFrame(font, Component.literal(a.name() + (have == 0 ? "  (not owned)" : "") + "\n" + a.desc()), mx0, my0);
                }
                ax += 22; shown++;
                if (ax > W - 30) break;
            }
            if (shown == 0) g.text(font, "No compatible attachments exist for this slot.", sx + 5, ay + 6, 0xFF55606C, false);
            y += 46;
        }
        if (previewAtt.isEmpty() || !regionsHit(mx, my)) { previewAtt = ""; previewSlot = ""; }
        g.text(font, "Buy attachments by phone, then fit them here. Each fit changes the real stats.", 8, H - 12, 0xFF55606C, false);
        g.pose().popMatrix();
    }

    private boolean regionsHit(int mx, int my) {
        for (Region r : regions) if (r.id.startsWith("attach:") && mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) return true;
        return false;
    }

    private static String fmt(float f) { return f == Math.round(f) ? "" + Math.round(f) : String.format("%.2f", f); }
    private static float parse(String s) { try { return Float.parseFloat(s.replaceAll("[^0-9.\\-]", "")); } catch (NumberFormatException e) { return 0; } }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        int ox = (width - W) / 2, oy = (height - H) / 2;
        double mx = ev.x() - ox, my = ev.y() - oy;
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
                if (r.id.equals("viewer")) { dragging = true; return true; }
                String[] p = r.id.split(":");
                if (p[0].equals("detach")) ClientPlayNetworking.send(new Payloads.WorkbenchAction("detach", p[1], ""));
                else if (p[0].equals("attach")) ClientPlayNetworking.send(new Payloads.WorkbenchAction("attach", p[1], p[2]));
                sound("ui_click", 1.0f);
                return true;
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    @Override public boolean mouseReleased(MouseButtonEvent ev) { dragging = false; return super.mouseReleased(ev); }
    @Override public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) { if (dragging) { spin += (float) dx * 2; return true; } return super.mouseDragged(ev, dx, dy); }
}

package dev.telephoneorder.client;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.weapon.GunData;
import dev.telephoneorder.weapon.GunItem;
import dev.telephoneorder.weapon.GunStats;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Ammo / heat / charge readout, hit markers, lock-on brackets, scope overlay, toasts and screen flashes. */
public class GunHud implements HudElement {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        ModConfig cfg = ModConfig.get();
        ItemStack stack = mc.player.getMainHandItem();

        // screen flash (teleport / rift / explosions)
        if (ClientState.flashTicks > 0) {
            float f = ClientState.flashTicks / (float) Math.max(1, ClientState.flashTotal);
            int a = (int) (Math.min(1f, f * 1.3f) * 150);
            g.fill(0, 0, w, h, (a << 24) | (ClientState.flashRgb & 0xFFFFFF));
        }

        if (stack.getItem() instanceof GunItem gi && cfg.hud) {
            GunDef def = gi.def;
            GunStats st = GunStats.of(def, stack);
            scope(g, w, h, st.zoomFactor(def));
            crosshair(g, w, h, def, st, mc);
            lockOn(g, mc, w, h);
            panel(g, font, mc, stack, def, st, w, h);
        }
        hitMarker(g, w, h);
        toasts(g, font, w);
    }

    private void panel(GuiGraphicsExtractor g, Font font, Minecraft mc, ItemStack stack, GunDef def, GunStats st, int w, int h) {
        CompoundTag t = GunData.read(stack);
        int ammo = t.getIntOr("ammo", 0), reserve = t.getIntOr("reserve", 0);
        if (!def.beh().equals("turret")) {
            var ad = Catalog.AMMO_BY_ID.get(def.ammo());
            var item = dev.telephoneorder.ModItems.AMMO.get(def.ammo());
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (s.getItem() == item) reserve += s.getCount() * ad.per();
            }
        }
        int pw = 132, ph = 46, x = w - pw - 8, y = h - ph - 36;
        g.fill(x, y, x + pw, y + ph, 0xA0080A0E);
        g.outline(x, y, pw, ph, 0xAA555F6B);
        g.fill(x, y, x + 2, y + ph, def.energy() ? 0xFF50E6FF : 0xFFE8C860);
        g.text(font, def.name().toUpperCase(), x + 6, y + 3, 0xFFB0B8C2, false);
        boolean reloading = GunData.reloading(stack);
        boolean low = ammo <= Math.max(1, st.mag / 5);
        String loaded = def.beh().equals("turret") ? "DEPLOY" : String.valueOf(ammo);
        g.pose().pushMatrix();
        g.pose().translate(x + 6, y + 13);
        g.pose().scale(2f, 2f);
        g.text(font, loaded, 0, 0, reloading ? 0xFF8A94A0 : (low ? 0xFFFF6A5A : 0xFFFFFFFF), true);
        g.pose().popMatrix();
        if (!def.beh().equals("turret")) g.text(font, "/ " + reserve, x + 8 + font.width(loaded) * 2 + 2, y + 21, 0xFF8A94A0, false);
        String mod = t.getStringOr("amod", "");
        String mode = def.modes().get(Math.floorMod(t.getIntOr("mode", 0), def.modes().size())).toUpperCase();
        g.text(font, mode + (mod.isEmpty() ? "" : "  " + mod.toUpperCase()), x + 6, y + 35, mod.isEmpty() ? 0xFF7A8490 : 0xFFFFC95A, false);
        // mag bar
        int bx = x + pw - 46, by = y + 3;
        float f = Math.min(1f, ammo / (float) Math.max(1, st.mag));
        g.fill(bx, by + 2, bx + 40, by + 5, 0xFF1A1E24);
        g.fill(bx, by + 2, bx + Math.round(40 * f), by + 5, def.energy() ? 0xFF50E6FF : 0xFFE8C860);
        if (def.usesHeat()) {
            float heat = t.getFloatOr("heat", 0f) / 100f;
            boolean oh = t.getBooleanOr("oh", false);
            g.fill(x + 6, y + ph - 6, x + pw - 6, y + ph - 3, 0xFF1A1E24);
            int col = oh ? ((mc.player.tickCount / 3) % 2 == 0 ? 0xFFFF2020 : 0xFFFF9020) : (heat > 0.7f ? 0xFFFF7A30 : 0xFF50E6FF);
            g.fill(x + 6, y + ph - 6, x + 6 + Math.round((pw - 12) * heat), y + ph - 3, col);
            if (oh) g.text(font, "OVERHEAT", x + pw - 6 - font.width("OVERHEAT"), y + 35, 0xFFFF4040, false);
        }
        if (reloading) {
            long start = t.getLongOr("rstart", 0), tot = Math.max(1, t.getLongOr("rtot", 1));
            float p = Math.min(1f, (mc.level.getGameTime() - start) / (float) tot);
            g.fill(x + 6, y + ph + 2, x + pw - 6, y + ph + 5, 0xFF1A1E24);
            g.fill(x + 6, y + ph + 2, x + 6 + Math.round((pw - 12) * p), y + ph + 5, 0xFFFFFFFF);
            g.text(font, "RELOADING", x + 6, y + ph + 7, 0xFFB0B8C2, false);
        }
        int chg = t.getIntOr("chg", 0);
        if (chg > 0 && def.beh().equals("charge")) {
            int max = (int) def.p("charge", 44);
            g.fill(w / 2 - 30, h / 2 + 14, w / 2 + 30, h / 2 + 18, 0xAA101418);
            g.fill(w / 2 - 30, h / 2 + 14, w / 2 - 30 + Math.round(60f * chg / max), h / 2 + 18, chg >= max ? 0xFFFFFF80 : def.glow() | 0xFF000000);
        }
        if (def.beh().equals("unstable")) {
            float u = t.getFloatOr("unst", 0f) / 100f;
            g.text(font, "CORE", x + 6, y - 9, 0xFF8AFF70, false);
            g.fill(x + 34, y - 8, x + pw, y - 5, 0xFF1A1E24);
            g.fill(x + 34, y - 8, x + 34 + Math.round((pw - 34) * u), y - 5, u > 0.75f ? 0xFFFF4030 : 0xFF8AFF70);
        }
    }

    private void crosshair(GuiGraphicsExtractor g, int w, int h, GunDef def, GunStats st, Minecraft mc) {
        if (ClientState.aimBlend > 0.9f && st.zoomFactor(def) < 0.5f) return;
        float spread = def.spread() * st.spread * (ClientState.aimBlend > 0.5f ? 0.45f : (mc.player.isShiftKeyDown() ? 0.8f : 1f));
        double sp = mc.player.getDeltaMovement().horizontalDistance();
        if (sp > 0.08) spread *= 1f + (float) Math.min(1.0, sp * 4) * 0.7f;
        if (!mc.player.onGround()) spread *= 1.8f;
        int gap = 3 + Math.round(Math.min(26f, spread * 2.2f)), len = 4;
        int cx = w / 2, cy = h / 2, c = 0xCCFFFFFF;
        g.fill(cx - gap - len, cy, cx - gap, cy + 1, c);
        g.fill(cx + gap, cy, cx + gap + len, cy + 1, c);
        g.fill(cx, cy - gap - len, cx + 1, cy - gap, c);
        g.fill(cx, cy + gap, cx + 1, cy + gap + len, c);
    }

    private void scope(GuiGraphicsExtractor g, int w, int h, float zoom) {
        if (zoom >= 0.5f || ClientState.aimBlend < 0.35f) return;
        float t = Math.min(1f, (ClientState.aimBlend - 0.35f) / 0.6f);
        int a = (int) (255 * t);
        int cx = w / 2, cy = h / 2;
        int r = (int) (Math.min(w, h) * 0.46f);
        int black = (a << 24);
        g.fill(0, 0, w, cy - r, black); g.fill(0, cy + r, w, h, black);
        for (int y = cy - r; y < cy + r; y++) {
            int dy = y - cy;
            int hw = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(0, y, cx - hw, y + 1, black);
            g.fill(cx + hw, y, w, y + 1, black);
        }
        int cc = ((int) (210 * t) << 24);
        g.fill(cx - r, cy, cx - 6, cy + 1, cc); g.fill(cx + 6, cy, cx + r, cy + 1, cc);
        g.fill(cx, cy - r, cx + 1, cy - 6, cc); g.fill(cx, cy + 6, cx + 1, cy + r, cc);
        for (int i = 1; i <= 4; i++) { g.fill(cx + i * 14, cy - 2, cx + i * 14 + 1, cy + 3, cc); g.fill(cx - i * 14, cy - 2, cx - i * 14 + 1, cy + 3, cc); g.fill(cx - 2, cy + i * 14, cx + 3, cy + i * 14 + 1, cc); }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF3030);
    }

    private void hitMarker(GuiGraphicsExtractor g, int w, int h) {
        if (ClientState.hitMarkerTicks <= 0) return;
        int cx = w / 2, cy = h / 2;
        int c = switch (ClientState.hitMarkerKind) { case 2 -> 0xFFFF3030; case 1 -> 0xFFFFD040; default -> 0xFFFFFFFF; };
        int a = Math.min(255, ClientState.hitMarkerTicks * 45);
        c = (a << 24) | (c & 0xFFFFFF);
        int o = 5 + (8 - ClientState.hitMarkerTicks) / 3, l = ClientState.hitMarkerKind == 2 ? 6 : 4;
        for (int i = 0; i < l; i++) {
            g.fill(cx - o - i, cy - o - i, cx - o - i + 1, cy - o - i + 1, c);
            g.fill(cx + o + i, cy - o - i, cx + o + i + 1, cy - o - i + 1, c);
            g.fill(cx - o - i, cy + o + i, cx - o - i + 1, cy + o + i + 1, c);
            g.fill(cx + o + i, cy + o + i, cx + o + i + 1, cy + o + i + 1, c);
        }
    }

    private void lockOn(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        if (ClientState.lockEntity < 0 || mc.level == null) return;
        Entity e = mc.level.getEntity(ClientState.lockEntity);
        if (e == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
        Vec3 f = mc.player.getViewVector(1f);
        double fd = d.dot(f);
        if (fd < 0.5) return;
        Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(f).normalize();
        double fovY = Math.toRadians(mc.options.fov().get() * (double) (1f + (ScopeInfo.fovMultiplier(mc.player.getMainHandItem()) - 1f)));
        double ty = Math.tan(fovY / 2), tx = ty * w / (double) h;
        int sx = (int) (w / 2 + (d.dot(right) / fd) / tx * (w / 2.0));
        int sy = (int) (h / 2 - (d.dot(up) / fd) / ty * (h / 2.0));
        float p = ClientState.lockProgress / 100f;
        boolean locked = ClientState.lockLocked;
        int size = locked ? 14 : (int) (30 - 16 * p);
        int c = locked ? 0xFFFF3040 : 0xFFFFD040;
        int t = mc.player.tickCount;
        if (locked && (t / 3) % 2 == 0) c = 0xFFFFFFFF;
        // four brackets that close in on the target
        int b = 6;
        g.fill(sx - size, sy - size, sx - size + b, sy - size + 1, c); g.fill(sx - size, sy - size, sx - size + 1, sy - size + b, c);
        g.fill(sx + size - b, sy - size, sx + size, sy - size + 1, c); g.fill(sx + size - 1, sy - size, sx + size, sy - size + b, c);
        g.fill(sx - size, sy + size - 1, sx - size + b, sy + size, c); g.fill(sx - size, sy + size - b, sx - size + 1, sy + size, c);
        g.fill(sx + size - b, sy + size - 1, sx + size, sy + size, c); g.fill(sx + size - 1, sy + size - b, sx + size, sy + size, c);
        g.text(mc.font, locked ? "LOCKED" : "LOCK " + ClientState.lockProgress + "%", sx - mc.font.width(locked ? "LOCKED" : "LOCK 100%") / 2, sy + size + 4, c, true);
    }

    private void toasts(GuiGraphicsExtractor g, Font font, int w) {
        int y = 8;
        long now = System.currentTimeMillis();
        for (int i = ClientState.TOASTS.size() - 1; i >= 0; i--) {
            ClientState.Toast t = ClientState.TOASTS.get(i);
            long age = now - t.born();
            float in = Math.min(1f, age / 250f), out = age > 4300 ? Math.max(0f, 1f - (age - 4300) / 700f) : 1f;
            int tw = font.width(t.text()) + 14;
            int x = w - tw - 8 + (int) ((1f - in) * (tw + 10));
            int a = (int) (200 * out);
            g.fill(x, y, x + tw, y + 16, (a << 24) | 0x080A0E);
            g.fill(x, y, x + 2, y + 16, (0xFF000000) | t.color());
            g.text(font, t.text(), x + 8, y + 4, ((int) (255 * out) << 24) | (t.color() & 0xFFFFFF), false);
            y += 19;
        }
    }
}

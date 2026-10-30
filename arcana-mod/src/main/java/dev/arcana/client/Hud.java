package dev.arcana.client;

import dev.arcana.ArcanaConfig;
import dev.arcana.item.WandItem;
import dev.arcana.magic.Mastery;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.magic.SpellSlot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The spell HUD: selected spell, mana, cooldown slots, mastery, plus cinematic screen overlays. */
public final class Hud implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        cinematic(g, mc, pt);
        ItemStack wand = mc.player.getMainHandItem().getItem() instanceof WandItem ? mc.player.getMainHandItem()
            : mc.player.getOffhandItem().getItem() instanceof WandItem ? mc.player.getOffhandItem() : ItemStack.EMPTY;
        if (wand.isEmpty()) return;

        School school = WandItem.schoolOf(wand);
        int stage = WandItem.stageOf(wand);
        Spell sel = WandItem.selectedOf(wand);
        int w = g.guiWidth(), h = g.guiHeight();
        float scale = ArcanaConfig.HUD_SCALE.get().floatValue();
        int pw = 150, ph = 74;
        g.pose().pushPose();
        g.pose().translate(w - 8, h - 8, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-pw, -ph, 0);
        int rgb = WandItem.tintOf(wand, sel.isMimic() ? sel.mimicked() : school);
        float t = ClientState.tick + pt;

        // panel
        g.fill(0, 0, pw, ph, 0xAA0B0716);
        g.fillGradient(0, 0, pw, 2, Draw.argb(rgb, 255), Draw.argb(rgb, 40));
        g.renderOutline(0, 0, pw, ph, Draw.argb(rgb, 110));

        // selected spell icon with cooldown sweep
        int ix = 6, iy = 8, is = 34;
        g.fill(ix - 2, iy - 2, ix + is + 2, iy + is + 2, Draw.argb(rgb, 70));
        Draw.icon(g, sel, ix, iy, is);
        cooldownOverlay(g, sel, ix, iy, is, t);
        g.drawString(mc.font, sel.displayName(), ix + is + 8, iy + 1, 0xFFFFFFFF, true);
        Component stageName = school == School.NONE ? Component.translatable("stage.arcana.forbidden") : Component.translatable("stage.arcana." + stage);
        g.drawString(mc.font, stageName, ix + is + 8, iy + 12, Draw.argb(rgb, 255), false);

        // mana bar
        int bx = ix + is + 8, by = iy + 25, bw = pw - bx - 8, bh = 8;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF05030C);
        float frac = ClientState.max <= 0 ? 0 : Math.min(1f, ClientState.shownMana / ClientState.max);
        int fw = (int) (bw * frac);
        g.fillGradient(bx, by, bx + fw, by + bh, 0xFF5B8CFF, 0xFFB46CFF);
        int shimmer = (int) ((t * 1.5f) % (bw + 20)) - 10;
        if (fw > 4) g.fill(Math.max(bx, bx + shimmer), by, Math.min(bx + fw, bx + shimmer + 6), by + bh, 0x40FFFFFF);
        g.drawString(mc.font, (int) ClientState.mana + "/" + (int) ClientState.max, bx + 2, by + 9, 0xFFB9C7FF, false);

        // secondary / special / ultimate slots
        SpellSlot[] slots = {SpellSlot.SECONDARY, SpellSlot.SPECIAL, SpellSlot.ULTIMATE};
        net.minecraft.client.KeyMapping[] keys = {ClientKeys.SECONDARY, ClientKeys.SPECIAL, ClientKeys.ULTIMATE};
        for (int i = 0; i < 3; i++) {
            Spell s = Spell.of(school, slots[i]);
            int sx = 6 + i * 30, sy = 46, ss = 22;
            if (s == null) continue;
            boolean locked = school != School.NONE && s.minStage > stage;
            g.fill(sx - 1, sy - 1, sx + ss + 1, sy + ss + 1, Draw.argb(rgb, locked ? 25 : 80));
            Draw.icon(g, s, sx, sy, ss);
            if (locked) g.fill(sx, sy, sx + ss, sy + ss, 0xB0000000);
            else cooldownOverlay(g, s, sx, sy, ss, t);
            g.drawString(mc.font, keys[i].getTranslatedKeyMessage().getString(), sx + 1, sy + ss - 8, 0xFFFFFFFF, true);
        }

        // mastery
        if (school != School.NONE) {
            int xp = ClientState.xp[school.ordinal()];
            int lvl = Mastery.level(xp);
            int mx = 98, my = 52;
            g.drawString(mc.font, Component.translatable("hud.arcana.mastery", lvl), mx, my - 2, 0xFFE8D9FF, false);
            g.fill(mx, my + 9, pw - 8, my + 13, 0xFF05030C);
            g.fill(mx, my + 9, mx + (int) ((pw - 8 - mx) * Mastery.progress(xp)), my + 13, Draw.argb(rgb, 255));
        } else {
            g.drawString(mc.font, Component.translatable("hud.arcana.unbound"), 98, 50, 0xFFFF9AF0, false);
        }
        g.pose().popPose();
    }

    private static void cooldownOverlay(GuiGraphics g, Spell s, int x, int y, int size, float t) {
        int left = ClientState.cooldownLeft(s);
        if (left > 0) {
            int total = Math.max(left, s.baseCooldown <= 0 ? 1 : Math.max(s.baseCooldown, left));
            float frac = Math.min(1f, left / (float) total);
            int hgt = (int) (size * frac);
            g.fill(x, y + size - hgt, x + size, y + size, 0xB0101018);
            String txt = left >= 200 ? String.valueOf(left / 20) : String.format("%.1f", left / 20f);
            g.drawCenteredString(Minecraft.getInstance().font, txt, x + size / 2, y + size / 2 - 4, 0xFFFFFFFF);
        } else if (ClientState.readyFlash[s.ordinal()] > 0) {
            g.fill(x, y, x + size, y + size, Draw.argb(0xFFFFFF, ClientState.readyFlash[s.ordinal()] * 14));
        }
    }

    private static void cinematic(GuiGraphics g, Minecraft mc, float pt) {
        if (!ArcanaConfig.CINEMATICS.get()) return;
        float s = ClientState.cinematicStrength(pt);
        if (s <= 0) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientState.cinematicKind == 1) {
            g.fill(0, 0, w, h, Draw.argb(0x8890A0, (int) (95 * s)));
            int bar = (int) (h * 0.1f * s);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        } else {
            int edge = (int) (150 * s);
            g.fillGradient(0, 0, w, edge, Draw.argb(ClientState.cinematicRgb, (int) (170 * s)), 0x00000000);
            g.fillGradient(0, h - edge, w, h, 0x00000000, Draw.argb(ClientState.cinematicRgb, (int) (170 * s)));
            g.fill(0, 0, w, h, Draw.argb(ClientState.cinematicRgb, (int) (55 * s * s)));
            int bar = (int) (h * 0.07f * s);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
    }
}

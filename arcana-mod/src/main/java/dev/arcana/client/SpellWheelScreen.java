package dev.arcana.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcana.item.WandItem;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.net.Net;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Hold-to-open radial wheel for choosing the spell bound to right-click. Release the key to confirm. */
public class SpellWheelScreen extends Screen {
    private final ItemStack wand;
    private final List<Spell> spells;
    private int hovered = -1;
    private float open;

    public SpellWheelScreen(ItemStack wand) {
        super(Component.translatable("hud.arcana.wheel"));
        this.wand = wand;
        this.spells = Spell.forWand(WandItem.schoolOf(wand), WandItem.stageOf(wand));
    }

    @Override public boolean isPauseScreen() { return false; }

    private boolean available(Spell s) {
        if (!s.isMimic()) return true;
        return (ClientState.recentMask & (1 << s.mimicked().ordinal())) != 0 || (minecraft != null && minecraft.player != null && minecraft.player.isCreative());
    }

    @Override
    public void tick() {
        if (!isWheelKeyDown()) {
            confirm();
            onClose();
        }
    }

    private boolean isWheelKeyDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), ClientKeys.WHEEL.getKey().getValue());
    }

    private void confirm() {
        if (hovered >= 0 && hovered < spells.size() && available(spells.get(hovered))) {
            PacketDistributor.sendToServer(new Net.SelectSpell(spells.get(hovered).id));
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        confirm();
        onClose();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        open = Math.min(1f, open + 0.12f);
        float ease = 1f - (1f - open) * (1f - open);
        float cx = width / 2f, cy = height / 2f;
        g.fill(0, 0, width, height, Draw.argb(0x05020D, (int) (150 * ease)));
        int n = spells.size();
        if (n == 0) return;
        float inner = 46 * ease, outer = 112 * ease;
        double ang = Math.atan2(mx - cx, -(my - cy));
        if (ang < 0) ang += Math.PI * 2;
        double dist = Math.hypot(mx - cx, my - cy);
        float seg = (float) (Math.PI * 2 / n);
        hovered = dist > 22 ? (int) ((ang + seg / 2) / seg) % n : -1;
        School school = WandItem.schoolOf(wand);
        int base = WandItem.tintOf(wand, school);
        Spell current = WandItem.selectedOf(wand);
        for (int i = 0; i < n; i++) {
            Spell s = spells.get(i);
            float a0 = i * seg - seg / 2 + 0.03f, a1 = i * seg + seg / 2 - 0.03f;
            boolean hov = i == hovered, ok = available(s);
            int col = s.isMimic() ? s.mimicked().color : base;
            float o = outer + (hov ? 8 : 0);
            int alphaIn = ok ? (hov ? 200 : 110) : 50, alphaOut = ok ? (hov ? 235 : 150) : 70;
            Draw.wedge(g, cx, cy, inner, o, a0, a1, Draw.argb(col, alphaIn), Draw.argb(col, alphaOut));
            if (s == current) Draw.wedge(g, cx, cy, o - 3, o, a0, a1, 0xFFFFFFFF, 0xFFFFFFFF);
            double mid = i * seg;
            float r = (inner + o) / 2f;
            int ix = (int) (cx + Math.sin(mid) * r) - 14, iy = (int) (cy - Math.cos(mid) * r) - 14;
            Draw.icon(g, s, ix, iy, 28);
            if (!ok) g.fill(ix, iy, ix + 28, iy + 28, 0xA0000000);
        }
        g.drawCenteredString(font, Component.translatable("hud.arcana.wheel"), (int) cx, (int) cy - 4, 0xFFAAAAFF);
        if (hovered >= 0) {
            Spell s = spells.get(hovered);
            g.drawCenteredString(font, s.displayName(), (int) cx, (int) (cy + outer + 18), 0xFFFFFFFF);
            g.drawCenteredString(font, available(s) ? s.description() : Component.translatable("cast.arcana.fail.mimic_locked"), (int) cx, (int) (cy + outer + 30), available(s) ? 0xFFCCCCDD : 0xFFFF8888);
            g.drawCenteredString(font, Component.translatable("hud.arcana.cost", s.baseMana, s.baseCooldown / 20), (int) cx, (int) (cy + outer + 42), 0xFF88AAFF);
        }
    }
}

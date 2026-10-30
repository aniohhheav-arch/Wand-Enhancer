package dev.arcana.client;

import dev.arcana.ArcanaConfig;
import dev.arcana.item.Core;
import dev.arcana.item.Customize;
import dev.arcana.item.Fx;
import dev.arcana.item.Handle;
import dev.arcana.item.Rune;
import dev.arcana.item.WandItem;
import dev.arcana.item.WandStyle;
import dev.arcana.magic.CastService;
import dev.arcana.magic.Mastery;
import dev.arcana.magic.School;
import dev.arcana.net.Net;
import dev.arcana.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Wand evolution and customisation interface. All changes are requests; the server validates costs. */
public class WandScreen extends Screen {
    private static final int ROW_H = 24;
    private Button evolve;
    private int left, top;

    public WandScreen() {
        super(Component.translatable("screen.arcana.wand"));
    }

    private ItemStack wand() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : CastService.heldWand(minecraft.player);
    }

    @Override
    protected void init() {
        left = width / 2 - 170;
        top = height / 2 - 100;
        for (int f = 0; f < WandStyle.FIELDS; f++) {
            final int field = f;
            int y = top + 62 + f * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> step(field, -1)).bounds(left + 150, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> step(field, 1)).bounds(left + 320, y, 20, 20).build());
        }
        evolve = addRenderableWidget(Button.builder(Component.translatable("screen.arcana.evolve"), b -> PacketDistributor.sendToServer(new Net.EvolveReq()))
            .bounds(left, top + 62 + 5 * ROW_H, 140, 20).build());
    }

    private void step(int field, int dir) {
        ItemStack w = wand();
        if (w.isEmpty()) return;
        int cur = WandItem.styleOf(w).get(field);
        int lo = field == Customize.COLOR ? -1 : 0;
        int count = Customize.optionCount(field) - (field == Customize.COLOR ? 1 : 0);
        int range = count - lo;
        int next = lo + Math.floorMod(cur - lo + dir, range);
        PacketDistributor.sendToServer(new Net.CustomizeReq(field, next));
    }

    private Component valueName(int field, int v) {
        return switch (field) {
            case Customize.HANDLE -> Component.translatable("handle.arcana." + Handle.byIndex(v).id);
            case Customize.COLOR -> v < 0 ? Component.translatable("color.arcana.school") : Component.translatable("color.minecraft." + DyeColor.byId(v).getName());
            case Customize.CORE -> Component.translatable("core.arcana." + Core.byIndex(v).id);
            case Customize.FX -> Component.translatable("fx.arcana." + Fx.byIndex(v).id);
            default -> Component.translatable("rune.arcana." + Rune.byIndex(v).id);
        };
    }

    private int count(Item item) {
        int n = 0;
        var inv = minecraft.player.getInventory();
        for (ItemStack s : inv.items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (wand().isEmpty()) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        ItemStack w = wand();
        if (w.isEmpty()) return;
        School school = WandItem.schoolOf(w);
        int stage = WandItem.stageOf(w);
        int rgb = WandItem.tintOf(w, school);
        float t = ClientState.tick + pt;
        g.fill(0, 0, width, height, 0xC0050310);
        // drifting motes
        for (int i = 0; i < 40; i++) {
            float x = (i * 97 + t * (0.15f + (i % 5) * 0.05f)) % width;
            float y = height - ((i * 53 + t * (0.3f + (i % 7) * 0.06f)) % height);
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, Draw.argb(rgb, 50 + (i * 13) % 100));
        }
        int pw = 350, ph = 62 + 6 * ROW_H + 40;
        g.fill(left - 10, top - 10, left + pw, top + ph, 0xE00B0716);
        g.renderOutline(left - 10, top - 10, pw + 10, ph + 10, Draw.argb(rgb, 160));
        g.fillGradient(left - 10, top - 10, left + pw, top - 8, Draw.argb(rgb, 255), Draw.argb(rgb, 30));

        g.renderItem(w, left, top);
        g.drawString(font, w.getHoverName(), left + 24, top + 1, 0xFFFFFFFF, true);
        Component stageName = school == School.NONE ? Component.translatable("stage.arcana.forbidden") : Component.translatable("stage.arcana." + stage);
        g.drawString(font, stageName, left + 24, top + 12, Draw.argb(rgb, 255), false);

        if (school != School.NONE) {
            int xp = ClientState.xp[school.ordinal()];
            g.drawString(font, Component.translatable("hud.arcana.mastery", Mastery.level(xp)), left + 200, top + 1, 0xFFE8D9FF, false);
            g.fill(left + 200, top + 14, left + 330, top + 19, 0xFF05030C);
            g.fill(left + 200, top + 14, left + 200 + (int) (130 * Mastery.progress(xp)), top + 19, Draw.argb(rgb, 255));
        }

        WandStyle st = WandItem.styleOf(w);
        for (int f = 0; f < WandStyle.FIELDS; f++) {
            int y = top + 62 + f * ROW_H;
            g.drawString(font, Component.translatable("customize.arcana.field." + f), left, y + 6, 0xFFB9B2D6, false);
            Component name = valueName(f, st.get(f));
            g.drawCenteredString(font, name, left + 245, y + 6, 0xFFFFFFFF);
        }

        // evolution requirements
        int y = top + 62 + 5 * ROW_H + 0;
        if (school == School.NONE || stage >= 2) {
            evolve.active = false;
            g.drawString(font, Component.translatable(school == School.NONE ? "screen.arcana.no_evolution" : "screen.arcana.max_stage"), left + 150, y + 6, 0xFFFFD27F, false);
        } else {
            int needM = stage == 0 ? ArcanaConfig.AWAKEN_MASTERY.get() : ArcanaConfig.ASCEND_MASTERY.get();
            int needC = stage == 0 ? ArcanaConfig.AWAKEN_CRYSTALS.get() : ArcanaConfig.ASCEND_CRYSTALS.get();
            Item rare = stage == 0 ? ModItems.AWAKENING_SHARD.get() : ModItems.ASCENSION_CORE.get();
            Item crystal = ModItems.CRYSTALS.get(school).get();
            int lvl = Mastery.level(ClientState.xp[school.ordinal()]);
            boolean creative = minecraft.player.isCreative();
            boolean okM = lvl >= needM, okR = creative || count(rare) >= 1, okC = creative || count(crystal) >= needC;
            evolve.active = okM && okR && okC;
            g.drawString(font, Component.translatable("screen.arcana.req.mastery", needM), left + 150, y - 1, okM ? 0xFF8CFF8C : 0xFFFF7777, false);
            g.drawString(font, Component.translatable("screen.arcana.req.item", 1, rare.getDescription()), left + 150, y + 9, okR ? 0xFF8CFF8C : 0xFFFF7777, false);
            g.drawString(font, Component.translatable("screen.arcana.req.item", needC, crystal.getDescription()), left + 150, y + 19, okC ? 0xFF8CFF8C : 0xFFFF7777, false);
        }
        g.drawString(font, Component.translatable("screen.arcana.cost_hint"), left, top + ph - 24, 0xFF8A83A8, false);
        super.render(g, mx, my, pt);
    }
}

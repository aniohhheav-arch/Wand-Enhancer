package dev.mysticarts.client.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.mysticarts.MysticArts;
import dev.mysticarts.client.AbilityInfo;
import dev.mysticarts.client.ClientPower;
import dev.mysticarts.client.KeyBindings;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Source;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.util.ColorUtil;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * The ability wheel. Hold the radial key, aim at an ability, release to select it. Scroll or click the tabs (or press
 * 1-8) to switch between the Mystic Arts, each socketed stone and the combination powers.
 */
public class RadialMenuScreen extends Screen {
    private static final ResourceLocation GLOW = MysticArts.id("textures/gui/radial_glow.png");
    private static final ResourceLocation RING = MysticArts.id("textures/gui/radial_ring.png");
    private static final int ICON = 24;

    private Source source;
    private int hovered = -1;
    private int lastHovered = -1;
    private float open;
    private long openedAt;

    public RadialMenuScreen() {
        super(Component.translatable("screen.mysticarts.radial"));
        this.source = KeyBindings.currentSource();
        this.openedAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        playSound(MaSounds.UI_OPEN.get(), 1f);
    }

    private void playSound(net.minecraft.sounds.SoundEvent s, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.5f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int c = ColorUtil.scale(source.color, 0.15f);
        g.fillGradient(0, 0, width, height, 0x88000000 | c, 0xCC050208);
    }

    private List<Ability> abilities() {
        return Ability.of(source);
    }

    private int[] slot(int i, int n, int cx, int cy, float radius) {
        boolean twoRings = n > 16;
        int perRing = twoRings ? (n + 1) / 2 : n;
        int ring = twoRings && i >= perRing ? 1 : 0;
        int idx = ring == 1 ? i - perRing : i;
        int count = ring == 1 ? n - perRing : perRing;
        float r = twoRings ? (ring == 0 ? radius * 0.62f : radius) : radius;
        double a = -Math.PI / 2 + (idx + (ring == 1 ? 0.5 : 0)) * Math.PI * 2 / count;
        return new int[] {cx + (int) (Math.cos(a) * r * open), cy + (int) (Math.sin(a) * r * open)};
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        open = Mth.clamp((System.currentTimeMillis() - openedAt) / 140f, 0f, 1f);
        open = 1f - (1f - open) * (1f - open);
        int cx = width / 2, cy = height / 2 + 10;
        float radius = Math.min(width, height) * 0.34f;
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        List<Ability> list = abilities();
        int n = list.size();

        // rotating rune ring behind the wheel
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        int ringSize = (int) (radius * 2.5f * open);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(time * 0.15f));
        RenderSystem.setShaderColor(ColorUtil.r(source.color), ColorUtil.g(source.color), ColorUtil.b(source.color), 0.55f * open);
        g.blit(RING, -ringSize / 2, -ringSize / 2, ringSize, ringSize, 0, 0, 256, 256, 256, 256);
        g.pose().popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();

        // hover: by angle and distance from the centre
        double dx = mouseX - cx, dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        hovered = -1;
        if (dist > 20) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int[] p = slot(i, n, cx, cy, radius);
                double d = (p[0] - mouseX) * (p[0] - mouseX) + (p[1] - mouseY) * (p[1] - mouseY);
                if (d < best) {
                    best = d;
                    hovered = i;
                }
            }
            if (best > (radius * 0.5) * (radius * 0.5)) hovered = -1;
        }
        if (hovered != lastHovered && hovered >= 0) playSound(MaSounds.UI_HOVER.get(), 1f + hovered * 0.01f);
        lastHovered = hovered;

        Ability selected = ClientPower.selected(source);
        for (int i = 0; i < n; i++) {
            Ability a = list.get(i);
            int[] p = slot(i, n, cx, cy, radius);
            boolean hover = i == hovered;
            String lock = AbilityInfo.locked(a);
            float scale = hover ? 1.35f : a == selected ? 1.12f : 1f;
            int size = (int) (ICON * scale);
            int x = p[0] - size / 2, y = p[1] - size / 2;
            int col = a.color();
            // glow halo
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            float pulse = 0.6f + 0.4f * Mth.sin(time * 4f + i);
            float halo = (hover ? 0.9f * pulse : a == selected ? 0.55f : 0.25f) * (lock != null ? 0.3f : 1f);
            RenderSystem.setShaderColor(ColorUtil.r(col), ColorUtil.g(col), ColorUtil.b(col), halo);
            int gs = size * 2;
            g.blit(GLOW, p[0] - gs / 2, p[1] - gs / 2, gs, gs, 0, 0, 64, 64, 64, 64);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(lock != null ? 0.35f : 1f, lock != null ? 0.35f : 1f, lock != null ? 0.35f : 1f, 1f);
            g.blit(AbilityInfo.icon(a), x, y, size, size, 0, 0, 32, 32, 32, 32);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            float cd = ClientPower.cooldownFraction(a);
            if (cd > 0) g.fill(x, y + (int) (size * (1 - cd)), x + size, y + size, 0xAA000000);
            if (a.ultimate()) {
                int frame = 0xFFFFD27A;
                g.fill(x - 1, y - 1, x + size + 1, y, frame);
                g.fill(x - 1, y + size, x + size + 1, y + size + 1, frame);
                g.fill(x - 1, y, x, y + size, frame);
                g.fill(x + size, y, x + size + 1, y + size, frame);
            }
        }

        // source tabs
        List<Source> sources = KeyBindings.available();
        int tabW = 22, tabsX = cx - sources.size() * tabW / 2;
        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            int x = tabsX + i * tabW, y = 12;
            boolean active = s == source;
            g.fill(x, y, x + 20, y + 20, active ? 0xCC000000 | ColorUtil.scale(s.color, 0.5f) : 0x66000000);
            if (active) g.fill(x, y + 20, x + 20, y + 21, 0xFF000000 | s.color);
            g.blit(MysticArts.id("textures/gui/source/" + s.id + ".png"), x + 2, y + 2, 16, 16, 0, 0, 16, 16, 16, 16);
        }
        g.drawCenteredString(font, Component.translatable("source.mysticarts." + source.id).withColor(source.color), cx, 38, 0xFFFFFF);

        // centre panel
        Ability show = hovered >= 0 ? list.get(hovered) : selected;
        int ty = cy - 24;
        g.drawCenteredString(font, Component.translatable(show.translationKey()).withColor(show.color()), cx, ty, 0xFFFFFF);
        List<FormattedCharSequence> lines = font.split(Component.translatable(show.translationKey() + ".desc"), (int) Math.max(120, radius * 1.0f));
        for (int i = 0; i < Math.min(5, lines.size()); i++) g.drawCenteredString(font, lines.get(i), cx, ty + 12 + i * 10, 0xCFCFCF);
        String lock = AbilityInfo.locked(show);
        int iy = ty + 14 + Math.min(5, lines.size()) * 10;
        if (lock != null) {
            g.drawCenteredString(font, Component.translatable(lock, Component.translatable(show.translationKey())), cx, iy, 0xFF6A5A);
        } else {
            String energy = show.cosmic() ? "cosmic" : "mystic";
            Component stats = Component.translatable("screen.mysticarts.stats", String.format("%.0f", AbilityInfo.cost(show)),
                    Component.translatable("energy.mysticarts." + energy), String.format("%.1f", show.cooldown / 20f));
            g.drawCenteredString(font, stats, cx, iy, 0x9FB8D8);
            if (show.ultimate()) g.drawCenteredString(font, Component.translatable("screen.mysticarts.ultimate"), cx, iy + 10, 0xFFD27A);
        }
        g.drawCenteredString(font, Component.translatable("screen.mysticarts.radial_hint"), cx, height - 18, 0x808080);
        super.render(g, mouseX, mouseY, partial);
    }

    private void switchSource(int dir) {
        List<Source> sources = KeyBindings.available();
        int i = sources.indexOf(source);
        source = sources.get(Math.floorMod(i + dir, sources.size()));
        hovered = -1;
        playSound(MaSounds.STONE_SELECT.get(), 1f);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) switchSource(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Source> sources = KeyBindings.available();
        int tabW = 22, tabsX = width / 2 - sources.size() * tabW / 2;
        if (mouseY >= 12 && mouseY <= 32) {
            int i = (int) ((mouseX - tabsX) / tabW);
            if (i >= 0 && i < sources.size()) {
                source = sources.get(i);
                playSound(MaSounds.STONE_SELECT.get(), 1f);
                return true;
            }
        }
        if (button == 0) {
            choose();
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            List<Source> sources = KeyBindings.available();
            int i = key - GLFW.GLFW_KEY_1;
            if (i < sources.size()) {
                source = sources.get(i);
                playSound(MaSounds.STONE_SELECT.get(), 1f);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (KeyBindings.RADIAL.matches(key, scan)) {
            choose();
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    private void choose() {
        List<Ability> list = abilities();
        if (hovered >= 0 && hovered < list.size()) {
            KeyBindings.select(list.get(hovered));
        } else {
            KeyBindings.select(ClientPower.selected(source));
        }
        onClose();
    }
}

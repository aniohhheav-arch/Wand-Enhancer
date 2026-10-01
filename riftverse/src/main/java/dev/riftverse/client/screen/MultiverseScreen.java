package dev.riftverse.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftverse.client.render.Fullscreen;
import dev.riftverse.client.render.RvShaders;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseEntry;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.Archetype;
import dev.riftverse.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Multiverse Console: browse every known reality, step into one, open a gate, imprint a Dimensional Key, roll a
 * random universe, or describe an entirely new one and watch it manifest.
 */
public class MultiverseScreen extends Screen {
    private static final int[] TABS = {-1, UniverseEntry.PRIME, UniverseEntry.DISCOVERED, UniverseEntry.MANIFESTED};
    private static final String[] TAB_KEYS = {"all", "prime", "discovered", "manifested"};
    private static final int ROW = 24;
    private static final int PAD = 12;
    private static final int PANEL_BG = 0xB0070512;
    private static final int TEXT_DIM = 0x8C8AA8;

    private final BlockPos console;
    private final List<UniverseEntry> entries;
    private int manifestsLeft;

    private int tab;
    private double scroll;
    @Nullable
    private UniverseEntry selected;
    private String promptText = "";
    private List<String> notes = List.of();
    private long notesAt;
    private float pulse;
    private float pulseO;
    private final float[] colorA = {0.55f, 0.35f, 1f};
    private final float[] colorB = {1f, 0.3f, 0.8f};

    private EditBox prompt;
    private Button travel;
    private Button gate;
    private Button imprint;
    private Button variant;
    private Button manifest;

    private int listX, listY, listW, listH;
    private int detailX, detailY, detailW, detailH;
    private int barY;

    public MultiverseScreen(BlockPos console, List<UniverseEntry> entries, int manifestsLeft) {
        super(Component.translatable("screen.riftverse.multiverse.title"));
        this.console = console;
        this.entries = new ArrayList<>(entries);
        this.manifestsLeft = manifestsLeft;
        if (!this.entries.isEmpty()) this.selected = this.entries.get(0);
        snapColors();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        listW = Mth.clamp(width / 3, 150, 210);
        detailW = Mth.clamp(width / 3, 160, 220);
        listX = PAD;
        listY = 46;
        barY = height - 34;
        listH = barY - listY - 10;
        detailX = width - PAD - detailW;
        detailY = listY;
        detailH = listH;

        int bw = (detailW - 18) / 2;
        int by = detailY + detailH - 48;
        travel = addRenderableWidget(Button.builder(Component.translatable("screen.riftverse.multiverse.travel"), b -> act(Payloads.BrowserAction.TRAVEL_UNIVERSE, true))
                .bounds(detailX + 6, by, bw, 18).build());
        gate = addRenderableWidget(Button.builder(Component.translatable("screen.riftverse.multiverse.open_gate"), b -> act(Payloads.BrowserAction.OPEN_GATE, true))
                .bounds(detailX + 12 + bw, by, bw, 18).build());
        imprint = addRenderableWidget(Button.builder(Component.translatable("screen.riftverse.multiverse.imprint"), b -> act(Payloads.BrowserAction.IMPRINT_KEY, false))
                .bounds(detailX + 6, by + 22, bw, 18).build());
        variant = addRenderableWidget(Button.builder(Component.translatable("screen.riftverse.multiverse.variant"), b -> act(Payloads.BrowserAction.TRAVEL_ARCHETYPE, true))
                .bounds(detailX + 12 + bw, by + 22, bw, 18).build());

        int randomW = 96;
        int manifestW = 72;
        int promptW = width - PAD * 2 - randomW - manifestW - 12;
        prompt = new EditBox(font, PAD, barY, promptW, 20, Component.translatable("screen.riftverse.multiverse.prompt_hint"));
        prompt.setMaxLength(150);
        prompt.setHint(Component.translatable("screen.riftverse.multiverse.prompt_hint").withColor(TEXT_DIM));
        prompt.setValue(promptText);
        prompt.setResponder(s -> promptText = s);
        addRenderableWidget(prompt);
        manifest = addRenderableWidget(Button.builder(Component.translatable("screen.riftverse.multiverse.manifest"), b -> submitPrompt())
                .bounds(PAD + promptW + 6, barY, manifestW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.riftverse.multiverse.random"), b -> {
            send(Payloads.BrowserAction.TRAVEL_RANDOM, 0L, 0, "");
            onClose();
        }).bounds(width - PAD - randomW, barY, randomW, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        boolean has = selected != null;
        travel.active = has;
        gate.active = has;
        imprint.active = has;
        variant.active = has;
        manifest.active = manifestsLeft > 0 && promptText.trim().length() >= 3;
    }

    // ------------------------------------------------------------------ actions

    private void send(int action, long universe, int archetype, String text) {
        PacketDistributor.sendToServer(new Payloads.BrowserAction(console, action, universe, archetype, text));
    }

    private void act(int action, boolean close) {
        if (selected == null) return;
        send(action, selected.id(), selected.archetype(), "");
        if (close) onClose();
    }

    private void submitPrompt() {
        String text = promptText.trim();
        if (text.length() < 3 || manifestsLeft <= 0) return;
        send(Payloads.BrowserAction.MANIFEST, 0L, 0, text);
        pulse = 0.6f;
    }

    /** Called when the server answers a manifest request with the freshly created universe. */
    public void onManifested(UniverseEntry entry, List<String> interpreterNotes) {
        entries.removeIf(e -> e.id() == entry.id());
        entries.add(0, entry);
        if (TABS[tab] != -1 && TABS[tab] != UniverseEntry.MANIFESTED) tab = 0;
        scroll = 0;
        selected = entry;
        notes = List.copyOf(interpreterNotes);
        notesAt = Util.getMillis();
        manifestsLeft = Math.max(0, manifestsLeft - 1);
        pulse = 1f;
        promptText = "";
        if (prompt != null) prompt.setValue("");
        updateButtons();
    }

    private List<UniverseEntry> visible() {
        int filter = TABS[tab];
        if (filter == -1) return entries;
        List<UniverseEntry> out = new ArrayList<>();
        for (UniverseEntry e : entries) if (e.category() == filter) out.add(e);
        return out;
    }

    private void select(UniverseEntry e) {
        if (e == selected) return;
        selected = e;
        pulse = Math.max(pulse, 0.45f);
        notes = List.of();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(RvSounds.UI_SELECT.get(), 1.1f, 0.5f));
        updateButtons();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int tabW = listW / TABS.length;
            if (my >= listY && my < listY + 14 && mx >= listX && mx < listX + listW) {
                int t = Mth.clamp((int) ((mx - listX) / tabW), 0, TABS.length - 1);
                if (t != tab) {
                    tab = t;
                    scroll = 0;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(RvSounds.UI_SELECT.get(), 1.4f, 0.35f));
                }
                return true;
            }
            UniverseEntry hit = rowAt(mx, my);
            if (hit != null) {
                select(hit);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll = Mth.clamp(scroll - sy * ROW, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (prompt.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            submitPrompt();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private double maxScroll() {
        return Math.max(0, visible().size() * ROW - (listH - 20));
    }

    @Nullable
    private UniverseEntry rowAt(double mx, double my) {
        int top = listY + 18;
        if (mx < listX || mx >= listX + listW || my < top || my >= listY + listH) return null;
        int index = (int) ((my - top + scroll) / ROW);
        List<UniverseEntry> list = visible();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void tick() {
        super.tick();
        pulseO = pulse;
        pulse *= 0.93f;
        int a = selected != null ? selected.color() : 0x8F6BFF;
        int b = selected != null ? ColorUtil.shiftHue(Archetype.byId(selected.archetype()).signatureColor, 0.12f) : 0xFF4FD8;
        approach(colorA, a, 0.12f);
        approach(colorB, b, 0.12f);
        updateButtons();
    }

    private static void approach(float[] c, int rgb, float k) {
        c[0] += (ColorUtil.r(rgb) - c[0]) * k;
        c[1] += (ColorUtil.g(rgb) - c[1]) * k;
        c[2] += (ColorUtil.b(rgb) - c[2]) * k;
    }

    private void snapColors() {
        if (selected == null) return;
        approach(colorA, selected.color(), 1f);
        approach(colorB, ColorUtil.shiftHue(Archetype.byId(selected.archetype()).signatureColor, 0.12f), 1f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawCosmos(g, partial);
        drawHeader(g);
        drawList(g, mouseX, mouseY);
        drawDetail(g);
        drawCenter(g, partial);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        String left = Component.translatable("screen.riftverse.multiverse.manifests_left", manifestsLeft).getString();
        g.drawString(font, left, PAD, barY - 11, manifestsLeft > 0 ? 0xB8A8FF : 0xFF6A6A, false);
    }

    private void drawCosmos(GuiGraphics g, float partial) {
        ShaderInstance shader = RvShaders.cosmos;
        if (shader == null) {
            g.fill(0, 0, width, height, 0xFF05030C);
            return;
        }
        g.flush();
        float p = Mth.lerp(partial, pulseO, pulse);
        shader.safeGetUniform("ColorA").set(colorA[0], colorA[1], colorA[2]);
        shader.safeGetUniform("ColorB").set(colorB[0], colorB[1], colorB[2]);
        shader.safeGetUniform("CosmosParams").set((float) width / Math.max(1, height), p, 1f + p * 1.5f, 1f + p * 0.6f);
        RenderSystem.disableDepthTest();
        Fullscreen.draw(shader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    private void drawHeader(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(width / 2f, 12, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        String title = spaced(getTitle().getString().toUpperCase());
        g.drawString(font, title, -font.width(title) / 2, 0, 0xF0E8FF, true);
        g.pose().popPose();
        String sub = Component.translatable("screen.riftverse.multiverse.subtitle", entries.size()).getString();
        g.drawString(font, sub, (width - font.width(sub)) / 2, 30, TEXT_DIM, false);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        int edge = 0x60000000 | (accent & 0xFFFFFF);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
        int corner = 0xFF000000 | (accent & 0xFFFFFF);
        g.fill(x - 1, y - 1, x + 8, y + 1, corner);
        g.fill(x - 1, y - 1, x + 1, y + 8, corner);
        g.fill(x + w - 8, y + h - 1, x + w + 1, y + h + 1, corner);
        g.fill(x + w - 1, y + h - 8, x + w + 1, y + h + 1, corner);
    }

    private int accent() {
        return ColorUtil.rgb(colorA[0], colorA[1], colorA[2]);
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        panel(g, listX, listY, listW, listH, accent());
        int tabW = listW / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int x = listX + i * tabW;
            boolean on = i == tab;
            if (on) g.fill(x + 1, listY + 1, x + tabW - 1, listY + 14, 0x40000000 | (accent() & 0xFFFFFF));
            String label = Component.translatable("screen.riftverse.multiverse.tab." + TAB_KEYS[i]).getString();
            g.drawString(font, label, x + (tabW - font.width(label)) / 2, listY + 3, on ? 0xFFFFFF : TEXT_DIM, false);
        }
        List<UniverseEntry> list = visible();
        int top = listY + 18;
        g.enableScissor(listX + 1, top, listX + listW - 1, listY + listH - 1);
        if (list.isEmpty()) {
            String empty = Component.translatable("screen.riftverse.multiverse.empty").getString();
            g.drawString(font, empty, listX + (listW - font.width(empty)) / 2, top + 12, TEXT_DIM, false);
        }
        UniverseEntry hover = rowAt(mouseX, mouseY);
        for (int i = 0; i < list.size(); i++) {
            int y = top + i * ROW - (int) scroll;
            if (y + ROW < top || y > listY + listH) continue;
            UniverseEntry e = list.get(i);
            boolean sel = e == selected;
            if (sel) g.fill(listX + 2, y, listX + listW - 2, y + ROW - 2, 0x50000000 | (e.color() & 0xFFFFFF));
            else if (e == hover) g.fill(listX + 2, y, listX + listW - 2, y + ROW - 2, 0x25FFFFFF);
            g.fill(listX + 5, y + 4, listX + 9, y + ROW - 6, 0xFF000000 | e.color());
            g.drawString(font, font.plainSubstrByWidth(e.name(), listW - 22), listX + 14, y + 3, sel ? 0xFFFFFF : 0xE0DCF0, false);
            String meta = e.designation() + "  " + tagFor(e.category());
            g.drawString(font, font.plainSubstrByWidth(meta, listW - 22), listX + 14, y + 13, TEXT_DIM, false);
        }
        g.disableScissor();
        double max = maxScroll();
        if (max > 0) {
            int track = listH - 22;
            int thumb = Math.max(12, (int) (track * (listH - 20) / (double) (list.size() * ROW)));
            int ty = top + (int) ((track - thumb) * (scroll / max));
            g.fill(listX + listW - 3, ty, listX + listW - 1, ty + thumb, 0x90FFFFFF);
        }
    }

    private static String tagFor(int category) {
        String key = switch (category) {
            case UniverseEntry.PRIME -> "prime";
            case UniverseEntry.MANIFESTED -> "manifested";
            default -> "discovered";
        };
        return Component.translatable("screen.riftverse.multiverse.category." + key).getString();
    }

    private void drawDetail(GuiGraphics g) {
        panel(g, detailX, detailY, detailW, detailH, accent());
        if (selected == null) {
            String hint = Component.translatable("screen.riftverse.multiverse.select").getString();
            List<FormattedCharSequence> lines = font.split(FormattedText.of(hint), detailW - 16);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), detailX + 8, detailY + 10 + i * 10, TEXT_DIM, false);
            return;
        }
        UniverseEntry e = selected;
        int x = detailX + 8;
        int y = detailY + 8;
        int w = detailW - 16;
        List<FormattedCharSequence> name = font.split(FormattedText.of(e.name()), (int) (w / 1.3f));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.3f, 1.3f, 1f);
        for (int i = 0; i < name.size() && i < 2; i++) g.drawString(font, name.get(i), 0, i * 10, 0xFF000000 | ColorUtil.lerp(e.color(), 0xFFFFFF, 0.35f), true);
        g.pose().popPose();
        y += Math.min(2, name.size()) * 13 + 2;
        Archetype arch = Archetype.byId(e.archetype());
        g.drawString(font, e.designation() + " · " + arch.displayName, x, y, TEXT_DIM, false);
        y += 11;
        g.fill(x, y, x + w, y + 1, 0x50000000 | (e.color() & 0xFFFFFF));
        y += 5;
        int limit = detailY + detailH - 54;
        if (!e.prompt().isEmpty()) {
            for (FormattedCharSequence line : font.split(FormattedText.of("“" + e.prompt() + "”"), w)) {
                if (y > limit - 10) break;
                g.drawString(font, line, x, y, 0xFFD9A8, false);
                y += 10;
            }
            y += 3;
        }
        for (FormattedCharSequence line : font.split(FormattedText.of(e.description()), w)) {
            if (y > limit - 10) break;
            g.drawString(font, line, x, y, 0xD8D4EA, false);
            y += 10;
        }
    }

    /** The space between the panels: the selected reality's sigil, and the interpreter's notes after a manifest. */
    private void drawCenter(GuiGraphics g, float partial) {
        int cx = width / 2;
        int cy = (listY + barY) / 2;
        if (selected != null) {
            String d = spaced(selected.designation());
            g.drawString(font, d, cx - font.width(d) / 2, cy - 4, 0xC0FFFFFF, false);
        }
        if (notes.isEmpty()) return;
        long elapsed = Util.getMillis() - notesAt;
        int chars = (int) (elapsed / 18);
        int boxW = Math.min(detailX - listX - listW - 20, 260);
        if (boxW < 80) return;
        int x = cx - boxW / 2;
        int y = cy + 24;
        String head = Component.translatable("screen.riftverse.multiverse.understood").getString();
        g.drawString(font, head, cx - font.width(head) / 2, y, 0xFFE9B0, false);
        y += 13;
        for (String note : notes) {
            if (chars <= 0 || y > barY - 24) break;
            String shown = note.length() > chars ? note.substring(0, chars) : note;
            chars -= note.length();
            for (FormattedCharSequence line : font.split(FormattedText.of("◆ " + shown), boxW)) {
                g.drawString(font, line, x, y, 0xE6E0FF, true);
                y += 10;
            }
        }
    }

    private static String spaced(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}

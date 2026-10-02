package dev.riftverse.client.screen;

import dev.riftverse.fusion.FusionEngine;
import dev.riftverse.fusion.FusionEngine.Gene;
import dev.riftverse.network.Payloads;
import dev.riftverse.universe.UniverseSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dimension Fusion Engine console. Left: the samples you carry (pick two to four; they become parents A-D). Right:
 * every gene group with its source (A, B, C, D or BLEND) and what that gives the new universe. Bottom: name,
 * live compatibility / instability and the FUSE button.
 */
public class FusionScreen extends Screen {
    private static final String[] LETTERS = {"A", "B", "C", "D"};
    private final List<Integer> slots;
    private final List<UniverseSpec> samples = new ArrayList<>();
    private final List<Integer> picked = new ArrayList<>();
    private final int[] choice = new int[Gene.values().length];
    private EditBox name;
    private String nameText = "";

    public FusionScreen(List<Integer> slots, List<CompoundTag> specs) {
        super(Component.literal("Dimension Fusion Engine"));
        this.slots = slots;
        for (CompoundTag t : specs) samples.add(UniverseSpec.load(t));
        for (int i = 0; i < Math.min(2, samples.size()); i++) picked.add(i);
        for (int i = 0; i < choice.length; i++) choice[i] = i % 2;
    }

    private List<UniverseSpec> parents() {
        List<UniverseSpec> out = new ArrayList<>();
        for (int i : picked) out.add(samples.get(i));
        return out;
    }

    private String describe(Gene g, UniverseSpec s) {
        return switch (g) {
            case TERRAIN -> s.terrain.name().toLowerCase() + (s.hasSea ? ", seas" : ", dry");
            case BIOMES -> s.materials.displayName;
            case STRUCTURES -> s.mega.name().toLowerCase().replace('_', ' ');
            case CREATURES -> s.archetype.displayName + " life";
            case WEATHER -> s.weather.name().toLowerCase();
            case SKY -> (s.moons > 0 ? s.moons + " moons" : "no moons") + (s.planetRings ? ", rings" : "") + (s.artStyle > 0 ? ", art style" : "");
            case GRAVITY -> String.format("%.2fg", s.gravity);
            case TIME -> s.time.name().toLowerCase().replace('_', ' ');
            case HAZARDS -> (s.vacuum ? "vacuum, " : "") + "glitch " + Math.round(s.glitch * 100) + "%";
            case MECHANICS -> (s.dreamlike ? "dreamlike, " : "") + s.music.name().toLowerCase();
        };
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = 10, top = 30, colW = Math.min(170, width / 3);
        for (int i = 0; i < samples.size() && i < 12; i++) {
            final int idx = i;
            int pos = picked.indexOf(i);
            UniverseSpec s = samples.get(i);
            String label = (pos >= 0 ? "[" + LETTERS[pos] + "] " : "      ") + s.name;
            addRenderableWidget(Button.builder(Component.literal(label).withColor(pos >= 0 ? s.accent | 0x404040 : 0xA0A0B0), b -> {
                if (picked.contains(idx)) picked.remove((Integer) idx);
                else if (picked.size() < 4) picked.add(idx);
                for (int k = 0; k < choice.length; k++) if (choice[k] >= picked.size()) choice[k] = 0;
                init();
            }).bounds(left, top + i * 22, colW, 20).build());
        }
        int gx = left + colW + 12;
        int gw = width - gx - 10;
        if (picked.size() >= 2) {
            for (Gene g : Gene.values()) {
                final int gi = g.ordinal();
                int y = top + gi * 22;
                String src = choice[gi] == FusionEngine.BLEND ? "BLEND" : LETTERS[choice[gi]];
                addRenderableWidget(Button.builder(Component.literal(src).withColor(choice[gi] == FusionEngine.BLEND ? 0xFFD040 : 0x80E0FF), b -> {
                    choice[gi] = choice[gi] == FusionEngine.BLEND ? 0 : choice[gi] + 1 >= picked.size() ? FusionEngine.BLEND : choice[gi] + 1;
                    init();
                }).bounds(gx + gw - 52, y, 50, 20).build());
            }
        }
        name = addRenderableWidget(new EditBox(font, gx, height - 52, Math.max(80, gw - 110), 20, Component.literal("name")));
        name.setHint(Component.literal("Name your universe (optional)").withColor(0x707090));
        name.setMaxLength(40);
        name.setValue(nameText);
        name.setResponder(v -> nameText = v);
        Button fuse = addRenderableWidget(Button.builder(Component.literal("FUSE").withColor(0xFF60FF), b -> {
            List<Integer> s = new ArrayList<>();
            for (int i : picked) s.add(slots.get(i));
            List<Integer> c = new ArrayList<>();
            for (int v : choice) c.add(v);
            PacketDistributor.sendToServer(new Payloads.FusionRequest(s, c, nameText));
            onClose();
        }).bounds(width - 108, height - 52, 98, 20).build());
        fuse.active = picked.size() >= 2;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xE8060410, 0xF0140828);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, "DIMENSION FUSION ENGINE", width / 2, 10, 0xFF80FF);
        int left = 10, top = 30, colW = Math.min(170, width / 3);
        g.drawString(font, "Samples (pick 2-4)", left, top - 10, 0x9090B0, false);
        int gx = left + colW + 12;
        int gw = width - gx - 10;
        List<UniverseSpec> ps = parents();
        if (ps.size() < 2) {
            g.drawString(font, "Select at least two samples to fuse.", gx, top, 0xC0A0C0, false);
            return;
        }
        g.drawString(font, "Gene group  →  inherited trait", gx, top - 10, 0x9090B0, false);
        for (Gene gene : Gene.values()) {
            int y = top + gene.ordinal() * 22 + 6;
            int c = choice[gene.ordinal()];
            String trait = c == FusionEngine.BLEND ? "blend of all parents" : describe(gene, ps.get(c));
            g.drawString(font, gene.label, gx, y, 0xE0E0F0, false);
            g.drawString(font, trait, gx + 110, y, c == FusionEngine.BLEND ? 0xFFD040 : 0xA0E0FF, false);
        }
        int compat = FusionEngine.compatibility(ps, choice);
        int col = compat >= 70 ? 0x60FF90 : compat >= 40 ? 0xFFD040 : 0xFF5060;
        g.drawString(font, "Compatibility " + compat + "%   •   instability risk " + (100 - compat) + "%", gx, height - 70, col, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

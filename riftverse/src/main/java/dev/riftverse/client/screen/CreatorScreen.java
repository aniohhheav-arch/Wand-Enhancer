package dev.riftverse.client.screen;

import dev.riftverse.network.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The hidden creator interfaces: a masked authority prompt, credential setup, and (after authentication) the creator
 * console. Secrets are masked on screen and sent only in a dedicated packet; nothing is stored on the client.
 */
public class CreatorScreen extends Screen {
    public static final int PROMPT = 0;
    public static final int CREDENTIAL = 1;
    public static final int CONSOLE = 2;

    private final int mode;
    private final boolean credentialSet;
    private EditBox first;
    private EditBox second;

    public CreatorScreen(int mode, boolean credentialSet) {
        super(Component.literal(""));
        this.mode = mode;
        this.credentialSet = credentialSet;
    }

    private static EditBox masked(net.minecraft.client.gui.Font font, int x, int y, int w, String hint) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(128);
        box.setHint(Component.literal(hint).withColor(0x60607A));
        box.setFormatter((text, offset) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        return box;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 30;
        if (mode == PROMPT) {
            first = addRenderableWidget(masked(font, cx - 100, y, 200, "..."));
            setInitialFocus(first);
            addRenderableWidget(Button.builder(Component.literal("Submit"), b -> {
                PacketDistributor.sendToServer(new Payloads.CreatorSubmit(PROMPT, first.getValue(), ""));
                onClose();
            }).bounds(cx - 50, y + 28, 100, 20).build());
        } else if (mode == CREDENTIAL) {
            if (credentialSet) first = addRenderableWidget(masked(font, cx - 100, y - 24, 200, "current secret"));
            second = addRenderableWidget(masked(font, cx - 100, y + 4, 200, "new secret (8+ characters)"));
            addRenderableWidget(Button.builder(Component.literal("Seal"), b -> {
                PacketDistributor.sendToServer(new Payloads.CreatorSubmit(CREDENTIAL, first == null ? "" : first.getValue(), second.getValue()));
                onClose();
            }).bounds(cx - 50, y + 32, 100, 20).build());
        } else {
            String[] labels = {"Materialize the Rupture", "Toggle Architect Aura", "Announce Presence", "Preview the Final Rupture", "Seal Session"};
            for (int i = 0; i < labels.length; i++) {
                final int action = i;
                addRenderableWidget(Button.builder(Component.literal(labels[i]).withColor(i == 0 ? 0xC080FF : 0xFFFFFF), b -> {
                    PacketDistributor.sendToServer(new Payloads.CreatorSubmit(CONSOLE, Integer.toString(action), ""));
                    onClose();
                }).bounds(cx - 110, y - 20 + i * 24, 220, 20).build());
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF0000000, 0xF0080014);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        String title = mode == PROMPT ? "◇" : mode == CREDENTIAL ? "SEAL A NEW CREDENTIAL" : "REALITY ARCHITECT";
        g.drawString(font, title, (width - font.width(title)) / 2, height / 2 - 70, 0xC080FF, true);
        if (mode == CONSOLE) {
            String sub = "Constraints disabled. Choose.";
            g.drawString(font, sub, (width - font.width(sub)) / 2, height / 2 - 56, 0x8080A0, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

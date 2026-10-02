package dev.riftverse.client.screen;

import dev.riftverse.entity.vehicle.DeLoreanEntity;
import dev.riftverse.network.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The time circuits: destination / present / last-departed displays, a keypad entry and the arming switch. */
public class TimeCircuitScreen extends Screen {
    private final int entityId;
    private final int destination;
    private EditBox year;

    public TimeCircuitScreen(int entityId, int destination) {
        super(Component.literal("Time Circuits"));
        this.entityId = entityId;
        this.destination = destination;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        year = addRenderableWidget(new EditBox(font, cx - 60, cy + 10, 120, 20, Component.literal("year")));
        year.setValue(Integer.toString(destination));
        year.setFilter(s -> s.matches("-?\\d{0,7}"));
        setInitialFocus(year);
        addRenderableWidget(Button.builder(Component.literal("SET DESTINATION").withColor(0xFF6040), b -> {
            try {
                PacketDistributor.sendToServer(new Payloads.Vehicle(entityId, Payloads.Vehicle.SET_YEAR, Integer.parseInt(year.getValue())));
            } catch (NumberFormatException ignored) {
            }
        }).bounds(cx - 100, cy + 36, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ARM TIME CIRCUITS").withColor(0xFFD040), b -> {
            send(1);
            onClose();
        }).bounds(cx - 100, cy + 60, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STANDBY").withColor(0x80FF80), b -> {
            send(0);
            onClose();
        }).bounds(cx + 2, cy + 60, 98, 20).build());
    }

    private void send(int arm) {
        try {
            PacketDistributor.sendToServer(new Payloads.Vehicle(entityId, Payloads.Vehicle.SET_YEAR, Integer.parseInt(year.getValue())));
        } catch (NumberFormatException ignored) {
        }
        PacketDistributor.sendToServer(new Payloads.Vehicle(entityId, Payloads.Vehicle.ARM, arm));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0000000, 0xE0100800);
    }

    private void panel(GuiGraphics g, int y, String label, String value, int color) {
        int cx = width / 2;
        g.fill(cx - 110, y, cx + 110, y + 18, 0xFF101010);
        g.renderOutline(cx - 110, y, 220, 18, 0xFF606060);
        g.drawString(font, label, cx - 106, y + 5, 0xFFC0C0C0, false);
        g.drawString(font, value, cx + 104 - font.width(value), y + 5, color, true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int cy = height / 2;
        String present = minecraft != null && minecraft.player != null ? Integer.toString(dev.riftverse.temporal.TemporalManager.PRESENT) : "----";
        panel(g, cy - 74, "DESTINATION TIME", year.getValue().isEmpty() ? "----" : year.getValue(), 0xFFFF4020);
        panel(g, cy - 52, "PRESENT TIME", present, 0xFF40FF60);
        panel(g, cy - 30, "LAST TIME DEPARTED", Integer.toString(destination), 0xFFFFC020);
        String hint = "Arm, then hit " + (int) 88 + " MPH";
        g.drawString(font, hint, (width - font.width(hint)) / 2, cy - 4, 0xFFAAAAAA, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.riftverse.client.screen;

import dev.riftverse.multiverse.event.EventDirector;
import dev.riftverse.multiverse.event.EventType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Event Control Center: browse all 60 events by category, start any of them on yourself, and drive the director
 * (intensity, chains, natural events, stop all). Actions go through the /multiverse event commands, so ordinary
 * permission rules apply.
 */
public class EventControlScreen extends Screen {
    private int category = 1;
    private int intensity;
    private boolean chains;
    private boolean natural;

    public EventControlScreen(int intensity, boolean chains, boolean natural) {
        super(Component.literal("Event Control Center"));
        this.intensity = intensity;
        this.chains = chains;
        this.natural = natural;
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) minecraft.player.connection.sendCommand("multiverse event " + cmd);
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = Math.min(420, width - 20);
        int x0 = (width - w) / 2;
        int y = 34;
        int tabW = w / 6;
        for (int c = 1; c <= 6; c++) {
            final int cat = c;
            String label = EventType.CATEGORIES[c].split(" · ")[1];
            addRenderableWidget(Button.builder(Component.literal((c == category ? "▶ " : "") + label).withColor(c == category ? 0xFFD040 : 0xC0C0D0),
                    b -> { category = cat; init(); }).bounds(x0 + (c - 1) * tabW, y, tabW - 2, 18).build());
        }
        y += 24;
        List<EventType> list = new ArrayList<>();
        for (EventType t : EventType.values()) if (t.category() == category) list.add(t);
        int half = w / 2 - 2;
        for (int i = 0; i < list.size(); i++) {
            EventType t = list.get(i);
            int bx = i % 2 == 0 ? x0 : x0 + w / 2 + 2;
            Button b = Button.builder(Component.literal(t.title).withColor(t.color), btn -> command("start " + t.id)).bounds(bx, y, half, 18).build();
            b.setTooltip(Tooltip.create(Component.literal(t.description)));
            addRenderableWidget(b);
            if (i % 2 == 1) y += 20;
        }
        if (list.size() % 2 == 1) y += 20;
        y = Math.max(y + 8, height - 54);
        EventDirector.Intensity lvl = EventDirector.Intensity.values()[intensity];
        int bw = w / 4 - 2;
        addRenderableWidget(Button.builder(Component.literal("Intensity: " + lvl.name()).withColor(0xFF8040), b -> {
            intensity = (intensity + 1) % EventDirector.Intensity.values().length;
            command("intensity " + EventDirector.Intensity.values()[intensity].name().toLowerCase());
            init();
        }).bounds(x0, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Chains: " + (chains ? "ON" : "OFF")), b -> {
            chains = !chains;
            command("chain " + (chains ? "on" : "off"));
            init();
        }).bounds(x0 + bw + 2, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Natural: " + (natural ? "ON" : "OFF")), b -> {
            natural = !natural;
            command("natural " + (natural ? "enable" : "disable"));
            init();
        }).bounds(x0 + 2 * (bw + 2), y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("STOP ALL").withColor(0xFF4050), b -> command("stop all"))
                .bounds(x0 + 3 * (bw + 2), y, bw, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xE0080812, 0xF0100820);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        String title = "EVENT CONTROL CENTER";
        g.drawString(font, title, (width - font.width(title)) / 2, 12, 0xFFD040, true);
        String sub = EventType.CATEGORIES[category] + "  •  click an event to start it on yourself";
        g.drawString(font, sub, (width - font.width(sub)) / 2, 22, 0x9090B0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

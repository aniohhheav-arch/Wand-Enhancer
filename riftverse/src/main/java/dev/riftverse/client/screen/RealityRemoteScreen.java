package dev.riftverse.client.screen;

import dev.riftverse.multiverse.CosmicRank;
import dev.riftverse.multiverse.EndProtocol;
import dev.riftverse.multiverse.RealityStatus;
import dev.riftverse.multiverse.event.EventType;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseEntry;
import dev.riftverse.universe.Archetype;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Reality Remote console. Left: every universe the holder has charted (the one they stand in first). Right: the
 * selected universe's readout and every Remote function — scan, stabilize, visit, modify, archive, restore, rebuild,
 * architect, events, and the six End Protocols with preview, two-step execution and abort. All requests are validated
 * again on the server.
 */
public class RealityRemoteScreen extends Screen {
    private static final int ROW = 22;
    private static final int PANEL = 0xC0080614;
    private static final int DIM = 0x8C8AA8;

    private final Payloads.OpenRemote data;
    private int selected;
    private int scroll;
    private int eventIndex;
    private int protocolIndex;
    private int rebuildMode = -1;
    private boolean reconstruct;
    private boolean armed;
    private long armedAt;
    private EditBox modifyBox;
    private EditBox createBox;
    private Button eventButton;
    private Button protocolButton;
    private Button reconstructButton;
    private Button executeButton;
    private Button rebuildButton;
    private int listX, listY, listW, listRows;
    private int panelX, panelY, panelW;

    public RealityRemoteScreen(Payloads.OpenRemote data) {
        super(Component.literal("Reality Remote"));
        this.data = data;
    }

    @Nullable
    private UniverseEntry target() {
        return selected >= 0 && selected < data.targets().size() ? data.targets().get(selected) : null;
    }

    private long targetId() {
        UniverseEntry e = target();
        return e == null ? Payloads.OpenRemote.NONE : e.id();
    }

    private void send(int action, int option, boolean flag, String text) {
        PacketDistributor.sendToServer(new Payloads.RemoteAction(action, targetId(), option, flag, text));
    }

    @Override
    protected void init() {
        int margin = 14;
        listX = margin;
        listY = 40;
        listW = Math.min(190, width / 3);
        listRows = Math.max(3, (height - listY - 40) / ROW);
        panelX = listX + listW + 12;
        panelY = 40;
        panelW = width - panelX - margin;
        int bw = Math.max(60, (panelW - 12) / 4);
        int x0 = panelX;
        int y = panelY + 64;

        addRenderableWidget(Button.builder(Component.literal("Scan"), b -> send(Payloads.RemoteAction.SCAN, 0, false, "")).bounds(x0, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stabilize"), b -> send(Payloads.RemoteAction.STABILIZE, 0, false, "")).bounds(x0 + bw + 4, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Visit"), b -> {
            send(Payloads.RemoteAction.VISIT, 0, false, "");
            onClose();
        }).bounds(x0 + (bw + 4) * 2, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Archive"), b -> send(Payloads.RemoteAction.ARCHIVE, 0, false, "")).bounds(x0 + (bw + 4) * 3, y, bw, 20).build());

        y += ROW + 2;
        modifyBox = new EditBox(font, x0, y, bw * 3 + 8, 20, Component.literal("trait value"));
        modifyBox.setHint(Component.literal("gravity 0.4 • time night • weather storm • glitch 0.6").withColor(DIM));
        modifyBox.setMaxLength(80);
        addRenderableWidget(modifyBox);
        addRenderableWidget(Button.builder(Component.literal("Modify"), b -> send(Payloads.RemoteAction.MODIFY, 0, false, modifyBox.getValue()))
                .bounds(x0 + (bw + 4) * 3, y, bw, 20).build());

        y += ROW + 2;
        addRenderableWidget(Button.builder(Component.literal("Restore"), b -> send(Payloads.RemoteAction.RESTORE, 0, false, "")).bounds(x0, y, bw, 20).build());
        rebuildButton = addRenderableWidget(Button.builder(rebuildLabel(), b -> {
            rebuildMode = rebuildMode >= Archetype.values().length - 1 ? -2 : rebuildMode + 1;
            b.setMessage(rebuildLabel());
        }).bounds(x0 + bw + 4, y, bw * 2 + 4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rebuild"), b -> send(Payloads.RemoteAction.REBUILD, rebuildMode, false, ""))
                .bounds(x0 + (bw + 4) * 3, y, bw, 20).build());

        y += ROW + 2;
        createBox = new EditBox(font, x0, y, bw * 3 + 8, 20, Component.literal("describe a universe"));
        createBox.setHint(Component.literal("Reality Architect: describe a universe, or dna:RV-...").withColor(DIM));
        createBox.setMaxLength(150);
        addRenderableWidget(createBox);
        addRenderableWidget(Button.builder(Component.literal("Architect"), b -> send(Payloads.RemoteAction.CREATE, 0, false, createBox.getValue()))
                .bounds(x0 + (bw + 4) * 3, y, bw, 20).build());

        y += ROW + 2;
        eventButton = addRenderableWidget(Button.builder(eventLabel(), b -> {
            eventIndex = (eventIndex + 1) % EventType.values().length;
            b.setMessage(eventLabel());
        }).bounds(x0, y, bw * 3 + 8, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trigger"), b -> {
            send(Payloads.RemoteAction.EVENT, eventIndex, false, "");
            onClose();
        }).bounds(x0 + (bw + 4) * 3, y, bw, 20).build());

        y += ROW + 10;
        protocolButton = addRenderableWidget(Button.builder(protocolLabel(), b -> {
            protocolIndex = (protocolIndex + 1) % EndProtocol.values().length;
            disarm();
            b.setMessage(protocolLabel());
        }).bounds(x0, y, bw * 2 + 4, 20).build());
        reconstructButton = addRenderableWidget(Button.builder(reconstructLabel(), b -> {
            reconstruct = !reconstruct;
            b.setMessage(reconstructLabel());
        }).bounds(x0 + (bw + 4) * 2, y, bw * 2 + 4, 20).build());
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Preview"), b -> {
            send(Payloads.RemoteAction.PREVIEW, protocolIndex, false, "");
            onClose();
        }).bounds(x0, y, bw, 20).build());
        executeButton = addRenderableWidget(Button.builder(Component.literal("EXECUTE").withColor(0xFF5A6A), b -> execute())
                .bounds(x0 + bw + 4, y, bw * 2 + 4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abort"), b -> send(Payloads.RemoteAction.STOP, 0, false, ""))
                .bounds(x0 + (bw + 4) * 3, y, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("▲"), b -> scroll = Math.max(0, scroll - 1)).bounds(listX + listW - 20, listY - 18, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> scroll = Math.min(Math.max(0, data.targets().size() - listRows), scroll + 1))
                .bounds(listX + listW - 42, listY - 18, 20, 16).build());
    }

    private Component rebuildLabel() {
        String m = rebuildMode == -1 ? "same definition" : rebuildMode == -2 ? "brand-new reality" : Archetype.byId(rebuildMode).displayName;
        return Component.literal("Rebuild as: " + m);
    }

    private Component eventLabel() {
        EventType t = EventType.values()[eventIndex];
        return Component.literal("Event: " + t.title).withColor(t.color);
    }

    private Component protocolLabel() {
        EndProtocol p = EndProtocol.values()[protocolIndex];
        return Component.literal("Protocol: " + p.title).withColor(p.colorA);
    }

    private Component reconstructLabel() {
        return Component.literal("Reconstruct after: " + (reconstruct ? "ON" : "OFF"));
    }

    private void disarm() {
        armed = false;
        if (executeButton != null) executeButton.setMessage(Component.literal("EXECUTE").withColor(0xFF5A6A));
    }

    private void execute() {
        if (target() == null) return;
        long now = System.currentTimeMillis();
        if (!armed || now - armedAt > 5000) {
            armed = true;
            armedAt = now;
            executeButton.setMessage(Component.literal("CONFIRM ERASURE?").withColor(0xFFFF40));
            return;
        }
        disarm();
        send(Payloads.RemoteAction.PROTOCOL, protocolIndex, reconstruct, "");
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listRows * ROW) {
            int row = (int) ((my - listY) / ROW) + scroll;
            if (row < data.targets().size()) {
                selected = row;
                disarm();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, data.targets().size() - listRows), scroll - Math.signum(dy)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0050310, 0xE0120820);
        g.fill(listX - 4, listY - 4, listX + listW + 4, listY + listRows * ROW + 4, PANEL);
        g.fill(panelX - 4, panelY - 4, panelX + panelW + 4, height - 14, PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        CosmicRank rank = CosmicRank.values()[Math.floorMod(data.rank(), CosmicRank.values().length)];
        g.drawString(font, "REALITY REMOTE", 14, 12, 0xFFE0A0, true);
        CosmicRank next = rank.next();
        g.drawString(font, rank.title + " • " + data.research() + " research" + (next != rank ? " (" + (next.points - data.research()) + " to " + next.title + ")" : ""),
                14, 24, rank.color, false);

        g.drawString(font, "Charted universes", listX, listY - 14, DIM, false);
        List<UniverseEntry> targets = data.targets();
        if (targets.isEmpty()) g.drawString(font, "Explore to chart universes.", listX + 4, listY + 6, DIM, false);
        for (int i = 0; i < listRows && i + scroll < targets.size(); i++) {
            int idx = i + scroll;
            UniverseEntry e = targets.get(idx);
            int y = listY + i * ROW;
            boolean sel = idx == selected;
            if (sel) g.fill(listX, y, listX + listW, y + ROW - 2, 0x60FFFFFF & (0x60000000 | (e.color() & 0xFFFFFF)));
            g.fill(listX, y, listX + 3, y + ROW - 2, 0xFF000000 | e.color());
            RealityStatus st = RealityStatus.byId(data.statuses().get(idx));
            String here = e.id() == data.current() ? "◆ " : "";
            g.drawString(font, font.plainSubstrByWidth(here + e.name(), listW - 10), listX + 6, y + 2, st == RealityStatus.ACTIVE ? 0xFFFFFF : 0xFF8A9A, false);
            g.drawString(font, e.designation() + (st == RealityStatus.ACTIVE ? "" : "  [" + st + "]"), listX + 6, y + 11, DIM, false);
        }

        UniverseEntry t = target();
        if (t != null) {
            int idx = selected;
            RealityStatus st = RealityStatus.byId(data.statuses().get(idx));
            g.drawString(font, t.name(), panelX, panelY, 0xFF000000 | t.color(), true);
            g.drawString(font, t.designation() + " • " + Archetype.byId(t.archetype()).displayName + " • " + st + " • stability " + data.stability().get(idx) + "%",
                    panelX, panelY + 12, DIM, false);
            List<net.minecraft.util.FormattedCharSequence> desc = font.split(Component.literal(t.description()), panelW);
            for (int i = 0; i < Math.min(2, desc.size()); i++) g.drawString(font, desc.get(i), panelX, panelY + 26 + i * 10, 0xC8C6E0, false);
        } else {
            g.drawString(font, "Select a universe", panelX, panelY, DIM, false);
        }
        int py = protocolButton.getY() - 12;
        g.drawString(font, "END PROTOCOLS", panelX, py, 0xFF5A6A, false);
        EndProtocol p = EndProtocol.values()[protocolIndex];
        g.drawString(font, font.plainSubstrByWidth(p.subtitle, panelW - 100), panelX + 90, py, DIM, false);
        if (armed && System.currentTimeMillis() - armedAt > 5000) disarm();
        g.drawString(font, font.plainSubstrByWidth("Everything destructive is backed up first and can be restored. Ranks: modify/events Voyager • "
                + "archive/restore/architect Cartographer • rebuild/erase/protocols Reality Architect", width - 28), 14, height - 11, 0x6A6888, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

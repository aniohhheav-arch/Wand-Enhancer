package com.stickyfingers.client.gui;

import com.stickyfingers.client.ClientState;
import com.stickyfingers.entity.ZipperKind;
import com.stickyfingers.network.NetworkSnapshotPayload.Entry;
import com.stickyfingers.network.StandActionPayload;
import com.stickyfingers.network.StandActionPayload.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zipper Network interface: a top-down radar of every active zipper and its connections, plus a list
 * to travel, set the default exit, open/close, link and unlink.
 */
public class ZipperNetworkScreen extends Screen {
    private static final int ROW = 22;
    private int linkFrom = -1;
    private int scroll;
    private int lastSize = -1;

    public ZipperNetworkScreen() {
        super(Component.translatable("screen.stickyfingers.network"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        if (ClientState.network.size() != lastSize) rebuild();
        if (minecraft.player != null && minecraft.player.tickCount % 20 == 0) {
            PacketDistributor.sendToServer(new StandActionPayload(Action.REFRESH_NETWORK, -1, -1));
        }
    }

    private void send(Action a, int x, int y) {
        PacketDistributor.sendToServer(new StandActionPayload(a, x, y));
    }

    private void rebuild() {
        clearWidgets();
        List<Entry> list = ClientState.network;
        lastSize = list.size();
        int left = listLeft();
        int top = 40;
        int visible = Math.max(1, (height - top - 40) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
        for (int i = scroll; i < Math.min(list.size(), scroll + visible); i++) {
            Entry e = list.get(i);
            int y = top + (i - scroll) * ROW;
            int x = left + 120;
            addRenderableWidget(Button.builder(Component.translatable("screen.stickyfingers.network.travel"), b -> { send(Action.NET_TRAVEL, e.id(), -1); onClose(); })
                    .bounds(x, y, 44, 18).build());
            addRenderableWidget(Button.builder(Component.literal(ClientState.exitZipper == e.id() ? "★" : "☆"), b -> send(Action.NET_SET_EXIT, e.id(), -1))
                    .bounds(x + 46, y, 18, 18).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.stickyfingers.network.exit"))).build());
            addRenderableWidget(Button.builder(Component.translatable(e.open() ? "screen.stickyfingers.network.close_seam" : "screen.stickyfingers.network.open_seam"),
                    b -> send(Action.NET_TOGGLE_OPEN, e.id(), -1)).bounds(x + 66, y, 40, 18).build());
            Component linkLabel = linkFrom == e.id() ? Component.literal("…") : Component.literal("↔");
            addRenderableWidget(Button.builder(linkLabel, b -> {
                if (linkFrom < 0) linkFrom = e.id();
                else if (linkFrom == e.id()) linkFrom = -1;
                else {
                    boolean linked = false;
                    for (int l : e.links()) if (l == linkFrom) linked = true;
                    send(linked ? Action.NET_UNLINK : Action.NET_LINK, linkFrom, e.id());
                    linkFrom = -1;
                }
                rebuild();
            }).bounds(x + 108, y, 18, 18).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.stickyfingers.network.link"))).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> send(Action.NET_CLOSE, e.id(), -1)).bounds(x + 128, y, 18, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.stickyfingers.network.close_all"), b -> send(Action.CLOSE_ALL, -1, -1))
                .bounds(left, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 104, height - 30, 60, 20).build());
    }

    private int listLeft() {
        return Math.max(8, width / 2 - 140);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= (int) Math.signum(sy);
        rebuild();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFE8A93B);
        List<Entry> list = ClientState.network;
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.stickyfingers.network.empty"), width / 2, height / 2, 0xFFAEB7CF);
            return;
        }
        int left = listLeft();
        int top = 40;
        int visible = Math.max(1, (height - top - 40) / ROW);
        for (int i = scroll; i < Math.min(list.size(), scroll + visible); i++) {
            Entry e = list.get(i);
            int y = top + (i - scroll) * ROW;
            int col = e.open() ? 0xFF7FFFD4 : 0xFFAEB7CF;
            g.fill(left - 2, y - 1, left + 118, y + 19, linkFrom == e.id() ? 0x80E8A93B : 0x60120A26);
            g.drawString(font, "#" + e.id() + " " + kindName(e.kind()).getString(), left, y + 1, col, true);
            StringBuilder links = new StringBuilder();
            for (int l : e.links()) links.append(links.isEmpty() ? "↔ " : ", ").append('#').append(l);
            String pos = e.pos().getX() + " " + e.pos().getY() + " " + e.pos().getZ();
            g.pose().pushPose();
            g.pose().translate(left, y + 11, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawString(font, pos + "  " + (links.isEmpty() ? "" : links.toString()), 0, 0, 0xFF8FD3FF, true);
            g.pose().popPose();
        }
        drawRadar(g, list);
    }

    /** Top-down map of zippers around the player with link lines. */
    private void drawRadar(GuiGraphics g, List<Entry> list) {
        int size = 110;
        int rx = Math.min(width - size - 10, listLeft() + 300);
        if (rx < listLeft() + 280) rx = width - size - 6;
        int ry = 40;
        int cx = rx + size / 2, cy = ry + size / 2;
        g.fill(rx, ry, rx + size, ry + size, 0xB0120A26);
        g.fill(rx, ry, rx + size, ry + 1, 0xFFE8A93B);
        Vec3 me = Minecraft.getInstance().player.position();
        String dim = Minecraft.getInstance().player.level().dimension().location().toString();
        double maxD = 8;
        for (Entry e : list) {
            if (!e.dimension().equals(dim)) continue;
            maxD = Math.max(maxD, Math.max(Math.abs(e.pos().getX() - me.x), Math.abs(e.pos().getZ() - me.z)));
        }
        double s = (size / 2.0 - 6) / maxD;
        Map<Integer, int[]> pts = new HashMap<>();
        for (Entry e : list) {
            if (!e.dimension().equals(dim)) continue;
            pts.put(e.id(), new int[]{cx + (int) ((e.pos().getX() - me.x) * s), cy + (int) ((e.pos().getZ() - me.z) * s)});
        }
        for (Entry e : list) {
            int[] a = pts.get(e.id());
            if (a == null) continue;
            for (int l : e.links()) {
                int[] b = pts.get(l);
                if (b == null || l < e.id()) continue;
                int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                for (int k = 0; k <= steps; k += 2) {
                    int x = a[0] + (b[0] - a[0]) * k / Math.max(1, steps);
                    int y = a[1] + (b[1] - a[1]) * k / Math.max(1, steps);
                    g.fill(x, y, x + 1, y + 1, 0xFF8FD3FF);
                }
            }
        }
        for (Entry e : list) {
            int[] a = pts.get(e.id());
            if (a == null) continue;
            int col = e.id() == ClientState.exitZipper ? 0xFFE8A93B : e.open() ? 0xFF7FFFD4 : 0xFF9A8CFF;
            g.fill(a[0] - 2, a[1] - 2, a[0] + 2, a[1] + 2, col);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.stickyfingers.network.radar"), cx, ry + size + 3, 0xFF9AA3C0);
    }

    private static Component kindName(int kind) {
        return Component.translatable("zipper.stickyfingers." + ZipperKind.byId(kind).name().toLowerCase(Locale.ROOT));
    }
}

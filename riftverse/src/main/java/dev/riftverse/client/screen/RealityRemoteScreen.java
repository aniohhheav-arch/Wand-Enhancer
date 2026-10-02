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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Reality Remote console. A list of charted universes on the left; on the right, four tabs that each hold a few
 * clearly labelled actions: UNIVERSE (scan, visit, stabilize, archive), REALITY (one-click physics presets, restore,
 * rebuild, architect), EVENTS (every multiverse event) and END (the six End Protocols, including ending the world you
 * stand in). The layout adapts to any screen size and GUI scale; every request is re-validated on the server.
 */
public class RealityRemoteScreen extends Screen {
    private enum Tab { UNIVERSE, REALITY, EVENTS, END }

    private static final int PANEL = 0xC0080614;
    private static final int LINE = 0x40FFFFFF;
    private static final int DIM = 0x9A98B8;
    private static final int GAP = 4;
    private static final int BH = 20;

    private static final String[][] PRESETS = {
            {"gravity", "0.35", "Low"}, {"gravity", "1", "Normal"}, {"gravity", "2", "Crushing"},
            {"time", "night", "Night"}, {"time", "day", "Day"}, {"time", "dusk", "Dusk"}, {"time", "cycle", "Cycle"},
            {"weather", "storm", "Storm"}, {"weather", "snow", "Snow"}, {"weather", "stardust", "Stardust"}, {"weather", "clear", "Clear"},
            {"glitch", "0.6", "Unstable"}, {"glitch", "0", "Stable"},
    };

    private final Payloads.OpenRemote data;
    private Tab tab = Tab.UNIVERSE;
    private int selected;
    private int scroll;
    private final int[] presetIndex = {0, 3, 7, 11};
    private int rebuildMode = -1;
    private int protocolIndex;
    private boolean permanent = true;
    private boolean reconstruct;
    private int armed = -1;
    private long armedAt;
    private String architectText = "";
    private EditBox architectBox;

    private int listX, listY, listW, listH, rowH;
    private int panelX, panelY, panelW, panelH;
    private int contentY;
    private boolean compact;

    public RealityRemoteScreen(Payloads.OpenRemote data) {
        super(Component.literal("Reality Remote"));
        this.data = data;
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private UniverseEntry target() {
        return selected >= 0 && selected < data.targets().size() ? data.targets().get(selected) : null;
    }

    private void send(int action, int option, boolean flag, String text) {
        UniverseEntry t = target();
        PacketDistributor.sendToServer(new Payloads.RemoteAction(action, t == null ? Payloads.OpenRemote.NONE : t.id(), option, flag, text));
    }

    private Button button(int x, int y, int w, Component label, Button.OnPress press) {
        return addRenderableWidget(Button.builder(label, press).bounds(x, y, w, BH).build());
    }

    private Button button(int x, int y, int w, Component label, String tip, Button.OnPress press) {
        Button b = Button.builder(label, press).bounds(x, y, w, BH).tooltip(Tooltip.create(Component.literal(tip))).build();
        return addRenderableWidget(b);
    }

    /** Two-click confirmation for anything destructive. */
    private boolean confirm(int id) {
        long now = System.currentTimeMillis();
        if (armed == id && now - armedAt < 5000) {
            armed = -1;
            return true;
        }
        armed = id;
        armedAt = now;
        rebuildWidgets();
        return false;
    }

    private Component danger(int id, String label) {
        boolean hot = armed == id && System.currentTimeMillis() - armedAt < 5000;
        return Component.literal(hot ? "CLICK AGAIN TO CONFIRM" : label).withColor(hot ? 0xFFFF40 : 0xFF6070);
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int m = 8;
        listX = m;
        listY = 40;
        listW = Mth.clamp(width * 30 / 100, 110, 190);
        listH = height - listY - m;
        rowH = 22;
        panelX = listX + listW + m;
        panelY = 40;
        panelW = width - panelX - m;
        panelH = height - panelY - m;

        Tab[] tabs = Tab.values();
        String[] names = {"Universe", "Reality", "Events", "END"};
        int tw = (panelW - GAP * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            Component label = Component.literal(t == tab ? "▸ " + names[i] : names[i]).withColor(t == Tab.END ? 0xFF6070 : t == tab ? 0xFFE0A0 : 0xFFFFFF);
            button(panelX + i * (tw + GAP), panelY, tw, label, b -> {
                tab = t;
                armed = -1;
                rebuildWidgets();
            });
        }
        compact = height < 310;
        contentY = panelY + BH + (compact ? 30 : 44);
        int half = (panelW - GAP) / 2;
        int x0 = panelX;
        int x1 = panelX + half + GAP;
        int y = contentY;
        switch (tab) {
            case UNIVERSE -> {
                button(x0, y, half, Component.literal("Scan"), "Full research readout of the selected universe (+research the first time).",
                        b -> send(Payloads.RemoteAction.SCAN, 0, false, ""));
                button(x1, y, half, Component.literal("Travel there"), "Open a passage to the selected universe.", b -> {
                    send(Payloads.RemoteAction.VISIT, 0, false, "");
                    onClose();
                });
                y += BH + GAP;
                button(x0, y, half, Component.literal("Stabilize"), "Stability back to 100%, glitching removed, events there ended.",
                        b -> send(Payloads.RemoteAction.STABILIZE, 0, false, ""));
                button(x1, y, half, danger(1, "Archive (seal away)"), "Seal the universe: terrain kept, nobody can enter until restored.", b -> {
                    if (confirm(1)) send(Payloads.RemoteAction.ARCHIVE, 0, false, "");
                });
            }
            case REALITY -> {
                String[] kinds = {"gravity", "time", "weather", "glitch"};
                for (int i = 0; i < 4; i++) {
                    final int slot = i;
                    String[] p = PRESETS[presetIndex[i]];
                    int bx = i % 2 == 0 ? x0 : x1;
                    button(bx, y, half, Component.literal("Set " + kinds[i] + " → " + p[2]), "Applies instantly to everyone inside the selected universe.", b -> {
                        String[] cur = PRESETS[presetIndex[slot]];
                        send(Payloads.RemoteAction.MODIFY, 0, false, cur[0] + " " + cur[1]);
                        int n = presetIndex[slot];
                        do n = (n + 1) % PRESETS.length; while (!PRESETS[n][0].equals(kinds[slot]));
                        presetIndex[slot] = n;
                        rebuildWidgets();
                    });
                    if (i % 2 == 1) y += BH + GAP;
                }
                y += 4;
                button(x0, y, half, Component.literal("Rebuild as: " + rebuildLabel()), "Choose what to rebuild into (click to cycle).", b -> {
                    rebuildMode = rebuildMode >= Archetype.values().length - 1 ? -2 : rebuildMode + 1;
                    rebuildWidgets();
                });
                button(x1, y, half, danger(2, "Rebuild now"), "Regenerate the selected universe's terrain (backed up first).", b -> {
                    if (confirm(2)) send(Payloads.RemoteAction.REBUILD, rebuildMode, false, "");
                });
                y += BH + GAP;
                button(x0, y, half, danger(3, "Restore last backup"), "Bring back an erased or archived universe (not ones ended forever).", b -> {
                    if (confirm(3)) send(Payloads.RemoteAction.RESTORE, 0, false, "");
                });
                y += BH + GAP + 4;
                architectBox = new EditBox(font, x0, y, panelW - 70 - GAP, BH, Component.literal("Describe a universe"));
                architectBox.setHint(Component.literal("Describe a new universe...").withColor(0x707090));
                architectBox.setMaxLength(150);
                architectBox.setValue(architectText);
                architectBox.setResponder(v -> architectText = v);
                addRenderableWidget(architectBox);
                button(x0 + panelW - 70, y, 70, Component.literal("Create"), "Reality Architect: manifest it (also accepts dna:RV-...).",
                        b -> send(Payloads.RemoteAction.CREATE, 0, false, architectText));
            }
            case EVENTS -> {
                EventType[] ev = EventType.values();
                for (int i = 0; i < ev.length; i++) {
                    final EventType t = ev[i];
                    int bx = i % 2 == 0 ? x0 : x1;
                    button(bx, y, half, Component.literal(pretty(t.title)).withColor(t.color), t.description + " (starts in front of you)", b -> {
                        send(Payloads.RemoteAction.EVENT, t.ordinal(), false, "");
                        onClose();
                    });
                    if (i % 2 == 1) y += BH + GAP;
                    if (y + BH > panelY + panelH) break;
                }
            }
            case END -> {
                EndProtocol[] ps = EndProtocol.values();
                for (int i = 0; i < ps.length; i++) {
                    final int idx = i;
                    EndProtocol p = ps[i];
                    int bx = i % 2 == 0 ? x0 : x1;
                    Component label = Component.literal((i == protocolIndex ? "▶ " : "") + pretty(p.title)).withColor(i == protocolIndex ? p.colorA : 0xC0C0D0);
                    button(bx, y, half, label, p.subtitle, b -> {
                        protocolIndex = idx;
                        armed = -1;
                        rebuildWidgets();
                    });
                    if (i % 2 == 1) y += BH + GAP;
                }
                y += compact ? 10 : 14;
                button(x0, y, half, Component.literal("Forever: " + (permanent ? "YES" : "no")).withColor(permanent ? 0xFF6070 : 0xFFFFFF),
                        "YES: no backup, it can never be visited or restored again. no: a backup is kept so it can be restored.", b -> {
                            permanent = !permanent;
                            if (permanent) reconstruct = false;
                            rebuildWidgets();
                        });
                Button rec = button(x1, y, half, Component.literal("Rebuild after: " + (reconstruct ? "YES" : "no")),
                        "Automatically reconstruct the universe once it has been unmade.", b -> {
                            reconstruct = !reconstruct;
                            rebuildWidgets();
                        });
                rec.active = !permanent;
                y += BH + GAP;
                button(x0, y, half, Component.literal("Preview (safe)"), "Watch the whole sequence. Nothing is destroyed.", b -> {
                    send(Payloads.RemoteAction.PREVIEW, protocolIndex, false, "");
                    onClose();
                });
                button(x1, y, half, Component.literal("Abort running protocol"), "Cancels it if it has not reached the point of no return.",
                        b -> send(Payloads.RemoteAction.STOP, 0, false, ""));
                y += BH + GAP + 4;
                UniverseEntry t = target();
                button(x0, y, panelW, danger(4, "END SELECTED: " + (t == null ? "—" : t.name())), "Run the protocol on the universe selected in the list.", b -> {
                    if (confirm(4)) {
                        send(Payloads.RemoteAction.PROTOCOL, protocolIndex, reconstruct && !permanent, permanent ? "permanent" : "");
                        onClose();
                    }
                });
                y += BH + GAP;
                button(x0, y, panelW, danger(5, "END THE WORLD I'M IN: " + data.here()), "Ends wherever you stand — a universe, Earth, the Nether, the End. "
                        + "Always permanent. Everyone there is carried to the Nexus.", b -> {
                    if (confirm(5)) {
                        send(Payloads.RemoteAction.END_HERE, protocolIndex, false, "permanent");
                        onClose();
                    }
                });
            }
        }
    }

    private static String pretty(String upper) {
        StringBuilder sb = new StringBuilder();
        boolean start = true;
        for (char c : upper.toCharArray()) {
            sb.append(start ? c : Character.toLowerCase(c));
            start = c == ' ';
        }
        return sb.toString();
    }

    private String rebuildLabel() {
        return rebuildMode == -1 ? "same" : rebuildMode == -2 ? "brand-new" : Archetype.byId(rebuildMode).displayName;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listX && mx < listX + listW && my >= listY + 14 && my < listY + listH) {
            int row = (int) ((my - listY - 14) / rowH) + scroll;
            if (row < data.targets().size()) {
                selected = row;
                armed = -1;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < listX + listW + 4) {
            int rows = Math.max(1, (listH - 14) / rowH);
            scroll = (int) Mth.clamp(scroll - Math.signum(dy), 0, Math.max(0, data.targets().size() - rows));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void tick() {
        if (armed >= 0 && System.currentTimeMillis() - armedAt > 5000) {
            armed = -1;
            rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xE0050310, 0xF0100820);
        g.fill(listX - 3, listY - 3, listX + listW + 3, listY + listH + 3, PANEL);
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        CosmicRank rank = CosmicRank.values()[Math.floorMod(data.rank(), CosmicRank.values().length)];
        CosmicRank next = rank.next();
        g.drawString(font, "REALITY REMOTE", 8, 8, 0xFFE0A0, true);
        String rankText = rank.title + " • " + data.research() + " research";
        g.drawString(font, rankText, width - 8 - font.width(rankText), 8, rank.color, false);
        if (next != rank) {
            int bw = 120;
            int bx = width - 8 - bw;
            float f = Mth.clamp((data.research() - rank.points) / (float) Math.max(1, next.points - rank.points), 0f, 1f);
            g.fill(bx, 19, bx + bw, 22, 0x60FFFFFF);
            g.fill(bx, 19, bx + (int) (bw * f), 22, 0xFF000000 | next.color);
        }
        g.drawString(font, font.plainSubstrByWidth("You are in: " + data.here(), width / 2), 8, 22, DIM, false);

        // universe list
        g.drawString(font, "Charted universes (" + data.targets().size() + ")", listX + 2, listY + 2, DIM, false);
        List<UniverseEntry> targets = data.targets();
        int rows = Math.max(1, (listH - 14) / rowH);
        if (targets.isEmpty()) g.drawString(font, "Travel to chart some.", listX + 4, listY + 18, DIM, false);
        for (int i = 0; i < rows && i + scroll < targets.size(); i++) {
            int idx = i + scroll;
            UniverseEntry e = targets.get(idx);
            int y = listY + 14 + i * rowH;
            boolean sel = idx == selected;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + rowH;
            if (sel) g.fill(listX, y, listX + listW, y + rowH - 1, 0x50000000 | (e.color() & 0xFFFFFF));
            else if (hover) g.fill(listX, y, listX + listW, y + rowH - 1, 0x20FFFFFF);
            g.fill(listX, y, listX + 2, y + rowH - 1, 0xFF000000 | e.color());
            RealityStatus st = RealityStatus.byId(data.statuses().get(idx));
            String name = (e.id() == data.current() ? "◆ " : "") + e.name();
            g.drawString(font, font.plainSubstrByWidth(name, listW - 8), listX + 5, y + 2, st == RealityStatus.ACTIVE ? 0xFFFFFF : 0xFF8A9A, false);
            String sub = Archetype.byId(e.archetype()).displayName + (st == RealityStatus.ACTIVE ? "" : " • " + st);
            g.drawString(font, font.plainSubstrByWidth(sub, listW - 8), listX + 5, y + 11, DIM, false);
        }
        if (targets.size() > rows) {
            int track = listH - 14;
            int bar = Math.max(10, track * rows / targets.size());
            int by = listY + 14 + (track - bar) * scroll / Math.max(1, targets.size() - rows);
            g.fill(listX + listW - 2, by, listX + listW, by + bar, 0x80FFFFFF);
        }

        // selected universe header
        int hy = panelY + BH + 6;
        UniverseEntry t = target();
        if (t != null) {
            RealityStatus st = RealityStatus.byId(data.statuses().get(selected));
            g.drawString(font, font.plainSubstrByWidth(t.name(), panelW), panelX, hy, 0xFF000000 | t.color(), true);
            String line = t.designation() + " • " + Archetype.byId(t.archetype()).displayName + " • " + st + " • stability " + data.stability().get(selected) + "%";
            g.drawString(font, font.plainSubstrByWidth(line, panelW), panelX, hy + 11, DIM, false);
            List<FormattedCharSequence> desc = font.split(Component.literal(t.description()), panelW);
            if (!desc.isEmpty() && !compact) g.drawString(font, desc.get(0), panelX, hy + 22, 0xC8C6E0, false);
        } else {
            g.drawString(font, "Select a universe on the left", panelX, hy, DIM, false);
        }
        g.fill(panelX, contentY - 5, panelX + panelW, contentY - 4, LINE);
        if (tab == Tab.END) {
            EndProtocol p = EndProtocol.values()[protocolIndex];
            int y = contentY + 3 * (BH + GAP);
            g.drawString(font, font.plainSubstrByWidth("▶ " + p.subtitle, panelW), panelX, y + (compact ? 0 : 2), p.colorA, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

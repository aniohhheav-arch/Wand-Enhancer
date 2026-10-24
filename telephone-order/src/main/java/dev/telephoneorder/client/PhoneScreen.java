package dev.telephoneorder.client;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.ModSounds;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.phone.Calls;
import dev.telephoneorder.phone.Listing;
import dev.telephoneorder.phone.PhoneService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The weapons-corporation terminal. Pick-up/connect sequence, rotary/keypad dialler, animated catalogue with a rotating hologram
 * viewer, live order tracking, account/deposits and branching phone calls, drawn with scanlines, glitches and a CRT vignette.
 */
public class PhoneScreen extends Screen {
    // ---- layout
    private static final int W = 372, H = 226;
    private static final int MX = 76, MY = 30, MW = 292, MH = 172;

    // ---- palette
    static final int BG = 0xFF0C0E12, PANEL = 0xFF151A21, PANEL2 = 0xFF1D242D, BRASS = 0xFFCDA03E, DIM = 0xFF6F7A86;
    static final int[] THEME_ACCENT = {0xFFFFB84A, 0xFF6BFFB4, 0xFFC8D860, 0xFF5CE1FF};
    static final int[] TIER_COL = {0xFF9BD6A0, 0xFF78BEFF, 0xFFFFC95A, 0xFFFF78C8, 0xFFBE78FF};
    static final String[] TIER_NAME = {"STANDARD", "ADVANCED", "EXPERIMENTAL", "PROTOTYPE", "CLASSIFIED"};

    private final int model;
    private final int accent;
    private int tick;
    private String tab = "call";
    private String dial = "";
    private int dialPress = -99;
    private String section = "ALL";
    private int sectionIdx = 0;
    private String selected = "vesper_p9";
    private int catScroll, orderScroll;
    private float spin, spinVel = 2.2f;
    private boolean dragging;
    private int callReveal, lastCallHash;
    private String lastToast = ""; private int toastAge = 999;
    private final Map<String, Integer> clickAnim = new HashMap<>();
    private final Random rng = new Random();
    private boolean closedSent;
    private float credDisplay = -1;

    private record Region(int x, int y, int w, int h, String id) {}
    private final List<Region> regions = new ArrayList<>();

    public PhoneScreen(int model) {
        super(Component.literal("Telephone Order"));
        this.model = Math.max(0, Math.min(3, model));
        this.accent = THEME_ACCENT[this.model];
    }

    // ---------------------------------------------------------------------------------------------- state access
    private CompoundTag st() { return ClientState.phone == null ? new CompoundTag() : ClientState.phone; }
    private int credits() { return st().getIntOr("credits", 0); }
    private int tierLevel() { return st().getIntOr("tier", 0); }
    private Set<String> unlocks() {
        Set<String> s = new HashSet<>();
        for (Tag t : st().getListOrEmpty("unlocks")) if (t instanceof StringTag x) s.add(x.value());
        return s;
    }
    private String dim() { return st().getStringOr("dim", "overworld"); }

    public void onState(CompoundTag t) {
        String go = t.getStringOr("go", "");
        if (!go.isEmpty()) { tab = go; playSound("ui_tab", 1.2f); }
        String toast = t.getStringOr("toast", "");
        if (!toast.equals(lastToast)) { lastToast = toast; toastAge = 0; }
        CompoundTag call = t.getCompoundOrEmpty("call");
        int hash = call.getStringOr("id", "").hashCode();
        if (hash != lastCallHash) { lastCallHash = hash; callReveal = 0; if (!call.getStringOr("id", "").equals("welcome") && !go.equals("catalog")) tab = "call"; }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        tick++;
        toastAge++;
        callReveal += 3;
        if (!dragging) { spin += spinVel; }
        if (tick % 40 == 0) send("refresh", "");
        if (tick == 6) playSound("phone_connect", 1.0f);
    }

    @Override
    public void onClose() {
        sendHangup();
        super.onClose();
    }

    @Override
    public void removed() {
        sendHangup();
        super.removed();
    }

    private void sendHangup() {
        if (closedSent) return;
        closedSent = true;
        send("hangup", "");
    }

    private void playSound(String name, float pitch) {
        if (ModSounds.has(name)) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get(name), pitch, 0.7f));
    }

    private void send(String action, String arg) { try { ClientPlayNetworking.send(new Payloads.PhoneAction(action, arg)); } catch (RuntimeException ignored) { } }

    /** dev screenshot hook */
    public void debugGo(String tab, String view, String sel, String section) { this.tab = tab; this.view = view; this.selected = sel; this.section = section; this.tick = 100; }

    // ---------------------------------------------------------------------------------------------- drawing primitives
    private int ox() { return (width - W) / 2; }
    private int oy() { return (height - H) / 2; }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
    }

    private void txt(GuiGraphicsExtractor g, String s, int x, int y, int color) { g.text(font, s, x, y, color, false); }
    private void txtS(GuiGraphicsExtractor g, String s, int x, int y, int color) { g.text(font, s, x, y, color, true); }

    private void txtScaled(GuiGraphicsExtractor g, String s, int x, int y, int color, float sc) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private void glitchTxt(GuiGraphicsExtractor g, String s, int x, int y, int color, float amount) {
        int j = tick % 4 == 0 ? rng.nextInt(3) - 1 : 0;
        if (amount > 0) {
            g.text(font, s, x + j + 1, y, 0x66FF3050, false);
            g.text(font, s, x - j - 1, y, 0x6630D8FF, false);
        }
        g.text(font, s, x, y, color, false);
    }

    private String corrupt(String s, float amount) {
        if (amount <= 0) return s;
        StringBuilder b = new StringBuilder();
        String junk = "█▓▒░#%&@?!/\\";
        long seed = tick / 3;
        Random r = new Random(seed * 31 + s.hashCode());
        for (char c : s.toCharArray()) b.append(c != ' ' && r.nextFloat() < amount ? junk.charAt(r.nextInt(junk.length())) : c);
        return b.toString();
    }

    private boolean hover(Region r, int mx, int my) { return mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h; }

    private boolean button(GuiGraphicsExtractor g, String id, String label, int x, int y, int w, int h, boolean enabled, int color, int mx, int my) {
        Region r = new Region(x, y, w, h, id);
        if (enabled) regions.add(r);
        boolean hov = enabled && hover(r, mx, my);
        int age = tick - clickAnim.getOrDefault(id, -99);
        boolean pressed = age >= 0 && age < 4;
        int off = pressed ? 1 : 0;
        int base = enabled ? (hov ? 0xFF2A3440 : PANEL2) : 0xFF14181D;
        g.fill(x, y, x + w, y + h, base);
        g.fill(x, y, x + w, y + 1, enabled ? (hov ? color : (color & 0x00FFFFFF) | 0x99000000) : 0xFF2A2F36);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF05070A);
        g.outline(x, y, w, h, enabled ? ((color & 0x00FFFFFF) | (hov ? 0xFF000000 : 0x77000000)) : 0xFF22272E);
        if (pressed) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, (color & 0x00FFFFFF) | 0x30000000);
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2 + off, enabled ? (hov ? 0xFFFFFFFF : color) : 0xFF4A525C, false);
        return hov;
    }

    private void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float f, int color) {
        g.fill(x, y, x + w, y + h, 0xFF080A0D);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, f)));
        g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
        g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x55FFFFFF);
        for (int sx = x + 4; sx < x + w - 1; sx += 4) g.fill(sx, y + 1, sx + 1, y + h - 1, 0x66000000);
    }

    private void scanlines(GuiGraphicsExtractor g, int x, int y, int w, int h, float intensity) {
        int a = (int) (0x2A * intensity);
        for (int yy = y; yy < y + h; yy += 2) g.fill(x, yy, x + w, yy + 1, (a << 24));
        int band = y + (int) ((tick * 1.7) % (h + 40)) - 20;
        for (int i = 0; i < 18; i++) {
            int yy = band + i;
            if (yy >= y && yy < y + h) g.fill(x, yy, x + w, yy + 1, ((int) (0x0E * intensity * (1 - Math.abs(i - 9) / 9.0)) << 24) | 0xFFFFFF);
        }
    }

    private void vignette(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + 12, 0x66000000, 0x00000000);
        g.fillGradient(x, y + h - 12, x + w, y + h, 0x00000000, 0x66000000);
    }

    // ---------------------------------------------------------------------------------------------- main render
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        regions.clear();
        int ox = ox(), oy = oy();
        g.fill(0, 0, width, height, 0xB0000000);
        // chassis
        panel(g, ox - 4, oy - 4, W + 8, H + 8, 0xFF1A1D22, 0xFF3A404A);
        panel(g, ox - 2, oy - 2, W + 4, H + 4, 0xFF0A0B0E, BRASS & 0x00FFFFFF | 0xAA000000);
        g.fill(ox, oy, ox + W, oy + H, BG);
        int mmx = mx - ox, mmy = my - oy;
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        boolean connecting = tick < 44;
        header(g, mmx, mmy);
        if (!connecting) {
            tabs(g, mmx, mmy);
            panel(g, MX - 2, MY - 2, MW + 4, MH + 4, 0xFF090B0E, 0xFF2B323B);
            g.fill(MX, MY, MX + MW, MY + MH, 0xFF0D1116);
            switch (tab) {
                case "dial" -> dialTab(g, mmx, mmy);
                case "catalog" -> catalogTab(g, mmx, mmy, a);
                case "orders" -> ordersTab(g, mmx, mmy);
                case "account" -> accountTab(g, mmx, mmy);
                default -> callTab(g, mmx, mmy);
            }
            scanlines(g, MX, MY, MW, MH, "ominous".equals(st().getCompoundOrEmpty("call").getStringOr("style", "")) ? 1.8f : 1f);
            vignette(g, MX, MY, MW, MH);
            footer(g, mmx, mmy);
        } else {
            connectingOverlay(g);
        }
        g.pose().popMatrix();
        // keep regions in screen space for hit tests (they were created in local space => shift)
        // (hit testing subtracts ox/oy as well)
    }

    private void header(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, W, 24, 0xFF11151A);
        g.fill(0, 23, W, 24, BRASS);
        g.fill(6, 4, 30, 20, BRASS);
        txt(g, "T.O.", 9, 8, 0xFF1A1100);
        txtS(g, "TELEPHONE ORDER", 36, 4, accent);
        txt(g, "ARMS & LOGISTICS  •  A SUBSIDIARY OF ITSELF", 36, 14, DIM);
        String area = switch (dim()) { case "nether" -> "666"; case "end" -> "000"; default -> "555"; };
        String line = "LINE " + (model + 1) + "  •  AREA " + area + "  •  " + (tick < 44 ? "CONNECTING" : "CONNECTED");
        txt(g, line, W - 6 - font.width(line), 4, tick < 44 ? 0xFFFFD060 : 0xFF6BFFB4);
        float target = credits();
        if (credDisplay < 0) credDisplay = target;
        credDisplay += (target - credDisplay) * 0.2f;
        String cr = Math.round(credDisplay) + " CR";
        int col = Math.abs(target - credDisplay) > 1 ? 0xFFFFFFFF : accent;
        txtS(g, cr, W - 6 - font.width(cr), 14, col);
        // blinking recording LED
        g.fill(W - 130, 6, W - 127, 9, (tick / 10) % 2 == 0 ? 0xFFFF3030 : 0xFF501010);
    }

    private void tabs(GuiGraphicsExtractor g, int mx, int my) {
        String[][] t = {{"call", "CALL"}, {"dial", "DIAL"}, {"catalog", "CATALOG"}, {"orders", "ORDERS"}, {"account", "ACCOUNT"}};
        int y = MY - 2;
        for (String[] e : t) {
            boolean sel = tab.equals(e[0]);
            Region r = new Region(6, y, 64, 26, "tab:" + e[0]);
            regions.add(r);
            boolean hov = hover(r, mx, my);
            g.fill(6, y, 70, y + 26, sel ? 0xFF222C38 : (hov ? 0xFF1B222B : PANEL));
            g.fill(6, y, 9, y + 26, sel ? accent : (hov ? DIM : 0xFF2A3038));
            g.outline(6, y, 64, 26, sel ? (accent & 0x00FFFFFF | 0xAA000000) : 0xFF262C34);
            txt(g, e[1], 15, y + 9, sel ? accent : (hov ? 0xFFFFFFFF : 0xFFB0B8C2));
            if (e[0].equals("orders")) {
                int n = st().getListOrEmpty("orders").size();
                if (n > 0) { g.fill(54, y + 3, 67, y + 14, 0xFFC03030); txt(g, "" + n, 59, y + 5, 0xFFFFFFFF); }
            }
            y += 30;
        }
        // phone model card
        int cy = y + 4;
        panel(g, 6, cy, 64, 26, PANEL, 0xFF262C34);
        String[] names = {"ROTARY", "TERMINAL", "UPLINK", "HANDSET"};
        String[] perk = {"CLASSIC", "-20% FEES", "-15% ETA", "ANYWHERE"};
        txt(g, names[model], 10, cy + 4, accent);
        txt(g, perk[model], 10, cy + 14, DIM);
    }

    private void footer(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(MX - 2, H - 20, W - 4, H - 4, 0xFF0A0C10);
        g.outline(MX - 2, H - 20, W - 4 - (MX - 2), 16, 0xFF262C34);
        CompoundTag s = st();
        String toast = s.getStringOr("toast", "");
        String style = s.getStringOr("toastStyle", "normal");
        if (!toast.isEmpty()) {
            int col = 0xFF000000 | s.getIntOr("toastColor", 0xFFFFFF);
            float amt = style.equals("corrupt") ? 0.12f : 0;
            String shown = corrupt(toast, amt);
            if (toastAge < 12 && (toastAge / 2) % 2 == 0) col = 0xFFFFFFFF;
            glitchTxt(g, shown, MX + 2, H - 16, col, amt);
        } else {
            String[] ticker = {"\"We ship it. You regret it. Everybody wins.\"", "Delivery times are estimates. Impacts are not.", "Dial 555-0100 for customer service. Dial 0000 for something else.",
                    "Torn slips, dead lines and hot numbers: the directory is incomplete on purpose."};
            String tk = ticker[(tick / 260) % ticker.length];
            int shift = (tick * 2) % 40;
            txt(g, tk, MX + 2, H - 16, 0xFF55606C);
        }
    }

    private void connectingOverlay(GuiGraphicsExtractor g) {
        g.fill(0, 24, W, H, 0xFF05080B);
        String[] lines = {"LIFTING RECEIVER ...... OK", "DIAL TONE ............. OK", "DIALLING 555-0100 ..... OK", "ROUTING VIA ORBITAL RELAY", "HANDSHAKE ............. "};
        int shown = Math.min(lines.length, tick / 8 + 1);
        for (int i = 0; i < shown; i++) {
            String ln = lines[i];
            if (i == 4) ln += tick > 36 ? "OK" : (tick / 3 % 2 == 0 ? "█" : " ");
            glitchTxt(g, ln, 70, 70 + i * 14, i < shown - 1 || tick > 36 ? 0xFF6BFFB4 : accent, 0.3f);
        }
        bar(g, 70, 70 + 5 * 14 + 8, 230, 8, tick / 44f, accent);
        scanlines(g, 0, 24, W, H - 24, 1.5f);
        // animated rotating dial
        int cx = W / 2, cy = 40;
        for (int i = 0; i < 10; i++) {
            double ang = Math.toRadians(tick * 12 + i * 36);
            int x = cx + (int) (Math.cos(ang) * 12), y = cy + (int) (Math.sin(ang) * 12);
            g.fill(x - 1, y - 1, x + 2, y + 2, i == 0 ? accent : 0xFF3A4450);
        }
    }

    // ---------------------------------------------------------------------------------------------- CALL tab
    private void callTab(GuiGraphicsExtractor g, int mx, int my) {
        CompoundTag call = st().getCompoundOrEmpty("call");
        String style = call.getStringOr("style", "normal");
        boolean corrupt = style.equals("corrupt"), omin = style.equals("ominous");
        int col = omin ? 0xFFD090FF : (corrupt ? 0xFFFF6070 : accent);
        String speaker = call.getStringOr("speaker", "T.O.");
        g.fill(MX, MY, MX + MW, MY + 16, omin ? 0xFF1A0F26 : 0xFF141A22);
        g.fill(MX, MY + 16, MX + MW, MY + 17, col);
        float amt = corrupt ? 0.12f : (omin && tick % 50 < 3 ? 0.2f : 0);
        glitchTxt(g, corrupt(speaker, amt), MX + 6, MY + 4, col, corrupt ? 1 : (omin ? 0.5f : 0));
        // an incoming-call pulse
        txt(g, "● REC", MX + MW - 38, MY + 4, (tick / 8) % 2 == 0 ? 0xFFFF4040 : 0xFF602020);
        int y = MY + 24, reveal = callReveal;
        ListTag lines = call.getListOrEmpty("lines");
        for (Tag t : lines) {
            if (!(t instanceof StringTag s)) continue;
            String full = s.value();
            int take = Math.max(0, Math.min(full.length(), reveal));
            reveal -= full.length() + 6;
            if (take <= 0) break;
            String part = full.substring(0, take);
            List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(Component.literal(corrupt(part, amt)), MW - 16);
            for (var w : wrapped) { g.text(font, w, MX + 8, y, col, false); y += 10; }
            if (take < full.length()) { g.fill(MX + 8 + 4, y - 9, MX + 8 + 9, y - 1, (tick / 3) % 2 == 0 ? col : 0x00000000); }
            y += 3;
        }
        ListTag ch = call.getListOrEmpty("choices");
        int cy = MY + MH - 6 - ch.size() * 19;
        boolean ready = reveal > 0 || ch.isEmpty();
        for (int i = 0; i < ch.size(); i++) {
            if (!(ch.get(i) instanceof StringTag s)) continue;
            button(g, "choice:" + i, (i + 1) + ".  " + s.value(), MX + 8, cy + i * 19, MW - 16, 16, callReveal > 30, col, mx, my);
        }
        if (ch.isEmpty()) txt(g, "[ call ended ]", MX + 8, MY + MH - 14, DIM);
    }

    // ---------------------------------------------------------------------------------------------- DIAL tab
    private void dialTab(GuiGraphicsExtractor g, int mx, int my) {
        int x0 = MX + 10, y0 = MY + 10;
        // display
        panel(g, x0, y0, 150, 28, 0xFF05110B, 0xFF1F5A3C);
        String shown = dial.length() > 3 ? dial.substring(0, 3) + "-" + dial.substring(3) : dial;
        String cursor = (tick / 8) % 2 == 0 ? "_" : " ";
        txtScaled(g, shown + cursor, x0 + 6, y0 + 7, 0xFF6BFFB4, 1.6f);
        txt(g, "DIAL A NUMBER", x0 + 2, y0 - 8 + 0, DIM);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < 12; i++) {
            int kx = x0 + (i % 3) * 52, ky = y0 + 36 + (i / 3) * 26;
            String k = keys[i];
            boolean digit = Character.isDigit(k.charAt(0));
            boolean lit = tick - dialPress < 5 && dialPress >= 0 && dialLast.equals(k);
            button(g, "key:" + k, k, kx, ky, 46, 22, digit && dial.length() < 10, lit ? 0xFFFFFFFF : accent, mx, my);
        }
        button(g, "dial", "CALL", x0, y0 + 140, 70, 18, !dial.isEmpty(), 0xFF6BFFB4, mx, my);
        button(g, "bksp", "← DEL", x0 + 76, y0 + 140, 36, 18, !dial.isEmpty(), 0xFFFFB84A, mx, my);
        button(g, "clr", "CLR", x0 + 118, y0 + 140, 36, 18, !dial.isEmpty(), 0xFFFF7070, mx, my);
        // directory panel
        int dx = MX + 176;
        panel(g, dx, MY + 8, 106, 156, 0xFF10151B, 0xFF262C34);
        txt(g, "DIRECTORY", dx + 6, MY + 13, accent);
        String[][] dir = {{"555-0100", "Customer Service"}, {"555-0142", "Receptionist"}, {"555-0150", "Records Dept."}};
        for (int i = 0; i < dir.length; i++) {
            int yy = MY + 28 + i * 22;
            boolean hov = button(g, "speed:" + dir[i][0], dir[i][0], dx + 4, yy, 98, 18, true, 0xFFB0B8C2, mx, my);
        }
        txt(g, "Not every line is listed.", dx + 6, MY + 100, DIM);
        txt(g, "Some numbers only ring", dx + 6, MY + 112, DIM);
        txt(g, "in particular places.", dx + 6, MY + 122, DIM);
        String area = switch (dim()) { case "nether" -> "666 (BRIMSTONE)"; case "end" -> "000 (VOID)"; case "overworld" -> "555 (SURFACE)"; default -> "??? (UNKNOWN)"; };
        txt(g, "AREA CODE", dx + 6, MY + 140, DIM);
        txt(g, area, dx + 6, MY + 150, accent);
    }
    private String dialLast = "";

    // ---------------------------------------------------------------------------------------------- CATALOG tab
    private List<String> sections() {
        List<String> l = new ArrayList<>();
        l.add("ALL");
        l.addAll(Listing.SECTIONS);
        return l;
    }

    private boolean redacted(Listing li) { return li.secret() != null && !unlocks().contains(li.secret()); }
    private boolean locked(Listing li) { return li.secret() == null && li.tier() > tierLevel(); }

    private List<Listing> visible() {
        List<Listing> out = new ArrayList<>();
        for (Listing li : Listing.ALL) if (section.equals("ALL") || li.section().equals(section)) out.add(li);
        out.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        return out;
    }
    private int rank(Listing li) { return (redacted(li) ? 3000 : locked(li) ? 2000 : 0) + li.tier() * 100 + li.price() / 40; }

    // ---- storefront state
    private String view = "grid";
    private final List<String> cart = new ArrayList<>();
    private int gridScroll;
    private static final int RED = 0xFFFF4747, ORANGE = 0xFFFF8A00;

    private static int h(Listing li, int salt) { return Math.floorMod(li.id().hashCode() * 31 + salt * 7919, 100000); }
    private static int discount(Listing li) { return 8 + h(li, 1) % 38; }
    private static int listPrice(Listing li) { return Math.round(li.price() * 100f / (100 - discount(li))); }
    private static float rating(Listing li) { return 3.9f + (h(li, 2) % 12) / 10f; }
    private static int sold(Listing li) { return 40 + h(li, 3) % 9000; }
    private static String stars(float r) {
        int full = Math.round(r);
        return "★".repeat(Math.min(5, full)) + "☆".repeat(Math.max(0, 5 - full));
    }
    private static String kfmt(int n) { return n >= 1000 ? String.format("%.1fk", n / 1000f) : "" + n; }

    private void catalogTab(GuiGraphicsExtractor g, int mx, int my, float partial) {
        switch (view) {
            case "product" -> productView(g, mx, my, partial);
            case "cart" -> cartView(g, mx, my);
            default -> gridView(g, mx, my);
        }
    }

    private void gridView(GuiGraphicsExtractor g, int mx, int my) {
        // flash-sale banner
        g.fillGradient(MX, MY, MX + MW, MY + 22, RED, ORANGE);
        for (int i = 0; i < MW; i += 6) g.fill(MX + i, MY + 20, MX + i + 3, MY + 22, 0x33FFFFFF);
        txtScaled(g, "SUPER ARMS SALE", MX + 6, MY + 4, 0xFFFFFFFF, 1.4f);
        long secs = 10800 - (System.currentTimeMillis() / 1000 % 10800);
        String cd = String.format("ENDS %02d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60);
        txt(g, cd, MX + 130, MY + 8, 0xFFFFF0C0);
        button(g, "cartview", "☆ CART " + cart.size(), MX + MW - 60, MY + 4, 56, 14, true, 0xFFFFFFFF, mx, my);
        // category pills
        button(g, "sec:-1", "<", MX + 4, MY + 25, 14, 13, true, ORANGE, mx, my);
        g.fill(MX + 20, MY + 25, MX + 130, MY + 38, 0xFFF2F2F2);
        String sName = section.equals("ALL") ? "All categories" : section;
        txt(g, sName, MX + 24, MY + 28, 0xFF333333);
        button(g, "sec:1", ">", MX + 132, MY + 25, 14, 13, true, ORANGE, mx, my);
        g.fill(MX + 152, MY + 25, MX + MW - 4, MY + 38, 0xFFF2F2F2);
        txt(g, "⌕  search weapons, attachments, ammo...", MX + 156, MY + 28, 0xFF9A9A9A);

        List<Listing> list = visible();
        int cols = 3, cw = 94, ch = 64, gx = MX + 4, gy = MY + 42;
        int rows = (list.size() + cols - 1) / cols;
        gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, rows - 2)));
        for (int i = 0; i < cols * 2; i++) {
            int idx = (gridScroll * cols) + i;
            if (idx >= list.size()) break;
            Listing li = list.get(idx);
            int x = gx + (i % cols) * (cw + 2), y = gy + (i / cols) * (ch + 2);
            card(g, li, x, y, cw, ch, mx, my);
        }
        if (rows > 2) {
            int sbH = 2 * (ch + 2), th = Math.max(10, sbH * 2 / rows);
            int ty = gy + (sbH - th) * gridScroll / Math.max(1, rows - 2);
            g.fill(MX + MW - 3, gy, MX + MW - 1, gy + sbH, 0xFF1A2028);
            g.fill(MX + MW - 3, ty, MX + MW - 1, ty + th, ORANGE);
        }
    }

    private void card(GuiGraphicsExtractor g, Listing li, int x, int y, int w, int hgt, int mx, int my) {
        boolean red = redacted(li), lock = locked(li);
        Region r = new Region(x, y, w, hgt, "card:" + li.id());
        regions.add(r);
        boolean hov = hover(r, mx, my);
        g.fill(x, y, x + w, y + hgt, red ? 0xFF1C1428 : 0xFFF7F7F7);
        g.outline(x, y, w, hgt, hov ? RED : (red ? 0xFF3A2A4A : 0xFFDDDDDD));
        // image area
        g.fill(x + 1, y + 1, x + w - 1, y + 31, red ? 0xFF120C1A : 0xFFEAEAEA);
        if (red) {
            glitchTxt(g, corrupt("█████", 0.4f), x + 30, y + 10, 0xFFBE78FF, 1);
            txt(g, "CLASSIFIED", x + 22, y + 21, 0xFF6A4A8A);
            txt(g, "dial the right line", x + 6, y + 36, 0xFF8A6AAA);
            txt(g, "to reveal this item", x + 6, y + 46, 0xFF8A6AAA);
            return;
        }
        float bob = hov ? (float) Math.sin(tick * 0.3) * 1.2f : 0;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2f, y + 16 + bob);
        g.pose().scale(1.7f, 1.7f);
        g.item(iconFor(li), -8, -8);
        g.pose().popMatrix();
        // badges
        int disc = discount(li);
        g.fill(x + 1, y + 1, x + 23, y + 10, RED);
        txt(g, "-" + disc + "%", x + 3, y + 2, 0xFFFFFFFF);
        int tc = TIER_COL[Math.min(4, li.tier())];
        g.fill(x + w - 5, y + 1, x + w - 1, y + 31, tc & (lock ? 0x66FFFFFF : 0xFFFFFFFF));
        // texts
        String nm = li.name();
        while (font.width(nm) > w - 6 && nm.length() > 3) nm = nm.substring(0, nm.length() - 1);
        txt(g, nm, x + 3, y + 33, 0xFF222222);
        String price = li.price() + " cr";
        txtS(g, price, x + 3, y + 43, lock ? 0xFF999999 : RED);
        String lp = listPrice(li) + "";
        int px = x + 6 + font.width(price);
        txt(g, lp, px, y + 43, 0xFF9A9A9A);
        g.fill(px - 1, y + 47, px + font.width(lp), y + 48, 0xFF9A9A9A);
        txt(g, stars(rating(li)), x + 3, y + 53, 0xFFFFB400);
        txt(g, kfmt(sold(li)) + " sold", x + w - 3 - font.width(kfmt(sold(li)) + " sold"), y + 53, 0xFF888888);
        if (lock) { g.fill(x + 1, y + 1, x + w - 1, y + hgt - 1, 0x99FFFFFF); txt(g, "LOCKED", x + w / 2 - 16, y + 14, 0xFFAA3030); }
    }

    private void productView(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Listing li = Listing.BY_ID.get(selected);
        if (li == null) { view = "grid"; return; }
        boolean lock = locked(li);
        button(g, "back", "< Back", MX + 4, MY + 4, 44, 13, true, ORANGE, mx, my);
        txt(g, "Home > " + li.section() + " > " + li.name(), MX + 54, MY + 7, DIM);
        // left: hologram + stats
        viewer(g, MX + 4, MY + 22, 104, 76, li, false, mx, my, partial);
        int y = MY + 102;
        if (li.gun() != null) gunStats(g, li.gun(), MX + 4, y, 160);
        else if (li.att() != null) attStats(g, li, MX + 4, y);
        else if (li.ammo() != null) ammoStats(g, li, MX + 4, y);
        else { txt(g, "SUPPORT ORDER  \u2022  delivered as a targeting beacon", MX + 4, y, ORANGE); txt(g, "Throw it (right-click) at a distant spot.", MX + 4, y + 10, 0xFF8A94A0); txt(g, "Cooldown applies. Danger close is denied.", MX + 4, y + 20, 0xFF8A94A0); }
        // right: listing card
        int x = MX + 116, w = MW - 120;
        g.fill(x, MY + 22, x + w, MY + MH - 4, 0xFFF7F7F7);
        g.outline(x, MY + 22, w, MH - 26, 0xFFDDDDDD);
        String nm = li.name();
        txtScaled(g, nm, x + 6, MY + 27, 0xFF111111, 1.25f);
        txt(g, stars(rating(li)) + String.format(" %.1f", rating(li)), x + 6, MY + 41, 0xFFFFB400);
        txt(g, kfmt(sold(li)) + " sold  •  " + kfmt(sold(li) / 7) + " reviews", x + 62, MY + 41, 0xFF777777);
        int disc = discount(li);
        int fee = Math.round(Catalog.DELIVERY_FEE.getOrDefault(li.delivery(), 10) * (model == 1 ? 0.8f : 1f));
        g.fill(x + 4, MY + 52, x + w - 4, MY + 74, 0xFFFFF0F0);
        txtScaled(g, li.price() + " cr", x + 8, MY + 56, RED, 1.7f);
        String lp = listPrice(li) + " cr";
        int lx = x + 12 + (int) (font.width(li.price() + " cr") * 1.7f);
        txt(g, lp, lx, MY + 58, 0xFF999999);
        g.fill(lx - 1, MY + 62, lx + font.width(lp), MY + 63, 0xFF999999);
        g.fill(lx, MY + 65, lx + 30, MY + 73, RED);
        txt(g, "-" + disc + "%", lx + 3, MY + 65, 0xFFFFFFFF);
        long day = st().getLongOr("day", 0L);
        String stock = stockOf(li, day);
        int eta = Math.round(li.etaSec() * (model == 2 ? 0.85f : 1f) * (stock.startsWith("BACKORDER") ? 2 : 1) * ModConfig.get().deliveryTimeMultiplier);
        txt(g, "✈ " + methodName(li.delivery()), x + 6, MY + 79, 0xFF333333);
        txt(g, "Delivery fee " + fee + " cr  •  ETA " + eta + "s", x + 6, MY + 89, 0xFF2E8B57);
        txt(g, stock, x + 6, MY + 99, stock.startsWith("IN") ? 0xFF2E8B57 : 0xFFCC7A00);
        var wrapped = font.split(Component.literal(li.desc()), w - 12);
        int dy = MY + 111;
        for (var wl : wrapped) { if (dy > MY + 128) break; g.text(font, wl, x + 6, dy, 0xFF555555, false); dy += 9; }
        int total = li.price() + fee;
        boolean afford = credits() >= total;
        boolean inCart = cart.contains(li.id());
        button(g, "addcart", inCart ? "✓ IN CART" : "ADD TO CART", x + 4, MY + MH - 28, (w - 12) / 2, 20, !lock && !inCart && cart.size() < 6, ORANGE, mx, my);
        button(g, "order", lock ? "LOCKED" : (afford ? "BUY NOW" : "NEED " + total), x + 8 + (w - 12) / 2, MY + MH - 28, (w - 12) / 2, 20, !lock && afford, RED, mx, my);
    }

    private void cartView(GuiGraphicsExtractor g, int mx, int my) {
        button(g, "back", "< Back", MX + 4, MY + 4, 44, 13, true, ORANGE, mx, my);
        txtS(g, "SHOPPING CART (" + cart.size() + "/6)", MX + 56, MY + 7, ORANGE);
        int y = MY + 24, total = 0, fees = 0;
        for (int i = 0; i < cart.size(); i++) {
            Listing li = Listing.BY_ID.get(cart.get(i));
            if (li == null) continue;
            int fee = Math.round(Catalog.DELIVERY_FEE.getOrDefault(li.delivery(), 10) * (model == 1 ? 0.8f : 1f));
            total += li.price(); fees += fee;
            g.fill(MX + 4, y, MX + MW - 4, y + 20, 0xFFF7F7F7);
            g.pose().pushMatrix(); g.pose().translate(MX + 8, y + 2); g.item(iconFor(li), 0, 0); g.pose().popMatrix();
            txt(g, li.name(), MX + 30, y + 3, 0xFF222222);
            txt(g, methodName(li.delivery()) + "  +" + fee + " cr", MX + 30, y + 12, 0xFF2E8B57);
            txtS(g, li.price() + " cr", MX + MW - 110, y + 6, RED);
            button(g, "rm:" + i, "REMOVE", MX + MW - 56, y + 3, 48, 14, true, 0xFF777777, mx, my);
            y += 22;
        }
        if (cart.isEmpty()) txt(g, "Your cart is empty. Go find something that goes bang.", MX + 10, MY + 30, DIM);
        int grand = total + fees;
        g.fill(MX + 4, MY + MH - 34, MX + MW - 4, MY + MH - 4, 0xFF10151B);
        txt(g, "Items " + total + " cr  +  delivery " + fees + " cr", MX + 10, MY + MH - 30, 0xFFB0B8C2);
        txtS(g, "Total " + grand + " cr", MX + 10, MY + MH - 18, grand > credits() ? 0xFFFF7070 : 0xFFFFFFFF);
        button(g, "checkout", "CHECKOUT (" + cart.size() + ")", MX + MW - 110, MY + MH - 28, 100, 20, !cart.isEmpty() && grand <= credits(), RED, mx, my);
    }

    private ItemStack iconFor(Listing li) {
        Item it = li.gun() != null ? ModItems.GUNS.get(li.id()) : li.att() != null ? ModItems.ATTACHMENTS.get(li.id()) : li.strike() != null ? ModItems.ALL.get(li.id()) : ModItems.AMMO.get(li.id());
        return new ItemStack(it);
    }

    private static String methodName(String m) {
        return switch (m) { case "drone" -> "COURIER DRONE"; case "pod" -> "REINFORCED POD"; case "teleport" -> "TELEPORT CONTAINER"; case "case" -> "CONTAINMENT CASE"; default -> "STANDARD CRATE"; };
    }

    private static String stockOf(Listing li, long day) {
        int h = Math.floorMod((li.id().hashCode() * 31) + (int) day * 17, 10);
        return h < 7 ? "IN STOCK" : (h < 9 ? "LIMITED STOCK" : "BACKORDER (+100% ETA)");
    }

    private void gunStats(GuiGraphicsExtractor g, GunDef d, int x, int y, int w) {
        float dmg = d.dmg() * d.pellets();
        String[][] rows = {{"DAMAGE", String.format("%.1f%s", d.dmg(), d.pellets() > 1 ? " x" + d.pellets() : "")}, {"RATE", Math.round(1200f / Math.max(1, d.delay())) + " rpm"},
                {"RANGE", Math.round(d.range()) + " m"}, {"MAG", d.mag() + (d.energy() ? " cells" : " rds")}, {"RELOAD", String.format("%.1fs", d.reload() / 20f)}};
        float[] f = {Math.min(1f, dmg / 55f), Math.min(1f, (1200f / Math.max(1, d.delay())) / 1000f), Math.min(1f, d.range() / 240f), Math.min(1f, d.mag() / 120f), 1f - Math.min(1f, d.reload() / 120f)};
        int bw = w - 108;
        for (int i = 0; i < rows.length; i++) {
            int yy = y + i * 9;
            txt(g, rows[i][0], x, yy, DIM);
            bar(g, x + 40, yy, bw - 34, 7, f[i], i == 0 ? 0xFFFF7A5A : accent);
            txt(g, rows[i][1], x + 40 + bw - 28, yy, 0xFFE0E6EC);
        }
        txt(g, "AMMO  " + Catalog.AMMO_BY_ID.get(d.ammo()).name() + "  •  MODES " + String.join("/", d.modes()).toUpperCase(), x, y + 46, 0xFF8A94A0);
        txt(g, "SLOTS  " + String.join(", ", d.slots()).toUpperCase(), x, y + 55, 0xFF6A747F);
    }

    private void attStats(GuiGraphicsExtractor g, Listing li, int x, int y) {
        var a = li.att();
        txt(g, "SLOT: " + a.slot().toUpperCase(), x, y, accent);
        txt(g, "FITS: " + String.join(", ", a.fits()), x, y + 10, 0xFF8A94A0);
        int yy = y + 22;
        for (var e : a.mods().entrySet()) {
            if (yy > MY + MH - 46) break;
            double v = e.getValue();
            if (e.getKey().equals("flash")) { txt(g, "No muzzle flash", x, yy, 0xFF6BFFB4); yy += 9; continue; }
            boolean good = switch (e.getKey()) { case "dmg", "range", "mag", "cool" -> v >= 1; case "zoom" -> v < 1; default -> v <= 1; };
            txt(g, e.getKey().toUpperCase() + " " + (v >= 1 ? "+" : "") + Math.round((v - 1) * 100) + "%", x, yy, good ? 0xFF6BFFB4 : 0xFFFF7070);
            yy += 9;
        }
    }

    private void ammoStats(GuiGraphicsExtractor g, Listing li, int x, int y) {
        var a = li.ammo();
        txt(g, a.per() + " rounds per item  •  x" + li.count() + " in the order", x, y, accent);
        txt(g, a.special() ? "Special rounds. Load from the off-hand." : "Standard rounds. Auto-loads from inventory.", x, y + 10, 0xFF8A94A0);
    }

    private void viewer(GuiGraphicsExtractor g, int x, int y, int w, int h, Listing li, boolean red, int mx, int my, float partial) {
        Region vr = new Region(x, y, w, h, "viewer");
        regions.add(vr);
        g.fill(x, y, x + w, y + h, 0xFF05090D);
        // grid floor
        for (int i = 0; i <= 8; i++) { g.fill(x + i * w / 8, y + h - 20, x + i * w / 8 + 1, y + h, 0x22406070); }
        for (int j = 0; j < 4; j++) { int yy = y + h - 20 + j * 6; g.fill(x, yy, x + w, yy + 1, 0x22406070); }
        int cx = x + w / 2, cy = y + h / 2 - 3;
        // holo base rings
        for (int r = 0; r < 3; r++) {
            int rw = 18 + r * 7 + (tick / 2 % 3 == r ? 1 : 0);
            for (int xx = -rw; xx <= rw; xx++) {
                int yy = (int) (Math.sqrt(1 - (xx * xx) / (double) (rw * rw)) * (rw * 0.28));
                g.fill(cx + xx, y + h - 11 + yy, cx + xx + 1, y + h - 10 + yy, 0x6600D8FF);
                g.fill(cx + xx, y + h - 11 - yy, cx + xx + 1, y + h - 10 - yy, 0x2200D8FF);
            }
        }
        // beam
        g.fillGradient(cx - 14, y + 4, cx + 14, y + h - 10, 0x0000D8FF, 0x2200D8FF);
        if (!red) {
            float ang = (float) Math.toRadians(spin + (dragging ? 0 : spinVel * partial));
            float cs = (float) Math.cos(ang);
            float sc = 3.0f;
            float bob = (float) Math.sin((tick + partial) * 0.12) * 1.5f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy + bob);
            g.pose().scale(Math.max(0.12f, Math.abs(cs)) * sc * Math.signum(cs == 0 ? 1 : cs), sc);
            g.item(iconFor(li), -8, -8);
            g.pose().popMatrix();
            // light sweep
            int sw = x + (int) (((tick + partial) * 1.6) % (w + 30)) - 15;
            g.fillGradient(Math.max(x, sw), y, Math.min(x + w, sw + 6), y + h, 0x00FFFFFF, 0x14FFFFFF);
        } else {
            for (int i = 0; i < 6; i++) { int yy = y + 8 + rng.nextInt(h - 16); g.fill(x + rng.nextInt(w / 2), yy, x + w / 2 + rng.nextInt(w / 2), yy + 1, 0x66BE78FF); }
            txt(g, "?", cx - 2, cy - 4, 0xFFBE78FF);
        }
        g.outline(x, y, w, h, 0xFF1F5A6A);
        // corner brackets
        int c = 0xFF00D8FF;
        g.fill(x, y, x + 6, y + 1, c); g.fill(x, y, x + 1, y + 6, c);
        g.fill(x + w - 6, y, x + w, y + 1, c); g.fill(x + w - 1, y, x + w, y + 6, c);
        g.fill(x, y + h - 1, x + 6, y + h, c); g.fill(x, y + h - 6, x + 1, y + h, c);
        g.fill(x + w - 6, y + h - 1, x + w, y + h, c); g.fill(x + w - 1, y + h - 6, x + w, y + h, c);
        txt(g, "3D PREVIEW", x + 3, y + h - 10, 0xAA00D8FF);
    }

    // ---------------------------------------------------------------------------------------------- ORDERS tab
    private void ordersTab(GuiGraphicsExtractor g, int mx, int my) {
        ListTag orders = st().getListOrEmpty("orders");
        txt(g, "LIVE ORDER STATUS", MX + 6, MY + 6, accent);
        txt(g, orders.size() + " ACTIVE", MX + MW - 50, MY + 6, DIM);
        int y = MY + 18;
        if (orders.isEmpty()) {
            txt(g, "No active orders. The sky is quiet. Suspiciously quiet.", MX + 8, y + 6, DIM);
            y += 24;
        }
        for (int i = 0; i < orders.size() && i < 4; i++) {
            if (!(orders.get(i) instanceof CompoundTag o)) continue;
            Listing li = Listing.BY_ID.get(o.getStringOr("listing", ""));
            if (li == null) continue;
            int pct = o.getIntOr("pct", 0);
            boolean inflight = o.getBooleanOr("inflight", false);
            panel(g, MX + 4, y, MW - 8, 30, PANEL, 0xFF262C34);
            g.pose().pushMatrix(); g.pose().translate(MX + 8, y + 7); g.item(iconFor(li), 0, 0); g.pose().popMatrix();
            txt(g, "#" + o.getIntOr("oid", 0) + "  " + li.name(), MX + 28, y + 3, 0xFFFFFFFF);
            String status = inflight ? "DELIVERING — LOOK UP" : pct < 10 ? "ORDER RECEIVED" : pct < 35 ? "PICKING & PACKING" : pct < 70 ? "IN TRANSIT" : pct < 95 ? "INBOUND" : "ARRIVING NOW";
            int scol = inflight ? 0xFFFF6A5A : (pct > 70 ? 0xFFFFC95A : 0xFF6BFFB4);
            txt(g, status, MX + 28, y + 13, (tick / 6) % 2 == 0 || !inflight ? scol : 0xFFFFFFFF);
            txt(g, methodName(o.getStringOr("method", "crate")), MX + 28, y + 22, 0xFF6A747F);
            // progress with moving dot
            int bx = MX + 140, bw = MW - 148;
            bar(g, bx, y + 8, bw, 8, pct / 100f, scol);
            int dot = bx + 1 + (int) ((bw - 4) * (pct / 100f));
            g.fill(dot, y + 6, dot + 3, y + 18, 0xFFFFFFFF);
            String eta = inflight ? "NOW" : o.getIntOr("remain", 0) + "s";
            txt(g, "WAREHOUSE", bx, y + 20, 0xFF4A5560);
            txt(g, eta, bx + bw / 2 - font.width(eta) / 2, y + 20, 0xFFB0B8C2);
            txt(g, "YOU", bx + bw - font.width("YOU"), y + 20, 0xFF4A5560);
            y += 33;
        }
        // history / receipts
        g.fill(MX + 4, y + 2, MX + MW - 4, y + 3, 0xFF262C34);
        txt(g, "PREVIOUS ORDERS  (collectible receipts drop with each delivery)", MX + 6, y + 6, accent);
        ListTag hist = st().getListOrEmpty("history");
        int hy = y + 18, shown = 0;
        for (int i = hist.size() - 1; i >= 0 && shown < 5 && hy < MY + MH - 12; i--, shown++) {
            if (!(hist.get(i) instanceof CompoundTag h)) continue;
            Listing li = Listing.BY_ID.get(h.getStringOr("listing", ""));
            String n = li == null ? "?" : li.name();
            txt(g, "#" + h.getIntOr("oid", 0), MX + 8, hy, DIM);
            txt(g, n, MX + 34, hy, 0xFFC8D0D8);
            txt(g, "day " + h.getIntOr("day", 0) + "  " + h.getIntOr("cost", 0) + " cr", MX + MW - 90, hy, 0xFF7A8490);
            hy += 10;
        }
        if (hist.isEmpty()) txt(g, "Nothing yet. Order something.", MX + 8, hy, DIM);
    }

    // ---------------------------------------------------------------------------------------------- ACCOUNT tab
    private int owned(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.getItem() == it) n += s.getCount();
        }
        return n;
    }

    private void accountTab(GuiGraphicsExtractor g, int mx, int my) {
        CompoundTag s = st();
        int tier = tierLevel();
        txt(g, "ACCOUNT", MX + 6, MY + 6, accent);
        txtS(g, credits() + " credits", MX + 6, MY + 18, 0xFFFFFFFF);
        txt(g, "CLEARANCE LEVEL " + tier + "  (" + TIER_NAME[tier] + ")", MX + 6, MY + 31, TIER_COL[tier]);
        int next = s.getIntOr("nextAt", -1), spent = s.getIntOr("spent", 0);
        bar(g, MX + 6, MY + 42, 120, 7, next < 0 ? 1f : (float) (spent - Catalog.TIER_THRESHOLDS[tier]) / Math.max(1, next - Catalog.TIER_THRESHOLDS[tier]), TIER_COL[tier]);
        txt(g, next < 0 ? "MAX CLEARANCE" : spent + " / " + next + " spent", MX + 6, MY + 52, DIM);
        // deposits
        txt(g, "DEPOSIT RESOURCES", MX + 6, MY + 68, accent);
        int y = MY + 79;
        for (String[] d : PhoneService.DEPOSIT) {
            Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(d[0]));
            int have = owned(d[0]);
            g.pose().pushMatrix(); g.pose().translate(MX + 6, y - 2); g.item(new ItemStack(it), 0, 0); g.pose().popMatrix();
            txt(g, "x" + have, MX + 26, y + 2, have > 0 ? 0xFFFFFFFF : 0xFF59626C);
            txt(g, d[1] + " cr", MX + 50, y + 2, 0xFF8A94A0);
            button(g, "dep:" + d[0], "DEPOSIT", MX + 86, y - 2, 46, 13, have > 0, 0xFF6BFFB4, mx, my);
            y += 14;
        }
        // ringtones
        int rx = MX + 148;
        txt(g, "RINGTONE", rx, MY + 6, accent);
        int cur = s.getIntOr("ringtone", 0);
        for (int i = 0; i < PhoneService.RINGTONES.length; i++) {
            boolean sel = Math.floorMod(cur, 4) == i;
            button(g, "ring:" + i, (sel ? "▶ " : "  ") + PhoneService.RINGTONES[i].toUpperCase(), rx, MY + 17 + i * 16, 80, 13, true, sel ? accent : 0xFFB0B8C2, mx, my);
        }
        // clue log
        txt(g, "ARCHIVE LINE", rx + 90, MY + 6, 0xFFBE78FF);
        int clues = s.getIntOr("clues", 0);
        for (int i = 0; i < 7; i++) {
            boolean got = (clues & (1 << i)) != 0;
            int cx = rx + 90 + i * 8;
            g.fill(cx, MY + 18, cx + 7, MY + 30, got ? 0xFF2A1840 : 0xFF14101C);
            g.outline(cx, MY + 18, 7, 12, got ? 0xFFBE78FF : 0xFF3A2A4A);
            txt(g, got ? "" + Calls.ARCHIVE_DIGITS[i] : "?", cx + 1, MY + 21, got ? 0xFFE8D0FF : 0xFF5A4A6A);
        }
        txt(g, "Find torn slips in old chests.", rx + 90, MY + 34, 0xFF7A6A8A);
        // unlocked lines
        txt(g, "SECRET LINES UNLOCKED", rx, MY + 88, accent);
        int yy = MY + 99;
        Set<String> un = unlocks();
        if (un.isEmpty()) txt(g, "None. Dial carefully.", rx, yy, DIM);
        for (String u : un) { if (yy > MY + MH - 12) break; txt(g, "• " + u.toUpperCase().replace('_', ' '), rx, yy, 0xFFBE78FF); yy += 9; }
    }

    // ---------------------------------------------------------------------------------------------- input
    private Region at(double mx, double my) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) return r;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        if (tick < 44) return true;
        double mx = ev.x() - ox(), my = ev.y() - oy();
        Region r = at(mx, my);
        if (r == null) return super.mouseClicked(ev, dbl);
        if (r.id.equals("viewer")) { dragging = true; return true; }
        clickAnim.put(r.id, tick);
        handleClick(r.id);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) { dragging = false; return super.mouseReleased(ev); }

    @Override
    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        if (dragging) { spin += (float) dx * 2.2f; return true; }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (tab.equals("catalog") && view.equals("grid")) { gridScroll -= (int) Math.signum(sy); return true; }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean charTyped(CharacterEvent ev) {
        if (tab.equals("dial") && ev.codepoint() >= '0' && ev.codepoint() <= '9') { pressKey(String.valueOf((char) ev.codepoint())); return true; }
        return super.charTyped(ev);
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        if (tab.equals("dial")) {
            if (ev.key() == 257 || ev.key() == 40 || ev.key() == 88) { if (!dial.isEmpty()) handleClick("dial"); return true; }   // enter
            if (ev.key() == 42 || ev.key() == 76) { handleClick("bksp"); return true; }                                          // backspace
        }
        return super.keyPressed(ev);
    }

    private void pressKey(String k) {
        if (dial.length() >= 10) return;
        dial += k; dialLast = k; dialPress = tick;
        playSound("key_beep", 0.8f + (k.charAt(0) - '0') * 0.06f);
        send("key", k);
    }

    private void handleClick(String id) {
        playSound("ui_click", 1.0f);
        if (id.startsWith("tab:")) { tab = id.substring(4); playSound("ui_tab", 1.0f); return; }
        if (id.startsWith("key:")) {
            String k = id.substring(4);
            if (Character.isDigit(k.charAt(0))) pressKey(k);
            return;
        }
        if (id.startsWith("speed:")) { dial = id.substring(6).replace("-", ""); send("dial", dial); tab = "call"; return; }
        if (id.startsWith("choice:")) { send("choice", id.substring(7)); return; }
        if (id.startsWith("dep:")) { send("deposit", id.substring(4)); return; }
        if (id.startsWith("ring:")) { send("ringtone", id.substring(5)); return; }
        if (id.startsWith("card:")) { selected = id.substring(5); spin = 0; view = "product"; return; }
        if (id.startsWith("rm:")) { int i = Integer.parseInt(id.substring(3)); if (i >= 0 && i < cart.size()) cart.remove(i); return; }
        if (id.startsWith("sec:")) {
            int d = Integer.parseInt(id.substring(4));
            List<String> s = sections();
            sectionIdx = Math.floorMod(sectionIdx + d, s.size());
            section = s.get(sectionIdx); gridScroll = 0;
            return;
        }
        switch (id) {
            case "dial" -> { if (!dial.isEmpty()) { send("dial", dial); tab = "call"; dial = ""; } }
            case "bksp" -> { if (!dial.isEmpty()) dial = dial.substring(0, dial.length() - 1); }
            case "clr" -> dial = "";
            case "order" -> { send("order", selected); }
            case "back" -> view = "grid";
            case "cartview" -> view = "cart";
            case "addcart" -> { if (!cart.contains(selected) && cart.size() < 6) cart.add(selected); }
            case "checkout" -> { for (String cid : new ArrayList<>(cart)) send("order", cid); cart.clear(); view = "grid"; tab = "orders"; }
            default -> { }
        }
    }
}

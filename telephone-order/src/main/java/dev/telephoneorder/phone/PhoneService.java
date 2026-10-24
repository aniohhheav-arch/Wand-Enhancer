package dev.telephoneorder.phone;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Fx;
import dev.telephoneorder.ModBlocks;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.Sched;
import dev.telephoneorder.block.PhoneStateProp;
import dev.telephoneorder.block.TelephoneBlock;
import dev.telephoneorder.delivery.DeliveryDirector;
import dev.telephoneorder.net.Payloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Server side of the telephone: pick-up, dialling, scripted calls, ordering, deposits and delivery scheduling. */
public final class PhoneService {
    private PhoneService() {}

    public static final String[] RINGTONES = {"classic", "digital", "satellite", "retro"};
    public static final String[][] DEPOSIT = {
            {"minecraft:copper_ingot", "1"}, {"minecraft:iron_ingot", "2"}, {"minecraft:gold_ingot", "6"}, {"minecraft:emerald", "20"},
            {"minecraft:diamond", "60"}, {"minecraft:netherite_ingot", "400"}};

    private static final Random RNG = new Random();

    static final class Session {
        BlockPos pos; ResourceKey<Level> dim; int model; boolean handheld;
        String node = "welcome"; String toast = ""; int toastColor = 0xFFFFFF; String go = ""; String toastStyle = "normal"; boolean dialing;
    }

    static final class Ring { int left; int age; String script; Ring(int l, String s) { left = l; script = s; } }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, Ring> RINGS = new HashMap<>();
    private static final Map<String, UUID> BUSY = new HashMap<>();
    private static final Map<UUID, Long> KEY_COOLDOWN = new HashMap<>();

    private static String key(ServerLevel l, BlockPos p) { return l.dimension().identifier() + "@" + p.asLong(); }

    // ------------------------------------------------------------------------------------------------ setup
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.PhoneAction.TYPE, (payload, ctx) -> handle(ctx.player(), payload.action(), payload.arg()));
        ServerTickEvents.END_SERVER_TICK.register(PhoneService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> hangUp(handler.getPlayer(), false));
    }

    private static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (t % 10 == 0) {
            for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                Session s = SESSIONS.get(id);
                if (p == null) { SESSIONS.remove(id); continue; }
                ServerLevel l = p.level() instanceof ServerLevel sl ? sl : null;
                boolean bad = l == null || !l.dimension().equals(s.dim) || p.isDeadOrDying()
                        || (s.handheld ? !(p.getMainHandItem().getItem() instanceof dev.telephoneorder.weapon.HandheldPhoneItem || p.getOffhandItem().getItem() instanceof dev.telephoneorder.weapon.HandheldPhoneItem)
                        : (p.position().distanceToSqr(Vec3.atCenterOf(s.pos)) > 64 || !(l.getBlockState(s.pos).getBlock() instanceof TelephoneBlock)));
                if (bad) { hangUp(p, true); }
            }
        }
        if (t % 20 == 0) {
            long now = server.overworld().getGameTime();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                List<CompoundTag> orders = Account.list(p, "orders");
                for (CompoundTag o : orders) {
                    if (o.getBooleanOr("inflight", false)) continue;
                    if (now >= o.getLongOr("placed", now) + o.getIntOr("eta", 0)) {
                        o.putBoolean("inflight", true);
                        Account.saveList(p, "orders", orders);
                        startDelivery(p, o);
                        break;
                    }
                }
                if (SESSIONS.containsKey(p.getUUID()) && t % 40 == 0) sendState(p);
            }
        }
    }

    private static void startDelivery(ServerPlayer p, CompoundTag order) {
        Listing li = Listing.BY_ID.get(order.getStringOr("listing", ""));
        if (li == null) return;
        int oid = order.getIntOr("oid", 0);
        DeliveryDirector.start(p, li, order.getStringOr("method", li.delivery()), () -> {
            List<CompoundTag> orders = Account.list(p, "orders");
            orders.removeIf(o -> o.getIntOr("oid", -1) == oid);
            Account.saveList(p, "orders", orders);
            List<CompoundTag> hist = Account.list(p, "history");
            CompoundTag h = new CompoundTag();
            h.putString("listing", li.id());
            h.putInt("day", (int) (p.level().getServer().overworld().getGameTime() / 24000L));
            h.putString("method", order.getStringOr("method", li.delivery()));
            h.putInt("cost", order.getIntOr("cost", 0));
            h.putInt("oid", oid);
            hist.add(h);
            while (hist.size() > 60) hist.remove(0);
            Account.saveList(p, "history", hist);
            if (SESSIONS.containsKey(p.getUUID())) sendState(p);
        }, oid);
    }

    // ------------------------------------------------------------------------------------------------ block interaction
    private static void setPhone(ServerLevel l, BlockPos pos, PhoneStateProp st) {
        BlockState bs = l.getBlockState(pos);
        if (bs.getBlock() instanceof TelephoneBlock && bs.getValue(TelephoneBlock.PHONE) != st) l.setBlock(pos, bs.setValue(TelephoneBlock.PHONE, st), 3);
    }

    public static void interact(ServerPlayer p, ServerLevel l, BlockPos pos, TelephoneBlock block) {
        String k = key(l, pos);
        UUID busy = BUSY.get(k);
        if (busy != null && !busy.equals(p.getUUID()) && SESSIONS.containsKey(busy)) {
            p.sendOverlayMessage(Component.literal("§eThe line is busy."));
            return;
        }
        Account.of(p);
        Ring ring = RINGS.remove(k);
        Session s = new Session();
        s.pos = pos; s.dim = l.dimension(); s.model = block.model;
        if (ring != null) s.node = ring.script;
        else if (Account.flag(p, "operator_pending") && !Account.has(p, "operator")) { s.node = "operator"; Account.setFlag(p, "operator_pending", false); }
        else s.node = "welcome";
        SESSIONS.put(p.getUUID(), s);
        BUSY.put(k, p.getUUID());
        setPhone(l, pos, PhoneStateProp.OFF_HOOK);
        Vec3 c = Vec3.atCenterOf(pos);
        Fx.sound(l, c, "phone_pickup", SoundSource.BLOCKS, 1.0f, block.model == 1 ? 1.2f : 1f);
        Sched.later(8, () -> { if (SESSIONS.get(p.getUUID()) == s) Fx.sound(l, c, "phone_connect", SoundSource.BLOCKS, 0.6f, 1f); });
        ServerPlayNetworking.send(p, new Payloads.PhoneOpen(pos, block.model));
        sendState(p);
    }

    /** Pocket telephone: no block needed, works anywhere. */
    public static void openHandheld(ServerPlayer p, ServerLevel l) {
        if (SESSIONS.containsKey(p.getUUID())) return;
        Account.of(p);
        Session s = new Session();
        s.pos = p.blockPosition(); s.dim = l.dimension(); s.model = 3; s.handheld = true;
        s.node = Account.flag(p, "operator_pending") && !Account.has(p, "operator") ? "operator" : "welcome";
        if (s.node.equals("operator")) Account.setFlag(p, "operator_pending", false);
        SESSIONS.put(p.getUUID(), s);
        Vec3 c = p.position().add(0, 1, 0);
        Fx.sound(l, c, "phone_pickup", SoundSource.PLAYERS, 0.8f, 1.4f);
        Sched.later(8, () -> { if (SESSIONS.get(p.getUUID()) == s) Fx.sound(l, c, "phone_connect", SoundSource.PLAYERS, 0.5f, 1.2f); });
        ServerPlayNetworking.send(p, new Payloads.PhoneOpen(s.pos, 3));
        sendState(p);
    }

    public static void hangUp(ServerPlayer p, boolean notifyClient) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        MinecraftServer server = p.level().getServer();
        if (server != null && s.handheld) {
            if (p.level() instanceof ServerLevel pl) Fx.sound(pl, p.position().add(0, 1, 0), "phone_hangup", SoundSource.PLAYERS, 0.6f, 1.4f);
        } else if (server != null) {
            ServerLevel l = server.getLevel(s.dim);
            if (l != null) {
                String k = key(l, s.pos);
                if (p.getUUID().equals(BUSY.get(k))) BUSY.remove(k);
                BlockState bs = l.getBlockState(s.pos);
                if (bs.getBlock() instanceof TelephoneBlock && !RINGS.containsKey(k)) {
                    setPhone(l, s.pos, PhoneStateProp.IDLE);
                    Fx.sound(l, Vec3.atCenterOf(s.pos), "phone_hangup", SoundSource.BLOCKS, 1.0f, 1f);
                }
            }
        }
        if (notifyClient) ServerPlayNetworking.send(p, new Payloads.PhoneClose());
    }

    // ------------------------------------------------------------------------------------------------ ringing
    public static void ring(ServerLevel l, BlockPos pos, int ticks, String script) {
        String k = key(l, pos);
        if (BUSY.containsKey(k) || !(l.getBlockState(pos).getBlock() instanceof TelephoneBlock)) return;
        RINGS.put(k, new Ring(ticks, script));
        setPhone(l, pos, PhoneStateProp.RING_A);
        l.scheduleTick(pos, l.getBlockState(pos).getBlock(), 2);
    }

    public static void ringTick(ServerLevel l, BlockPos pos, BlockState state) {
        String k = key(l, pos);
        Ring r = RINGS.get(k);
        if (r == null) { if (state.getValue(TelephoneBlock.PHONE).ringing()) setPhone(l, pos, PhoneStateProp.IDLE); return; }
        if (BUSY.containsKey(k)) { RINGS.remove(k); return; }
        r.age += 2; r.left -= 2;
        if (r.left <= 0) { RINGS.remove(k); setPhone(l, pos, PhoneStateProp.IDLE); return; }
        int phase = r.age % 60;
        Vec3 c = Vec3.atCenterOf(pos);
        if (phase < 24) {
            setPhone(l, pos, ((r.age / 2) % 2 == 0) ? PhoneStateProp.RING_A : PhoneStateProp.RING_B);
            if (phase == 2) {
                int model = state.getBlock() instanceof TelephoneBlock tb ? tb.model : 0;
                for (ServerPlayer p : l.players()) {
                    if (p.position().distanceToSqr(c) > 40 * 40) continue;
                    CompoundTag acc = Account.of(p);
                    int tone = Math.floorMod(acc.getIntOr("ringtone", model), RINGTONES.length);
                    Fx.privateSound(p, c, "ring_" + RINGTONES[tone], SoundSource.BLOCKS, 2.0f, 1.0f);
                }
                Fx.p(l, net.minecraft.core.particles.ParticleTypes.NOTE, c.add(0, 0.9, 0), 2, 0.25, 0.1, 0.25, 1.0);
            }
        } else {
            setPhone(l, pos, PhoneStateProp.IDLE);
        }
        l.scheduleTick(pos, state.getBlock(), 2);
    }

    /** Rings every telephone within {@code radius} blocks of the player. Returns how many were found. */
    public static int ringNear(ServerPlayer p, int radius, int ticks, String script) {
        ServerLevel l = (ServerLevel) p.level();
        BlockPos o = p.blockPosition();
        int n = 0;
        for (BlockPos bp : BlockPos.betweenClosed(o.offset(-radius, -4, -radius), o.offset(radius, 4, radius))) {
            if (l.getBlockState(bp).getBlock() instanceof TelephoneBlock) { ring(l, bp.immutable(), ticks, script); n++; }
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------------ actions
    public static void handle(ServerPlayer p, String action, String arg) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        if (action.equals("hangup")) { hangUp(p, false); return; }
        if (!(p.level() instanceof ServerLevel l)) return;
        switch (action) {
            case "refresh" -> { }
            case "key" -> key(p, l, s, arg);
            case "dial" -> dial(p, l, s, arg);
            case "choice" -> { try { choose(p, l, s, Integer.parseInt(arg)); } catch (NumberFormatException ignored) { } }
            case "order" -> order(p, l, s, arg);
            case "deposit" -> deposit(p, s, arg);
            case "ringtone" -> {
                try {
                    int i = Math.floorMod(Integer.parseInt(arg), RINGTONES.length);
                    Account.of(p).putInt("ringtone", i);
                    Fx.privateSound(p, Vec3.atCenterOf(s.pos), "ring_" + RINGTONES[i], SoundSource.BLOCKS, 1.5f, 1f);
                    toast(s, "Ringtone set: " + RINGTONES[i].toUpperCase(), 0x7CFFB0);
                } catch (NumberFormatException ignored) { }
            }
            case "ack" -> { s.toast = ""; }
            default -> { return; }
        }
        sendState(p);
    }

    private static void toast(Session s, String text, int color) { s.toast = text; s.toastColor = color; s.toastStyle = "normal"; }

    private static void key(ServerPlayer p, ServerLevel l, Session s, String digit) {
        long now = l.getGameTime();
        Long last = KEY_COOLDOWN.get(p.getUUID());
        if (last != null && now - last < 2) return;
        KEY_COOLDOWN.put(p.getUUID(), now);
        int d = digit.isEmpty() ? 0 : Math.floorMod(digit.charAt(0) - '0', 10);
        Vec3 c = Vec3.atCenterOf(s.pos);
        Fx.sound(l, c, "key_beep", SoundSource.BLOCKS, 0.7f, 0.75f + d * 0.06f);
        PhoneStateProp[] cyc = {PhoneStateProp.DIAL_A, PhoneStateProp.DIAL_B, PhoneStateProp.DIAL_C};
        setPhone(l, s.pos, cyc[(int) (now % 3)]);
        Sched.later(5, () -> { if (SESSIONS.get(p.getUUID()) == s) setPhone(l, s.pos, PhoneStateProp.OFF_HOOK); });
    }

    private static String dimOf(ServerLevel l) {
        if (l.dimension() == Level.OVERWORLD) return "overworld";
        if (l.dimension() == Level.NETHER) return "nether";
        if (l.dimension() == Level.END) return "end";
        return "other";
    }

    private static void dial(ServerPlayer p, ServerLevel l, Session s, String raw) {
        String n = raw.replaceAll("[^0-9]", "");
        if (n.length() > 12) n = n.substring(0, 12);
        s.dialing = false;
        Vec3 c = Vec3.atCenterOf(s.pos);
        Fx.sound(l, c, "phone_connect", SoundSource.BLOCKS, 0.8f, 1f);
        String dim = dimOf(l);
        String node;
        switch (n) {
            case "5550100" -> node = "welcome";
            case "5550113" -> node = Account.spent(p) >= 1000 ? (Account.has(p, "proto_lab") ? "proto_ok" : "proto") : "proto_denied";
            case "0000000", "0000" -> node = "disconnected";
            case "5550101" -> node = !dim.equals("overworld") ? "wrong_dim" : (l.isBrightOutside() && l.canSeeSky(p.blockPosition()) ? "line_overworld" : "line_overworld_dark");
            case "5550666" -> node = dim.equals("nether") ? "line_nether" : "wrong_dim";
            case "5550999" -> node = dim.equals("end") ? "line_end" : "wrong_dim";
            case Calls.ARCHIVE_NUMBER -> node = "archive";
            case "5550142" -> node = "operator_hint";
            case "5550150" -> node = "clue_hint";
            case "8675309" -> node = "jenny";
            case "1234567" -> node = Account.flag(p, "luggage") ? "busy" : "luggage";
            case "911", "112", "999" -> node = "emergency";
            case "4040404" -> node = "e404";
            default -> node = n.length() >= 7 && RNG.nextInt(3) == 0 ? "wrong" : "busy";
        }
        if (node.equals("disconnected")) {
            Fx.sound(l, c, "phone_disconnected", SoundSource.BLOCKS, 1.0f, 1f);
        } else if (node.equals("operator_hint") || node.equals("wrong")) {
            Fx.sound(l, c, "static_burst", SoundSource.BLOCKS, 0.4f, 1f);
        }
        showNode(p, s, node);
        toast(s, "Dialled " + formatNumber(n), 0x9FE8FF);
    }

    public static String formatNumber(String n) {
        if (n.length() == 7) return n.substring(0, 3) + "-" + n.substring(3);
        return n;
    }

    private static final Map<String, String> NODE_UNLOCKS = Map.of("proto_ok", "proto_lab", "line_overworld", "line_overworld", "line_nether", "line_nether",
            "line_end", "line_end", "archive", "archive", "operator_pay", "operator");

    private static void showNode(ServerPlayer p, Session s, String id) {
        if (!Calls.NODES.containsKey(id)) id = "welcome";
        s.node = id;
        String key = NODE_UNLOCKS.get(id);
        if (key != null && !Account.has(p, key)) {
            Account.unlock(p, key);
            toast(s, "CLASSIFIED LISTING UNLOCKED", 0xFFC54A);
            if (p.level() instanceof ServerLevel l) Fx.sound(l, Vec3.atCenterOf(s.pos), "item_ready", SoundSource.BLOCKS, 0.8f, 1f);
        }
    }

    private static void choose(ServerPlayer p, ServerLevel l, Session s, int idx) {
        Calls.Node node = Calls.NODES.get(s.node);
        if (node == null || idx < 0 || idx >= node.choices().size()) return;
        Calls.Choice ch = node.choices().get(idx);
        Fx.sound(l, Vec3.atCenterOf(s.pos), "ui_click", SoundSource.BLOCKS, 0.4f, 1.2f);
        String next = ch.next();
        boolean hang = false;
        for (String a : ch.action().split(";")) {
            if (a.isEmpty()) continue;
            String[] kv = a.split(":", 2);
            String arg = kv.length > 1 ? kv[1] : "";
            switch (kv[0]) {
                case "unlock" -> {
                    boolean had = Account.has(p, arg);
                    Account.unlock(p, arg);
                    if (!had) { toast(s, "CLASSIFIED LISTING UNLOCKED", 0xFFC54A); Fx.sound(l, Vec3.atCenterOf(s.pos), "item_ready", SoundSource.BLOCKS, 0.8f, 1f); }
                }
                case "pay" -> {
                    int cost = Integer.parseInt(arg);
                    if (!Account.pay(p, cost)) next = "operator_broke";
                }
                case "credits" -> { Account.add(p, (int) (Integer.parseInt(arg) * ModConfig.get().credits)); toast(s, "+" + arg + " credits", 0x7CFFB0); }
                case "goto" -> s.go = arg;
                case "flag" -> Account.setFlag(p, arg, true);
                case "hangup" -> hang = true;
                case "event" -> { if (arg.equals("eerie")) eerie(p, l, s); }
                default -> { }
            }
        }
        if (hang) {
            Session sc = s;
            Sched.later(2, () -> { if (SESSIONS.get(p.getUUID()) == sc) hangUp(p, true); });
        }
        // unlock side effects: "pay" with unlock handled in script order (pay then unlock)
        if (!ch.next().equals(next) || !hang) showNode(p, s, next);
    }

    private static void eerie(ServerPlayer p, ServerLevel l, Session s) {
        Vec3 at = p.position();
        Account.setFlag(p, "operator_pending", true);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, true, false, false));
        Fx.privateSound(p, at, "eerie_drone", SoundSource.AMBIENT, 1.4f, 1f);
        Sched.later(30, () -> Fx.privateSound(p, at.add(-p.getViewVector(1f).x * 3, 0, -p.getViewVector(1f).z * 3), "whisper", SoundSource.AMBIENT, 1.6f, 0.8f));
        Sched.later(60, () -> Fx.privateSound(p, at, "static_burst", SoundSource.AMBIENT, 1.0f, 0.6f));
        ServerPlayNetworking.send(p, new Payloads.ScreenFlash(0x200030, 40));
        Sched.later(100, () -> {
            if (!p.isAlive() || p.hasDisconnected()) return;
            int n = ringNear(p, 14, 1400, "operator");
            if (n == 0) ServerPlayNetworking.send(p, new Payloads.Notify("Somewhere a phone is ringing. Find one and answer it.", 0xB070FF));
            else ServerPlayNetworking.send(p, new Payloads.Notify("A phone is ringing. Nobody dialled it.", 0xB070FF));
        });
    }

    // ------------------------------------------------------------------------------------------------ orders / deposits
    private static int stock(Listing li, long day) {
        int h = Math.floorMod((li.id().hashCode() * 31) + (int) day * 17, 10);
        return h < 7 ? 0 : (h < 9 ? 1 : 2);
    }

    private static double feeMult(int model) { return model == 1 ? 0.8 : 1.0; }
    private static double etaMult(int model) { return model == 2 ? 0.85 : 1.0; }

    public static boolean available(ServerPlayer p, Listing li) {
        if (li.secret() != null && !Account.has(p, li.secret())) return false;
        return li.tier() <= Account.tier(p) || li.secret() != null;
    }

    private static void order(ServerPlayer p, ServerLevel l, Session s, String arg) {
        String[] parts = arg.split("\\|");
        String id = parts[0];
        Listing li = Listing.BY_ID.get(id);
        if (li == null) return;
        String method = parts.length > 1 && Catalog.DELIVERY_FEE.containsKey(parts[1]) ? parts[1] : li.delivery();
        Vec3 c = Vec3.atCenterOf(s.pos);
        if (!available(p, li)) { deny(l, c, s, "CLEARANCE INSUFFICIENT"); return; }
        List<CompoundTag> orders = Account.list(p, "orders");
        if (orders.size() >= 6) { deny(l, c, s, "TOO MANY ACTIVE ORDERS (6)"); return; }
        int fee = (int) Math.round(Catalog.DELIVERY_FEE.getOrDefault(method, 10) * feeMult(s.model));
        int cost = li.price() + fee;
        if (Account.credits(p) < cost) { deny(l, c, s, "INSUFFICIENT CREDITS  (need " + cost + ")"); return; }
        Account.pay(p, cost);
        long now = l.getServer().overworld().getGameTime();
        int stock = stock(li, now / 24000L);
        double eta = li.etaSec() / Catalog.ETA_MULT.getOrDefault(li.delivery(), 1f) * Catalog.ETA_MULT.getOrDefault(method, 1f) * 20.0 * etaMult(s.model) * ModConfig.get().deliveryTimeMultiplier * (stock == 2 ? 2.0 : 1.0);
        if (method.equals("teleport")) eta = Catalog.TELEPORT_ETA_SEC * 20.0;
        CompoundTag t = Account.of(p);
        int oid = t.getIntOr("nextOid", 1);
        t.putInt("nextOid", oid + 1);
        CompoundTag o = new CompoundTag();
        o.putInt("oid", oid);
        o.putString("listing", li.id());
        o.putString("method", method);
        o.putLong("placed", now);
        o.putInt("eta", Math.max(40, (int) eta));
        o.putInt("cost", cost);
        orders.add(o);
        Account.saveList(p, "orders", orders);
        Fx.sound(l, c, "order_confirm", SoundSource.BLOCKS, 1.0f, 1f);
        s.toastColor = 0x7CFFB0; s.toastStyle = "normal";
        s.toast = "ORDER #" + oid + " CONFIRMED  -" + cost + " cr  ETA " + (Math.max(40, (int) eta) / 20) + "s";
        afterOrder(p, l, s, li);
    }

    private static void deny(ServerLevel l, Vec3 c, Session s, String why) {
        Fx.sound(l, c, "order_denied", SoundSource.BLOCKS, 0.8f, 1f);
        s.toast = why; s.toastColor = 0xFF6060; s.toastStyle = "normal";
    }

    private static void afterOrder(ServerPlayer p, ServerLevel l, Session s, Listing li) {
        BlockPos pos = s.pos;
        boolean secret = li.secret() != null;
        if (secret || li.tier() >= 3 && RNG.nextInt(100) < 35) {
            s.toastStyle = "corrupt";
            s.toast = "ORD3R C0NF1RM3D ▒▒ DELIVERY TO: ██████ (" + li.name().toUpperCase() + ")";
            Fx.sound(l, Vec3.atCenterOf(pos), "static_burst", SoundSource.BLOCKS, 0.7f, 1f);
            Sched.later(120, () -> { if (p.isAlive() && !p.hasDisconnected() && !BUSY.containsKey(key(l, pos))) ring(l, pos, 900, "event_ringback"); });
        } else if (RNG.nextInt(100) < 8) {
            Sched.later(160, () -> { if (p.isAlive() && !p.hasDisconnected() && !BUSY.containsKey(key(l, pos))) ring(l, pos, 600, "event_survey"); });
        }
    }

    private static void deposit(ServerPlayer p, Session s, String itemId) {
        int value = 0;
        for (String[] d : DEPOSIT) if (d[0].equals(itemId)) value = Integer.parseInt(d[1]);
        if (value <= 0) return;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        int taken = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.getItem() == item && !st.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) { taken += st.getCount(); st.setCount(0); }
        }
        ServerLevel l = (ServerLevel) p.level();
        if (taken == 0) { toast(s, "NOTHING TO DEPOSIT", 0xFF6060); Fx.sound(l, Vec3.atCenterOf(s.pos), "ui_error", SoundSource.BLOCKS, 0.7f, 1f); return; }
        int add = Math.round(taken * value * ModConfig.get().credits);
        Account.add(p, add);
        Fx.sound(l, Vec3.atCenterOf(s.pos), "order_confirm", SoundSource.BLOCKS, 0.6f, 1.4f);
        toast(s, "DEPOSITED " + taken + "x -> +" + add + " credits", 0x7CFFB0);
    }

    // ------------------------------------------------------------------------------------------------ state
    public static void sendState(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        MinecraftServer server = p.level().getServer();
        CompoundTag acc = Account.of(p);
        CompoundTag t = new CompoundTag();
        t.putInt("credits", acc.getIntOr("credits", 0));
        int spent = acc.getIntOr("spent", 0);
        t.putInt("spent", spent);
        int tier = Account.tierFor(spent);
        t.putInt("tier", tier);
        t.putInt("nextAt", tier + 1 < Catalog.TIER_THRESHOLDS.length ? Catalog.TIER_THRESHOLDS[tier + 1] : -1);
        t.putInt("model", s.model);
        t.putInt("ringtone", acc.getIntOr("ringtone", 0));
        ServerLevel l = (ServerLevel) p.level();
        t.putString("dim", dimOf(l));
        long now = server.overworld().getGameTime();
        t.putLong("day", now / 24000L);
        ListTag un = new ListTag();
        for (String u : Account.unlocks(p)) un.add(StringTag.valueOf(u));
        t.put("unlocks", un);
        t.putInt("clues", acc.getIntOr("clues", 0));
        ListTag ol = new ListTag();
        for (CompoundTag o : Account.list(p, "orders")) {
            CompoundTag c = new CompoundTag();
            c.putInt("oid", o.getIntOr("oid", 0));
            c.putString("listing", o.getStringOr("listing", ""));
            long placed = o.getLongOr("placed", now);
            int eta = Math.max(1, o.getIntOr("eta", 1));
            int pct = (int) Math.min(100, Math.max(0, (now - placed) * 100 / eta));
            boolean inflight = o.getBooleanOr("inflight", false);
            c.putInt("pct", inflight ? 100 : pct);
            c.putInt("remain", inflight ? 0 : (int) Math.max(0, (placed + eta - now) / 20));
            c.putString("method", o.getStringOr("method", "crate"));
            c.putBoolean("inflight", inflight);
            ol.add(c);
        }
        t.put("orders", ol);
        ListTag hl = new ListTag();
        for (CompoundTag h : Account.list(p, "history")) hl.add(h.copy());
        t.put("history", hl);
        // call
        Calls.Node node = Calls.NODES.getOrDefault(s.node, Calls.NODES.get("welcome"));
        CompoundTag call = new CompoundTag();
        call.putString("id", s.node);
        call.putString("speaker", node.speaker());
        call.putString("style", node.style());
        ListTag lines = new ListTag();
        if (s.node.equals("welcome")) {
            for (String f : Calls.flavourForDimension(dimOf(l))) lines.add(StringTag.valueOf(f));
        }
        for (String ln : node.lines()) lines.add(StringTag.valueOf(ln));
        call.put("lines", lines);
        ListTag ch = new ListTag();
        for (Calls.Choice c : node.choices()) ch.add(StringTag.valueOf(c.label()));
        call.put("choices", ch);
        t.put("call", call);
        t.putString("toast", s.toast);
        t.putInt("toastColor", s.toastColor);
        t.putString("toastStyle", s.toastStyle);
        t.putString("go", s.go);
        s.go = "";
        ServerPlayNetworking.send(p, new Payloads.PhoneState(t));
    }

    public static void forget(UUID id) { SESSIONS.remove(id); KEY_COOLDOWN.remove(id); }
    public static void clear() { SESSIONS.clear(); RINGS.clear(); BUSY.clear(); KEY_COOLDOWN.clear(); }
}

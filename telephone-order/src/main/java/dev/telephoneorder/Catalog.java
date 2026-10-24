package dev.telephoneorder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable weapon / attachment / ammo catalog, loaded from the generated JSON in {@code telephoneorder_catalog/}. */
public final class Catalog {
    public static final List<String> TIERS = List.of("standard", "advanced", "experimental", "prototype", "classified");

    public record GunDef(String id, String name, String cat, String fam, String beh, String ammo, int tier, int price, String rarity,
                         String delivery, int eta, float dmg, int pellets, float spread, float range, int delay, int mag, int reload,
                         boolean perRound, float rp, float ry, float hs, float ap, int pierce, List<String> modes, float heat,
                         float cool, String sound, String rsound, String desc, String secret, List<String> slots,
                         Map<String, Double> p, int glow) {
        public double p(String key, double def) { return p.getOrDefault(key, def); }
        public boolean has(String key) { return p.containsKey(key) && p.get(key) != 0; }
        public boolean energy() { return fam.equals("energy") || fam.equals("heavy") && !ammo.equals("ammo_rocket"); }
        public boolean usesHeat() { return heat > 0; }
    }

    public record AttDef(String id, String name, String slot, List<String> fits, int price, int tier, Map<String, Double> mods,
                         String desc, boolean laser, boolean light, String finish, int index) {
        public double mod(String k, double def) { return mods.getOrDefault(k, def); }
    }

    public record AmmoDef(String id, String name, int per, int price, boolean special, String mod, String desc, int color) {}

    public static final List<GunDef> GUNS;
    public static final Map<String, GunDef> GUN_BY_ID = new LinkedHashMap<>();
    public static final List<AttDef> ATTS;
    public static final Map<String, AttDef> ATT_BY_ID = new LinkedHashMap<>();
    public static final List<AmmoDef> AMMO;
    public static final Map<String, AmmoDef> AMMO_BY_ID = new LinkedHashMap<>();
    public static final List<String> SOUNDS;
    public static final int[] TIER_THRESHOLDS;
    public static final Map<String, Integer> DELIVERY_FEE = new HashMap<>();
    /** shipping methods in display order, and how their delivery time scales (lower = faster). */
    public static final List<String> SHIP_METHODS = List.of("crate", "drone", "pod", "case", "teleport");
    /** Teleport (rift) delivery always arrives this many seconds after ordering. */
    public static final int TELEPORT_ETA_SEC = 5;
    public static final Map<String, Float> ETA_MULT = Map.of("crate", 1.4f, "drone", 1.0f, "pod", 0.85f, "case", 0.7f, "teleport", 0.1f);

    static {
        try {
            List<GunDef> guns = new ArrayList<>();
            for (JsonElement e : arr("guns.json")) guns.add(gun(e.getAsJsonObject()));
            GUNS = Collections.unmodifiableList(guns);
            for (GunDef g : guns) GUN_BY_ID.put(g.id(), g);

            List<AttDef> atts = new ArrayList<>();
            int i = 0;
            for (JsonElement e : arr("attachments.json")) atts.add(att(e.getAsJsonObject(), i++));
            ATTS = Collections.unmodifiableList(atts);
            for (AttDef a : atts) ATT_BY_ID.put(a.id(), a);

            List<AmmoDef> ammo = new ArrayList<>();
            for (JsonElement e : arr("ammo.json")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray c = o.getAsJsonArray("color");
                ammo.add(new AmmoDef(o.get("id").getAsString(), o.get("name").getAsString(), o.get("per").getAsInt(), o.get("price").getAsInt(),
                        o.get("special").getAsBoolean(), o.has("mod") ? o.get("mod").getAsString() : "", o.has("desc") ? o.get("desc").getAsString() : "",
                        (c.get(0).getAsInt() << 16) | (c.get(1).getAsInt() << 8) | c.get(2).getAsInt()));
            }
            AMMO = Collections.unmodifiableList(ammo);
            for (AmmoDef a : ammo) AMMO_BY_ID.put(a.id(), a);

            List<String> sounds = new ArrayList<>();
            for (JsonElement e : arr("sounds.json")) sounds.add(e.getAsString());
            SOUNDS = Collections.unmodifiableList(sounds);

            JsonObject meta = obj("meta.json");
            JsonArray th = meta.getAsJsonArray("thresholds");
            TIER_THRESHOLDS = new int[th.size()];
            for (int k = 0; k < th.size(); k++) TIER_THRESHOLDS[k] = th.get(k).getAsInt();
            for (Map.Entry<String, JsonElement> en : meta.getAsJsonObject("fees").entrySet()) DELIVERY_FEE.put(en.getKey(), en.getValue().getAsInt());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load the Telephone Order catalog", ex);
        }
    }

    private Catalog() {}

    private static JsonElement read(String name) {
        var in = Catalog.class.getResourceAsStream("/telephoneorder_catalog/" + name);
        if (in == null) throw new IllegalStateException("Missing catalog resource " + name);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    private static JsonArray arr(String n) { return read(n).getAsJsonArray(); }
    private static JsonObject obj(String n) { return read(n).getAsJsonObject(); }

    private static List<String> strs(JsonArray a) {
        List<String> l = new ArrayList<>();
        for (JsonElement e : a) l.add(e.getAsString());
        return Collections.unmodifiableList(l);
    }

    private static Map<String, Double> nums(JsonObject o) {
        Map<String, Double> m = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) m.put(e.getKey(), v.getAsDouble());
            else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) m.put(e.getKey(), v.getAsBoolean() ? 1.0 : 0.0);
            else if (v.isJsonArray() && v.getAsJsonArray().size() == 3) {
                JsonArray c = v.getAsJsonArray();
                m.put(e.getKey(), (double) ((c.get(0).getAsInt() << 16) | (c.get(1).getAsInt() << 8) | c.get(2).getAsInt()));
            }
        }
        return m;
    }

    private static GunDef gun(JsonObject o) {
        Map<String, Double> p = nums(o.getAsJsonObject("p"));
        return new GunDef(o.get("id").getAsString(), o.get("name").getAsString(), o.get("cat").getAsString(), o.get("fam").getAsString(),
                o.get("beh").getAsString(), o.get("ammo").getAsString(), TIERS.indexOf(o.get("tier").getAsString()), o.get("price").getAsInt(),
                o.get("rarity").getAsString(), o.get("delivery").getAsString(), o.get("eta").getAsInt(), o.get("dmg").getAsFloat(),
                o.get("pellets").getAsInt(), o.get("spread").getAsFloat(), o.get("range").getAsFloat(), o.get("delay").getAsInt(),
                o.get("mag").getAsInt(), o.get("reload").getAsInt(), o.get("perRound").getAsBoolean(), o.get("rp").getAsFloat(),
                o.get("ry").getAsFloat(), o.get("hs").getAsFloat(), o.get("ap").getAsFloat(), o.get("pierce").getAsInt(), strs(o.getAsJsonArray("modes")),
                o.get("heat").getAsFloat(), o.get("cool").getAsFloat(), o.get("sound").getAsString(), o.get("rsound").getAsString(),
                o.get("desc").getAsString(), o.get("secret").isJsonNull() ? null : o.get("secret").getAsString(), strs(o.getAsJsonArray("slots")),
                p, p.containsKey("glow") ? p.get("glow").intValue() : 0xFFFFFF);
    }

    private static AttDef att(JsonObject o, int index) {
        return new AttDef(o.get("id").getAsString(), o.get("name").getAsString(), o.get("slot").getAsString(), strs(o.getAsJsonArray("fits")),
                o.get("price").getAsInt(), TIERS.indexOf(o.get("tier").getAsString()), nums(o.getAsJsonObject("mods")), o.get("desc").getAsString(),
                o.has("laser"), o.has("light"), o.has("finish") ? o.get("finish").getAsString() : "", index);
    }

    public static boolean fits(AttDef a, GunDef g) {
        return g.slots().contains(a.slot()) && a.fits().contains(g.fam());
    }
}

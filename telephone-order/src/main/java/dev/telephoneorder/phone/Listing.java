package dev.telephoneorder.phone;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.AmmoDef;
import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One line in the weapons-corporation catalogue (shared by client UI and server validation). */
public record Listing(String id, String kind, String section, String name, int price, int tier, String secret, String delivery, int etaSec,
                      String rarity, String desc, int count, GunDef gun, AttDef att, AmmoDef ammo, String strike) {

    public static final List<Listing> ALL;
    public static final Map<String, Listing> BY_ID = new LinkedHashMap<>();
    public static final List<String> SECTIONS = List.of("Sidearms", "Submachine Guns", "Rifles", "Shotguns", "Support", "Precision", "Tactical",
            "Sci-Fi", "Heavy", "Classified", "Support", "Attachments", "Ammunition");

    static {
        List<Listing> l = new ArrayList<>();
        for (GunDef g : Catalog.GUNS) {
            l.add(new Listing(g.id(), "gun", sectionOf(g), g.name(), g.price(), g.tier(), g.secret(), g.delivery(), g.eta(), g.rarity(), g.desc(), 1, g, null, null, null));
        }
        for (AttDef a : Catalog.ATTS) {
            l.add(new Listing(a.id(), "att", "Attachments", a.name(), a.price(), a.tier(), null, a.price() > 150 ? "drone" : "crate", 18, a.tier() >= 2 ? "uncommon" : "common", a.desc(), 1, null, a, null, null));
        }
        for (AmmoDef a : Catalog.AMMO) {
            int n = a.special() ? 2 : 3;
            l.add(new Listing(a.id(), "ammo", "Ammunition", a.name() + " x" + n, a.price() * n, a.special() ? 1 : 0, null, "crate", 12, a.special() ? "uncommon" : "common",
                    a.special() ? a.desc() : (a.per() + " rounds per box. Supplied in " + n + " boxes."), n, null, null, a, null));
        }
        String[][] strikes = {
                {"strike_meteor_precision", "Meteor Strike: Precision", "450", "1", "drone", "25", "uncommon", "Five fireballs walk onto a marked spot. Danger close is denied.", "Support"},
                {"strike_meteor_random", "Meteor Strike: Barrage", "800", "2", "drone", "30", "rare", "A scattered rain of fireballs over a wide patch. Craters. Dust. Regret.", "Support"},
                {"strike_meteor_wide", "Meteor Strike: Wide Area", "1500", "2", "pod", "40", "rare", "A massive shower across a huge radius. Bring a bunker.", "Support"},
                {"strike_orbital", "Orbital Strike", "2200", "2", "case", "50", "rare", "Siren, darkening sky, an orbital platform and a colossal beam of light. Long cooldown.", "Support"},
                {"strike_lance", "Planetary Lance", "6000", "3", "case", "80", "epic", "The sky bends toward a single point; a colossal sweeping beam follows. The most spectacular strike we sell.", "Support"},
                {"strike_strategic", "Strategic Superweapon", "9000", "3", "case", "100", "epic", "Sirens, a blinding flash, a mushroom cloud and a shockwave. Ships in safe demonstration mode (see config).", "Support"}};
        for (String[] k : strikes) l.add(new Listing(k[0], "strike", k[8], k[1], Integer.parseInt(k[2]), Integer.parseInt(k[3]), null, k[4], Integer.parseInt(k[5]), k[6], k[7], 1, null, null, null, k[0]));
        ALL = Collections.unmodifiableList(l);
        for (Listing x : l) BY_ID.put(x.id(), x);
    }

    private static String sectionOf(GunDef g) {
        if (g.cat().equals("classified")) return "Classified";
        if (g.cat().equals("scifi")) return "Sci-Fi";
        if (g.cat().equals("heavy")) return "Heavy";
        if (g.cat().equals("tactical")) {
            return switch (g.fam()) { case "sniper" -> "Precision"; case "lmg" -> "Support"; default -> "Tactical"; };
        }
        return switch (g.fam()) {
            case "pistol" -> "Sidearms"; case "smg" -> "Submachine Guns"; case "shotgun" -> "Shotguns"; case "lmg" -> "Support"; case "sniper" -> "Precision";
            default -> g.id().equals("mesa_dmr") ? "Precision" : "Rifles";
        };
    }

    public boolean secretOnly() { return secret != null; }

    public String itemId() { return id; }
}

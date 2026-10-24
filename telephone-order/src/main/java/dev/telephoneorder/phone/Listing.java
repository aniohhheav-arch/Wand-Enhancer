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
                      String rarity, String desc, int count, GunDef gun, AttDef att, AmmoDef ammo) {

    public static final List<Listing> ALL;
    public static final Map<String, Listing> BY_ID = new LinkedHashMap<>();
    public static final List<String> SECTIONS = List.of("Sidearms", "Submachine Guns", "Rifles", "Shotguns", "Support", "Precision", "Tactical",
            "Sci-Fi", "Heavy", "Classified", "Attachments", "Ammunition");

    static {
        List<Listing> l = new ArrayList<>();
        for (GunDef g : Catalog.GUNS) {
            l.add(new Listing(g.id(), "gun", sectionOf(g), g.name(), g.price(), g.tier(), g.secret(), g.delivery(), g.eta(), g.rarity(), g.desc(), 1, g, null, null));
        }
        for (AttDef a : Catalog.ATTS) {
            l.add(new Listing(a.id(), "att", "Attachments", a.name(), a.price(), a.tier(), null, a.price() > 150 ? "drone" : "crate", 18, a.tier() >= 2 ? "uncommon" : "common", a.desc(), 1, null, a, null));
        }
        for (AmmoDef a : Catalog.AMMO) {
            int n = a.special() ? 2 : 3;
            l.add(new Listing(a.id(), "ammo", "Ammunition", a.name() + " x" + n, a.price() * n, a.special() ? 1 : 0, null, "crate", 12, a.special() ? "uncommon" : "common",
                    a.special() ? a.desc() : (a.per() + " rounds per box. Supplied in " + n + " boxes."), n, null, null, a));
        }
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

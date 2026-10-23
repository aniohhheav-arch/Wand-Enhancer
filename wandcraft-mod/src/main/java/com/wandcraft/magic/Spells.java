package com.wandcraft.magic;

import com.wandcraft.magic.spells.ArcaneSpells;
import com.wandcraft.magic.spells.ElementalSpells;
import com.wandcraft.magic.spells.OtherSchools;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Spells {
    private static final Map<String, Spell> ALL = new LinkedHashMap<>();
    private static boolean loaded;

    private Spells() {}

    public static synchronized void init() {
        if (loaded) return;
        loaded = true;
        ArcaneSpells.register();
        ElementalSpells.register();
        OtherSchools.register();
        Icons.apply();
    }

    public static Spell add(Spell s) {
        if (ALL.putIfAbsent(s.id, s) != null) throw new IllegalStateException("Duplicate spell " + s.id);
        return s;
    }

    public static Spell get(String id) { return id == null ? null : ALL.get(id); }
    public static Collection<Spell> all() { return ALL.values(); }

    public static List<Spell> in(Category c) {
        List<Spell> out = new ArrayList<>();
        for (Spell s : ALL.values()) if (s.category == c) out.add(s);
        return out;
    }

    /** Categories that currently contain at least one spell. */
    public static List<Category> activeCategories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) if (!in(c).isEmpty()) out.add(c);
        return out;
    }
}

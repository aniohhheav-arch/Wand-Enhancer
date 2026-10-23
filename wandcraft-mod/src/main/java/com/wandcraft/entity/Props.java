package com.wandcraft.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Catalogue of GeckoLib prop kinds. The geometry, animations and texture live under {@code assets/wandcraft/**&#47;prop/<id>}. */
public final class Props {
    public enum Mode { SOLID, TRANSLUCENT, ADDITIVE }

    public record Def(String id, Mode mode, boolean fullbright) {}

    private static final Map<String, Def> ALL = new LinkedHashMap<>();

    static {
        reg("vine_guardian", Mode.SOLID, false);
        reg("rune_sigil", Mode.ADDITIVE, true);
        reg("rune_sigil_red", Mode.ADDITIVE, true);
        reg("gravity_orb", Mode.ADDITIVE, true);
        reg("void_orb", Mode.TRANSLUCENT, true);
        reg("black_hole", Mode.TRANSLUCENT, true);
        reg("bloom", Mode.TRANSLUCENT, true);
        reg("mirror", Mode.TRANSLUCENT, true);
        reg("shield_panel", Mode.TRANSLUCENT, true);
        reg("shield_panel_gold", Mode.TRANSLUCENT, true);
        reg("dome_blue", Mode.TRANSLUCENT, true);
        reg("dome_gold", Mode.TRANSLUCENT, true);
        reg("fire_pillar", Mode.ADDITIVE, true);
        reg("tornado_sand", Mode.TRANSLUCENT, false);
        reg("roots", Mode.SOLID, false);
        reg("light_orb", Mode.ADDITIVE, true);
        reg("sanctuary", Mode.ADDITIVE, true);
        reg("portal_blue", Mode.TRANSLUCENT, true);
        reg("portal_orange", Mode.TRANSLUCENT, true);
        reg("portal_violet", Mode.TRANSLUCENT, true);
        reg("slash_wave", Mode.ADDITIVE, true);
        reg("time_ring", Mode.ADDITIVE, true);
        reg("reality_crack", Mode.ADDITIVE, true);
        reg("phantom_wall", Mode.TRANSLUCENT, true);
    }

    private Props() {}

    private static void reg(String id, Mode m, boolean fb) { ALL.put(id, new Def(id, m, fb)); }

    public static Def get(String id) { return ALL.get(id); }
    public static Iterable<String> ids() { return ALL.keySet(); }
}

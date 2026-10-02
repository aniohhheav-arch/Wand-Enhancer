package dev.riftverse.multiverse;

import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.SpecFactory;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.universe.UniverseTraits.WeatherKind;
import java.util.Locale;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Universe DNA: a short, shareable code capturing a reality's defining genes (archetype, terrain, weather, time,
 * gravity, instability, moons, sky flags and seed). Codes can be pasted into /multiverse universe create dna:CODE to
 * grow a sibling of the original reality.
 *
 * <pre>RV-AA-T-W-M-GG-X-O-F-SEED</pre>
 */
public final class UniverseDna {
    private UniverseDna() {}

    private static String b36(long v, int width) {
        String s = Long.toString(v, 36).toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < width; i++) sb.append('0');
        return sb.append(s).toString();
    }

    public static String encode(UniverseSpec s) {
        int flags = (s.vacuum ? 1 : 0) | (s.dreamlike ? 2 : 0) | (s.skyBlackHole ? 4 : 0) | (s.planetRings ? 8 : 0) | (s.binarySun ? 16 : 0);
        return "RV-" + b36(s.archetype.ordinal(), 2)
                + "-" + b36(s.terrain.ordinal(), 1)
                + "-" + b36(s.weather.ordinal(), 1)
                + "-" + b36(s.time.ordinal(), 1)
                + "-" + b36(Math.max(0, Math.min(1295, Math.round(s.gravity * 100))), 2)
                + "-" + b36(Math.max(0, Math.min(35, Math.round(s.glitch * 35))), 1)
                + "-" + b36(Math.max(0, Math.min(35, s.moons)), 1)
                + "-" + b36(flags, 1)
                + "-" + b36(s.seed & 0xFFFFFFFFL, 7);
    }

    /** Builds a spec for the given slot from a DNA code, or returns null if the code is malformed. */
    @Nullable
    public static UniverseSpec decode(String code, UniverseId id) {
        String[] g = code.trim().toUpperCase(Locale.ROOT).split("-");
        if (g.length != 10 || !g[0].equals("RV")) return null;
        try {
            Archetype a = Archetype.byId(Integer.parseInt(g[1], 36));
            long seed = Long.parseLong(g[9], 36);
            UniverseSpec s = SpecFactory.random(id, seed, a, new Random(seed));
            s.terrain = TerrainMode.byId(Integer.parseInt(g[2], 36));
            s.weather = WeatherKind.byId(Integer.parseInt(g[3], 36));
            s.time = TimeMode.byId(Integer.parseInt(g[4], 36));
            s.gravity = Math.max(0.05f, Integer.parseInt(g[5], 36) / 100f);
            s.glitch = Integer.parseInt(g[6], 36) / 35f;
            s.moons = Integer.parseInt(g[7], 36);
            int flags = Integer.parseInt(g[8], 36);
            s.vacuum = (flags & 1) != 0;
            s.dreamlike = (flags & 2) != 0;
            s.skyBlackHole = (flags & 4) != 0;
            s.planetRings = (flags & 8) != 0;
            s.binarySun = (flags & 16) != 0;
            if (s.planetRings && s.planetSize < 0.25f) s.planetSize = 0.35f;
            s.id = id;
            return s;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

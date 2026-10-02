package dev.riftverse.fusion;

import dev.riftverse.universe.SpecFactory;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dimension fusion. A universe is treated as ten independent gene groups; a fusion picks each group from one parent
 * (or blends it across all of them) and assembles a new, fully specified reality. Because every group maps onto the
 * same procedural generator the result is always a real, playable universe — no combination is hand-made.
 */
public final class FusionEngine {
    public enum Gene {
        TERRAIN("Terrain"), BIOMES("Biomes & materials"), STRUCTURES("Structures"), CREATURES("Creatures"), WEATHER("Weather"),
        SKY("Sky & atmosphere"), GRAVITY("Gravity"), TIME("Time flow"), HAZARDS("Hazards"), MECHANICS("Special mechanics");

        public final String label;

        Gene(String label) {
            this.label = label;
        }
    }

    /** Choice value meaning "blend every parent". */
    public static final int BLEND = -1;

    public record Result(UniverseSpec spec, int compatibility, List<String> notes) {}

    private FusionEngine() {}

    private static float avg(List<UniverseSpec> ps, java.util.function.ToDoubleFunction<UniverseSpec> f) {
        double s = 0;
        for (UniverseSpec p : ps) s += f.applyAsDouble(p);
        return (float) (s / ps.size());
    }

    private static int mixColor(List<UniverseSpec> ps, java.util.function.ToIntFunction<UniverseSpec> f) {
        int r = 0, g = 0, b = 0;
        for (UniverseSpec p : ps) {
            int c = f.applyAsInt(p);
            r += (c >> 16) & 255;
            g += (c >> 8) & 255;
            b += c & 255;
        }
        int n = ps.size();
        return ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    /** How well parents fit together, 0-100. Low values make the result unstable. */
    public static int compatibility(List<UniverseSpec> parents, int[] choice) {
        if (parents.size() < 2) return 100;
        int score = 100 - (parents.size() - 2) * 8;
        float gMin = Float.MAX_VALUE, gMax = 0;
        boolean anyVacuum = false, anySea = false, anyDream = false;
        float glitch = 0;
        java.util.Set<Object> terrains = new java.util.HashSet<>(), times = new java.util.HashSet<>();
        for (UniverseSpec p : parents) {
            gMin = Math.min(gMin, p.gravity);
            gMax = Math.max(gMax, p.gravity);
            anyVacuum |= p.vacuum;
            anySea |= p.hasSea;
            anyDream |= p.dreamlike;
            glitch = Math.max(glitch, p.glitch);
            terrains.add(p.terrain);
            times.add(p.time);
        }
        score -= (int) Math.min(25, (gMax - gMin) * 20);
        if (anyVacuum && anySea) score -= 12;
        if (terrains.size() > 1) score -= 6 * (terrains.size() - 1);
        if (times.size() > 1) score -= 4 * (times.size() - 1);
        if (anyDream && glitch > 0.3f) score -= 10;
        score -= (int) (glitch * 15);
        int blends = 0;
        for (int c : choice) if (c == BLEND) blends++;
        score -= blends * 2;
        return Math.max(0, Math.min(100, score));
    }

    public static Result fuse(List<UniverseSpec> parents, int[] choice, String name, UniverseId id, long seed) {
        Random r = new Random(seed);
        List<String> notes = new ArrayList<>();
        UniverseSpec base = parents.get(Math.max(0, choice[Gene.CREATURES.ordinal()]) % parents.size());
        UniverseSpec s = SpecFactory.random(id, seed, base.archetype, new Random(seed));
        for (Gene g : Gene.values()) {
            int c = choice[g.ordinal()];
            UniverseSpec p = c == BLEND ? parents.get(r.nextInt(parents.size())) : parents.get(Math.min(c, parents.size() - 1));
            boolean blend = c == BLEND;
            switch (g) {
                case TERRAIN -> {
                    s.terrain = p.terrain;
                    s.baseHeight = blend ? (int) avg(parents, q -> q.baseHeight) : p.baseHeight;
                    s.amplitude = blend ? avg(parents, q -> q.amplitude) : p.amplitude;
                    s.roughness = blend ? avg(parents, q -> q.roughness) : p.roughness;
                    s.seaLevel = blend ? (int) avg(parents, q -> q.seaLevel) : p.seaLevel;
                    s.hasSea = p.hasSea;
                    s.islandDensity = blend ? avg(parents, q -> q.islandDensity) : p.islandDensity;
                }
                case BIOMES -> {
                    s.materials = p.materials;
                    s.decorDensity = blend ? avg(parents, q -> q.decorDensity) : p.decorDensity;
                }
                case STRUCTURES -> {
                    s.mega = p.mega;
                    s.megaDensity = blend ? avg(parents, q -> q.megaDensity) : p.megaDensity;
                }
                case CREATURES -> {
                    s.archetype = p.archetype;
                    s.hostility = blend ? avg(parents, q -> q.hostility) : p.hostility;
                    s.creatureScale = blend ? avg(parents, q -> q.creatureScale) : p.creatureScale;
                }
                case WEATHER -> {
                    s.weather = p.weather;
                    s.stormIntensity = blend ? avg(parents, q -> q.stormIntensity) : p.stormIntensity;
                    s.fogDensity = blend ? avg(parents, q -> q.fogDensity) : p.fogDensity;
                    s.fogColor = blend ? mixColor(parents, q -> q.fogColor) : p.fogColor;
                }
                case SKY -> {
                    s.skyTop = blend ? mixColor(parents, q -> q.skyTop) : p.skyTop;
                    s.skyHorizon = blend ? mixColor(parents, q -> q.skyHorizon) : p.skyHorizon;
                    s.nebulaA = blend ? mixColor(parents, q -> q.nebulaA) : p.nebulaA;
                    s.nebulaB = blend ? mixColor(parents, q -> q.nebulaB) : p.nebulaB;
                    s.sunColor = p.sunColor;
                    s.accent = blend ? mixColor(parents, q -> q.accent) : p.accent;
                    s.starDensity = blend ? avg(parents, q -> q.starDensity) : p.starDensity;
                    s.nebulaIntensity = blend ? avg(parents, q -> q.nebulaIntensity) : p.nebulaIntensity;
                    s.auroraIntensity = blend ? avg(parents, q -> q.auroraIntensity) : p.auroraIntensity;
                    s.galaxyIntensity = blend ? avg(parents, q -> q.galaxyIntensity) : p.galaxyIntensity;
                    s.moons = p.moons;
                    s.planetSize = p.planetSize;
                    s.planetRings = p.planetRings;
                    s.skyBlackHole = p.skyBlackHole;
                    s.binarySun = p.binarySun;
                    s.sunSize = p.sunSize;
                    s.gradeTint = p.gradeTint;
                    s.gradeStrength = p.gradeStrength;
                    s.saturation = blend ? avg(parents, q -> q.saturation) : p.saturation;
                    s.artStyle = p.artStyle;
                    s.artStyle2 = blend ? parents.get((parents.indexOf(p) + 1) % parents.size()).artStyle : p.artStyle2;
                }
                case GRAVITY -> s.gravity = blend ? avg(parents, q -> q.gravity) : p.gravity;
                case TIME -> s.time = p.time;
                case HAZARDS -> {
                    s.glitch = blend ? avg(parents, q -> q.glitch) : p.glitch;
                    s.vacuum = p.vacuum;
                    s.naturalBlackHoles = p.naturalBlackHoles;
                }
                case MECHANICS -> {
                    s.dreamlike = p.dreamlike;
                    s.music = p.music;
                }
            }
        }
        // reconcile genes that cannot coexist
        if (s.vacuum && s.hasSea) {
            s.hasSea = false;
            notes.add("Vacuum and oceans are incompatible: the seas boiled away into space.");
        }
        if (s.gravity < 0.05f) {
            s.gravity = 0.05f;
            notes.add("Gravity clamped to a survivable minimum.");
        }
        if (s.amplitude > 140f) {
            s.amplitude = 140f;
            notes.add("Terrain height clamped to the world's build limits.");
        }
        int compat = compatibility(parents, choice);
        int instability = 100 - compat;
        if (instability > 0 && r.nextInt(150) < instability) {
            SpecFactory.mutate(s, r);
            s.glitch = Math.min(1f, s.glitch + instability / 200f);
            notes.add("Fusion instability mutated the result (" + instability + "% risk).");
        }
        s.name = name == null || name.isBlank() ? fusedName(parents, r) : name.trim();
        s.prompt = "fusion of " + String.join(" + ", parents.stream().map(q -> q.name).toList());
        s.id = id;
        return new Result(s, compat, notes);
    }

    private static String fusedName(List<UniverseSpec> parents, Random r) {
        String a = parents.get(0).name.split(" ")[0];
        String b = parents.get(parents.size() - 1).name;
        String[] w = b.split(" ");
        return a + "-" + w[w.length - 1];
    }
}

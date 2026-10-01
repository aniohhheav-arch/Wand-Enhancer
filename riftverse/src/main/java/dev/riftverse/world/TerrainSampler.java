package dev.riftverse.world;

import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.util.Hash;
import dev.riftverse.util.Noise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Column-based terrain model for one universe. Everything is expressed as 2D fields (ground height, island slabs,
 * ceiling) so whole chunks can be filled quickly and spawn points can be predicted without generating chunks.
 */
public final class TerrainSampler {
    public static final int NONE = Integer.MIN_VALUE;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;

    private static final Map<Long, TerrainSampler> CACHE = new ConcurrentHashMap<>();

    public final UniverseSpec spec;
    private final Noise base;
    private final Noise detail;
    private final Noise islands;
    private final Noise aux;
    private final Noise ceiling;
    private final Noise veins;
    private final int originX;
    private final int originZ;
    private final double duneAngleCos;
    private final double duneAngleSin;

    private TerrainSampler(UniverseSpec spec) {
        this.spec = spec;
        long s = spec.seed;
        this.base = new Noise(Hash.of(s, 1));
        this.detail = new Noise(Hash.of(s, 2));
        this.islands = new Noise(Hash.of(s, 3));
        this.aux = new Noise(Hash.of(s, 4));
        this.ceiling = new Noise(Hash.of(s, 5));
        this.veins = new Noise(Hash.of(s, 6));
        this.originX = spec.id.centerX();
        this.originZ = spec.id.centerZ();
        double a = Hash.unit(Hash.of(s, 7)) * Math.PI;
        this.duneAngleCos = Math.cos(a);
        this.duneAngleSin = Math.sin(a);
    }

    public static TerrainSampler of(UniverseSpec spec) {
        TerrainSampler cached = CACHE.get(spec.id.pack());
        if (cached != null && cached.spec == spec) return cached;
        TerrainSampler fresh = new TerrainSampler(spec);
        CACHE.put(spec.id.pack(), fresh);
        return fresh;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Mutable per-column result to avoid allocation in hot loops. */
    public static final class Column {
        public int ground = NONE;
        public int islandTop1 = NONE;
        public int islandBottom1 = NONE;
        public int islandTop2 = NONE;
        public int islandBottom2 = NONE;
        public int ceilingBottom = NONE;
        public boolean frayed;

        void reset() {
            ground = NONE;
            islandTop1 = NONE;
            islandBottom1 = NONE;
            islandTop2 = NONE;
            islandBottom2 = NONE;
            ceilingBottom = NONE;
            frayed = false;
        }

        /** Highest walkable surface in the column, or NONE. Ceilings are ignored. */
        public int highestSurface() {
            int best = ground;
            if (islandTop1 != NONE && islandTop1 > best) best = islandTop1;
            if (islandTop2 != NONE && islandTop2 > best) best = islandTop2;
            return best;
        }
    }

    public void sample(int x, int z, Column out) {
        out.reset();
        double dx = x - originX;
        double dz = z - originZ;
        double edge = Math.max(Math.abs(dx), Math.abs(dz));
        if (edge > dev.riftverse.universe.UniverseId.BOUNDARY) {
            // The edge of a reality: terrain dissolves into nothing.
            out.frayed = true;
            return;
        }
        float f = spec.roughness;
        float amp = spec.amplitude;
        int b = spec.baseHeight;
        TerrainMode mode = spec.terrain;

        switch (mode) {
            case ROLLING -> out.ground = (int) (b + amp * base.fbm(dx / 220 * f, dz / 220 * f, 5) + 4 * detail.fbm(dx / 38, dz / 38, 2));
            case MOUNTAINS, GLACIAL -> {
                double r = base.ridged(dx / 420 * f, dz / 420 * f, 6);
                double sharp = mode == TerrainMode.GLACIAL ? 2.1 : 1.6;
                out.ground = (int) (b - amp * 0.25 + amp * 1.5 * Math.pow(r, sharp) + 7 * detail.fbm(dx / 80, dz / 80, 3));
            }
            case OCEAN -> {
                double h = b + amp * base.fbm(dx / 320, dz / 320, 4);
                double n = aux.fbm(dx / 520, dz / 520, 4);
                if (n > 0.32) h = Math.max(h, spec.seaLevel + (n - 0.32) * 120 + 3 * detail.fbm(dx / 30, dz / 30, 2));
                out.ground = (int) h;
            }
            case ARCHIPELAGO -> {
                double n = base.warped(dx / 260 * f, dz / 260 * f, 1.6, 5);
                out.ground = (int) (spec.seaLevel - 20 + n * 75 + 4 * detail.fbm(dx / 25, dz / 25, 2));
            }
            case FLOATING, FRAGMENTS -> out.ground = NONE;
            case CITY -> out.ground = b;
            case SPIRES -> {
                double h = b + amp * 0.45 * base.fbm(dx / 200 * f, dz / 200 * f, 4);
                out.ground = (int) Math.max(h, spireHeight(x, z, h));
            }
            case CRATERS -> out.ground = (int) craterHeight(x, z, b + amp * 0.4 * base.fbm(dx / 260, dz / 260, 4) + 2 * detail.fbm(dx / 20, dz / 20, 2));
            case CANYONS -> {
                double h = b + amp * 0.6 + 6 * base.fbm(dx / 150, dz / 150, 3);
                h = Math.floor(h / 5) * 5 + 2 * detail.fbm(dx / 40, dz / 40, 2);
                double c = Math.abs(aux.fbm(dx / 340 * f, dz / 340 * f, 4));
                if (c < 0.09) h -= amp * 1.2 * Math.pow(1 - c / 0.09, 0.6);
                out.ground = (int) h;
            }
            case DUNES -> {
                double u = dx * duneAngleCos + dz * duneAngleSin;
                double warp = base.fbm(dx / 160, dz / 160, 3) * 9;
                double dune = Math.sin(u / 24 + warp) * 0.5 + 0.5;
                out.ground = (int) (b + amp * 0.55 * dune * dune + amp * 0.35 * detail.fbm(dx / 220, dz / 220, 3));
            }
            case INVERTED -> {
                out.ground = (int) (b + amp * 0.6 * base.fbm(dx / 210, dz / 210, 4));
                out.ceilingBottom = (int) (MAX_Y - 34 - amp * 0.9 * (ceiling.fbm(dx / 150, dz / 150, 5) + 0.6) - 26 * Math.pow(Math.max(0, ceiling.ridged(dx / 70, dz / 70, 3) - 0.55) * 2.2, 2));
            }
            case SHATTERED -> {
                int cx = Math.floorDiv(x, 8) * 8;
                int cz = Math.floorDiv(z, 8) * 8;
                double h = b + amp * base.fbm((cx - originX) / 160.0, (cz - originZ) / 160.0, 4);
                long hh = Hash.of(spec.seed, cx, cz);
                h += Hash.range(hh, -3, 3) * 2;
                out.ground = (int) (Math.floor(h / 4) * 4);
            }
            case VOLCANIC -> {
                double r = base.ridged(dx / 300 * f, dz / 300 * f, 5);
                out.ground = (int) (b - 6 + amp * 0.9 * r * r + 5 * detail.fbm(dx / 50, dz / 50, 3));
            }
        }

        if (out.ground != NONE) out.ground = Math.max(MIN_Y + 4, Math.min(MAX_Y - 40, out.ground));

        float density = spec.islandDensity;
        if (mode == TerrainMode.FLOATING) density = Math.max(density, 0.6f);
        if (mode == TerrainMode.FRAGMENTS) density = Math.max(density, 0.35f);
        if (density > 0.01f) sampleIslands(dx, dz, density, mode, out);
    }

    private void sampleIslands(double dx, double dz, float density, TerrainMode mode, Column out) {
        boolean shards = mode == TerrainMode.FRAGMENTS;
        double scale = shards ? 95 : 175;
        double threshold = 0.36 - density * 0.32;
        double m = islands.fbm(dx / scale, dz / scale, 4);
        int floor = out.ground == NONE ? (mode == TerrainMode.FRAGMENTS ? 60 : 70) : Math.max(out.ground, spec.hasSea ? spec.seaLevel : out.ground);
        if (m > threshold) {
            double t = (m - threshold) / (1 - threshold);
            double center = Math.max(floor + 45, mode == TerrainMode.FLOATING ? 115 : 135) + 35 * islands.fbm(dx / 600 + 91.3, dz / 600 - 17.7, 2);
            double top = center + t * (shards ? 10 : 22) + 3 * detail.fbm(dx / 26 + 5, dz / 26, 2);
            double depth = t * (shards ? 60 : 110) * (0.55 + 0.45 * Math.abs(detail.fbm(dx / 50 - 3, dz / 50, 3)));
            if (shards) top = Math.floor(top / 3) * 3;
            out.islandTop1 = (int) top;
            out.islandBottom1 = (int) (top - Math.max(2, depth));
        }
        if (density > 0.45f || shards) {
            double m2 = islands.fbm(dx / (scale * 0.8) + 313.1, dz / (scale * 0.8) - 77.2, 4);
            if (m2 > threshold + 0.06) {
                double t = (m2 - threshold - 0.06) / (1 - threshold);
                double center = Math.max(floor + 95, 195) + 25 * islands.fbm(dx / 400 - 41.1, dz / 400 + 9.3, 2);
                double top = Math.min(MAX_Y - 20, center + t * 14);
                double depth = t * (shards ? 40 : 70) + 3;
                out.islandTop2 = (int) top;
                out.islandBottom2 = (int) (top - depth);
            }
        }
    }

    private double spireHeight(int x, int z, double ground) {
        int cell = 26;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        double best = ground;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long h = Hash.of(spec.seed ^ 0x5BA1L, cx + i, cz + j);
                if (Hash.unit(h) > 0.55f) continue;
                double px = (cx + i) * cell + Hash.unit(Hash.mix(h)) * cell;
                double pz = (cz + j) * cell + Hash.unit(Hash.mix(h + 1)) * cell;
                double r = 3 + Hash.unit(Hash.mix(h + 2)) * 6;
                double height = 18 + Hash.unit(Hash.mix(h + 3)) * spec.amplitude * 2.2;
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (d < r) best = Math.max(best, ground + height * Math.pow(1 - d / r, 1.4));
            }
        }
        return best;
    }

    private double craterHeight(int x, int z, double h) {
        int cell = 110;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long hh = Hash.of(spec.seed ^ 0xC4A7L, cx + i, cz + j);
                if (Hash.unit(hh) > 0.7f) continue;
                double r = 12 + Hash.unit(Hash.mix(hh)) * 34;
                double px = (cx + i) * cell + r + Hash.unit(Hash.mix(hh + 1)) * (cell - 2 * r);
                double pz = (cz + j) * cell + r + Hash.unit(Hash.mix(hh + 2)) * (cell - 2 * r);
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)) / r;
                if (d < 1.0) h -= (1 - d * d) * r * 0.42;
                else if (d < 1.4) h += Math.max(0, 1 - Math.abs(d - 1.12) / 0.28) * r * 0.13;
            }
        }
        return h;
    }

    /** Whether a buried block at this position becomes a glowing vein. */
    public boolean veinAt(int x, int y, int z) {
        return veins.noise(x / 11.0, y / 9.0, z / 11.0) > 0.58;
    }

    /** Predicts a safe standing Y near (x, z), searching outward in a spiral. Returns {x, y, z} or null. */
    public int[] findSurface(int x, int z, int radius) {
        Column c = new Column();
        for (int r = 0; r <= radius; r += 6) {
            int steps = r == 0 ? 1 : Math.max(8, r / 3);
            for (int i = 0; i < steps; i++) {
                double a = (Math.PI * 2 * i) / steps;
                int px = x + (int) Math.round(Math.cos(a) * r);
                int pz = z + (int) Math.round(Math.sin(a) * r);
                sample(px, pz, c);
                int top = c.highestSurface();
                if (top == NONE) continue;
                if (spec.hasSea && top < spec.seaLevel && spec.terrain != TerrainMode.CITY) continue;
                if (c.ceilingBottom != NONE && c.ceilingBottom - top < 6) continue;
                return new int[] {px, top + 1, pz};
            }
        }
        return null;
    }
}

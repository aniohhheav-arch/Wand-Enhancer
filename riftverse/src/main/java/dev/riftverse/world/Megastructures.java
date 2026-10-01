package dev.riftverse.world;

import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.MegaKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.util.Hash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Colossal, analytically-defined landmarks. Each structure is a signed-distance style function evaluated per block, so
 * a single landmark can span dozens of chunks and still generate seamlessly and deterministically.
 */
public final class Megastructures {
    public static final int CELL = 288;
    private static final int MAX_REACH = 140;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private Megastructures() {}

    public static final class Placement {
        public final MegaKind kind;
        public final int x;
        public final int y;
        public final int z;
        public final long seed;
        int radius;
        int minY;
        int maxY;
        final double[] p = new double[16];
        double[][] parts = new double[0][];

        Placement(MegaKind kind, int x, int y, int z, long seed) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.seed = seed;
        }

        public boolean intersects(int minX, int minZ, int maxX, int maxZ) {
            return x + radius >= minX && x - radius <= maxX && z + radius >= minZ && z - radius <= maxZ;
        }

        public int radius() {
            return radius;
        }

        public int minY() {
            return minY;
        }

        public int maxY() {
            return maxY;
        }

        /** World-space anchor of the structure's "heart" (halo centre, ziggurat summit, ...), used for rifts and loot. */
        public int[] heart() {
            return switch (kind) {
                case HALO_RING -> new int[] {x, (int) p[3], z};
                case ZIGGURAT -> new int[] {x, y + (int) p[2] + 2, z};
                case COLOSSAL_PILLARS -> new int[] {x, y + 3, z};
                case NEON_MEGATOWER -> new int[] {x, y + (int) p[1] + 1, z};
                case MONOLITH -> new int[] {x, y + (int) p[2] + 3, z};
                default -> new int[] {x, y + 2, z};
            };
        }
    }

    /** All landmarks whose footprint touches the given block rectangle. */
    public static List<Placement> touching(UniverseSpec spec, TerrainSampler sampler, int minX, int minZ, int maxX, int maxZ) {
        List<Placement> out = new ArrayList<>(2);
        if (spec.megaDensity <= 0f || (spec.mega == MegaKind.NONE && spec.mega2 == MegaKind.NONE)) return out;
        int c0x = Math.floorDiv(minX - MAX_REACH, CELL);
        int c1x = Math.floorDiv(maxX + MAX_REACH, CELL);
        int c0z = Math.floorDiv(minZ - MAX_REACH, CELL);
        int c1z = Math.floorDiv(maxZ + MAX_REACH, CELL);
        TerrainSampler.Column col = new TerrainSampler.Column();
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                tryPlace(spec, sampler, col, spec.mega, cx, cz, 0, spec.megaDensity * 0.85f, minX, minZ, maxX, maxZ, out);
                tryPlace(spec, sampler, col, spec.mega2, cx, cz, 1, spec.megaDensity * 0.45f, minX, minZ, maxX, maxZ, out);
            }
        }
        return out;
    }

    private static void tryPlace(UniverseSpec spec, TerrainSampler sampler, TerrainSampler.Column col, MegaKind kind, int cx, int cz, int slot,
                                 float chance, int minX, int minZ, int maxX, int maxZ, List<Placement> out) {
        if (kind == MegaKind.NONE) return;
        long h = Hash.of(spec.seed ^ 0x3E6AL, cx, cz, slot);
        if (Hash.unit(h) >= chance) return;
        int px = cx * CELL + 24 + Hash.range(Hash.mix(h + 1), 0, CELL - 48);
        int pz = cz * CELL + 24 + Hash.range(Hash.mix(h + 2), 0, CELL - 48);
        if (slot == 1) {
            px += CELL / 2;
            pz += CELL / 3;
        }
        sampler.sample(px, pz, col);
        if (col.frayed) return;
        int base = col.highestSurface();
        if (base == TerrainSampler.NONE) base = 96 + Hash.range(Hash.mix(h + 3), 0, 60);
        if (spec.terrain == TerrainMode.OCEAN && col.ground != TerrainSampler.NONE && col.ground < spec.seaLevel) base = col.ground;
        Placement p = build(kind, px, base + 1, pz, Hash.mix(h + 4), spec);
        double ddx = px - spec.id.centerX();
        double ddz = pz - spec.id.centerZ();
        if (Math.sqrt(ddx * ddx + ddz * ddz) < p.radius + 40) return;
        if (p.intersects(minX, minZ, maxX, maxZ)) out.add(p);
    }

    private static float r(long seed, int i) {
        return Hash.unit(Hash.of(seed, i));
    }

    private static Placement build(MegaKind kind, int x, int y, int z, long seed, UniverseSpec spec) {
        Placement p = new Placement(kind, x, y, z, seed);
        float s = Math.min(1.6f, 0.75f + spec.amplitude / 80f);
        switch (kind) {
            case NEON_MEGATOWER -> {
                double w = 10 + r(seed, 0) * 10;
                double hgt = 110 + r(seed, 1) * 120;
                p.p[0] = w;
                p.p[1] = hgt;
                p.radius = (int) w + 3;
                p.minY = y - 30;
                p.maxY = y + (int) hgt + 30;
            }
            case CRYSTAL_SPIRE, ICE_SPIRE -> {
                boolean ice = kind == MegaKind.ICE_SPIRE;
                int count = 1 + 2 + (int) (r(seed, 0) * 3);
                p.parts = new double[count][];
                double mainR = (ice ? 5 : 7) + r(seed, 1) * (ice ? 6 : 11);
                double mainH = (ice ? 80 : 60) + r(seed, 2) * (ice ? 110 : 140) * s;
                double maxExtent = 0;
                for (int i = 0; i < count; i++) {
                    double a = r(seed, 10 + i) * Math.PI * 2;
                    double dist = i == 0 ? 0 : mainR * (1.3 + r(seed, 20 + i) * 1.4);
                    double rad = i == 0 ? mainR : mainR * (0.3 + r(seed, 30 + i) * 0.35);
                    double hh = i == 0 ? mainH : mainH * (0.22 + r(seed, 40 + i) * 0.35);
                    double tilt = i == 0 ? 0.08 : 0.18 + r(seed, 50 + i) * 0.22;
                    double ox = Math.cos(a) * dist;
                    double oz = Math.sin(a) * dist;
                    double tx = Math.cos(a) * tilt + (i == 0 ? (r(seed, 60) - 0.5) * 0.2 : 0);
                    double tz = Math.sin(a) * tilt + (i == 0 ? (r(seed, 61) - 0.5) * 0.2 : 0);
                    p.parts[i] = new double[] {ox, oz, rad, hh, tx, tz};
                    maxExtent = Math.max(maxExtent, Math.abs(ox) + Math.abs(oz) + rad + (Math.abs(tx) + Math.abs(tz)) * hh);
                }
                p.radius = (int) Math.min(MAX_REACH, maxExtent + 2);
                p.minY = y - 6;
                p.maxY = y + (int) mainH + 2;
            }
            case ZIGGURAT -> {
                double size = 22 + r(seed, 0) * 22;
                int levels = (int) ((size - 6) / 4);
                p.p[0] = size;
                p.p[1] = levels;
                p.p[2] = levels * 6;
                p.radius = (int) size + 2;
                p.minY = y - 10;
                p.maxY = y + levels * 6 + 9;
            }
            case COLOSSAL_PILLARS -> {
                double ring = 26 + r(seed, 0) * 26;
                int n = 8 + (int) (r(seed, 1) * 5);
                double hgt = 45 + r(seed, 2) * 50 * s;
                p.p[0] = ring;
                p.p[1] = n;
                p.p[2] = hgt;
                p.p[3] = r(seed, 3) < 0.5 ? 3 : 4;
                p.radius = (int) (ring + p.p[3] + 2);
                p.minY = y - 8;
                p.maxY = y + (int) hgt + 5;
            }
            case HALO_RING -> {
                double ring = 45 + r(seed, 0) * 45 * Math.min(1.2f, s);
                double tube = 4 + r(seed, 1) * 3;
                double angle = r(seed, 2) * Math.PI;
                p.p[0] = ring;
                p.p[1] = tube;
                p.p[2] = angle;
                p.p[3] = y + ring * 0.72;
                p.radius = (int) Math.min(MAX_REACH, ring + tube + 2);
                p.minY = y - 4;
                p.maxY = (int) (p.p[3] + ring + tube + 1);
            }
            case GIANT_TREE -> {
                double trunk = 3 + r(seed, 0) * 5;
                double hgt = 60 + r(seed, 1) * 80 * s;
                double canopy = 16 + r(seed, 2) * 14;
                int clusters = 4 + (int) (r(seed, 3) * 3);
                p.p[0] = trunk;
                p.p[1] = hgt;
                p.p[2] = r(seed, 4) * Math.PI * 2;
                p.parts = new double[clusters][];
                for (int i = 0; i < clusters; i++) {
                    double a = r(seed, 10 + i) * Math.PI * 2;
                    double d = (i == 0 ? 0 : 0.35 + r(seed, 20 + i) * 0.3) * canopy;
                    double oy = hgt * (i == 0 ? 0.95 : 0.6 + r(seed, 30 + i) * 0.3);
                    double rx = canopy * (0.55 + r(seed, 40 + i) * 0.3);
                    double ry = 6 + r(seed, 50 + i) * 5;
                    p.parts[i] = new double[] {Math.cos(a) * d, oy, Math.sin(a) * d, rx, ry};
                }
                p.radius = (int) (canopy * 1.5 + trunk + 4);
                p.minY = y - 6;
                p.maxY = y + (int) (hgt * 1.0 + 16);
            }
            case GIANT_MUSHROOM -> {
                double stem = 2 + r(seed, 0) * 4;
                double hgt = 25 + r(seed, 1) * 45 * s;
                double cap = 10 + r(seed, 2) * 16;
                double thick = 5 + r(seed, 3) * 5;
                p.p[0] = stem;
                p.p[1] = hgt;
                p.p[2] = cap;
                p.p[3] = thick;
                p.p[4] = r(seed, 4) * Math.PI * 2;
                p.radius = (int) (cap + 8);
                p.minY = y - 4;
                p.maxY = y + (int) (hgt + thick + 2);
            }
            case MONOLITH -> {
                double a = 2 + (int) (r(seed, 0) * 2);
                double b = 2 + (int) (r(seed, 1) * 2);
                double hgt = 40 + r(seed, 2) * 70 * s;
                p.p[0] = a;
                p.p[1] = b;
                p.p[2] = hgt;
                p.p[3] = (r(seed, 3) - 0.5) * 0.16;
                p.parts = new double[3][];
                for (int i = 0; i < 3; i++) {
                    p.parts[i] = new double[] {(r(seed, 10 + i) - 0.5) * 10, hgt + 6 + r(seed, 20 + i) * 24, (r(seed, 30 + i) - 0.5) * 10, 1 + (int) (r(seed, 40 + i) * 3)};
                }
                p.radius = 16;
                p.minY = y - 8;
                p.maxY = y + (int) hgt + 36;
            }
            case GLITCH_CUBES -> {
                int count = 6 + (int) (r(seed, 0) * 9);
                p.parts = new double[count][];
                for (int i = 0; i < count; i++) {
                    double a = r(seed, 10 + i) * Math.PI * 2;
                    double d = r(seed, 20 + i) * 36;
                    p.parts[i] = new double[] {Math.cos(a) * d, r(seed, 30 + i) * 70, Math.sin(a) * d, 2 + (int) (r(seed, 40 + i) * 9), r(seed, 50 + i) < 0.4 ? 1 : 0, i % 3};
                }
                p.radius = 50;
                p.minY = y - 12;
                p.maxY = y + 84;
            }
            case RIBCAGE -> {
                double len = 70 + r(seed, 0) * 70;
                double angle = r(seed, 1) * Math.PI;
                double rib = 14 + r(seed, 2) * 10;
                p.p[0] = len;
                p.p[1] = angle;
                p.p[2] = rib;
                p.radius = (int) Math.min(MAX_REACH, len / 2 + 22);
                p.minY = y - 4;
                p.maxY = y + (int) rib + 24;
            }
            case VOLCANO -> {
                double rad = 45 + r(seed, 0) * 45;
                double hgt = 45 + r(seed, 1) * 65;
                p.p[0] = rad;
                p.p[1] = hgt;
                p.radius = (int) rad + 2;
                p.minY = y - 12;
                p.maxY = y + (int) hgt + 2;
            }
            case ARCH -> {
                double ra = 18 + r(seed, 0) * 30;
                double tube = 4 + r(seed, 1) * 4;
                p.p[0] = ra;
                p.p[1] = tube;
                p.p[2] = r(seed, 2) * Math.PI;
                p.radius = (int) (ra + tube + 2);
                p.minY = y - 6;
                p.maxY = y + (int) (ra + tube + 2);
            }
            default -> {
                p.radius = 0;
                p.minY = y;
                p.maxY = y;
            }
        }
        return p;
    }

    private static boolean jitter(long seed, int x, int y, int z, float chance) {
        return Hash.unit(Hash.of(seed, x, y, z)) < chance;
    }

    /** Block for this landmark at a world position, or null when the landmark leaves it untouched. */
    public static BlockState sample(Placement p, MaterialSet m, int x, int y, int z) {
        int lx = x - p.x;
        int ly = y - p.y;
        int lz = z - p.z;
        return switch (p.kind) {
            case NEON_MEGATOWER -> tower(p, m, lx, ly, lz);
            case CRYSTAL_SPIRE -> spires(p, m.accent, m.structure2, lx, ly, lz);
            case ICE_SPIRE -> spires(p, Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(), lx, ly, lz);
            case ZIGGURAT -> ziggurat(p, m, lx, ly, lz);
            case COLOSSAL_PILLARS -> pillars(p, m, lx, ly, lz);
            case HALO_RING -> halo(p, m, lx, y, lz);
            case GIANT_TREE -> tree(p, m, x, y, z, lx, ly, lz);
            case GIANT_MUSHROOM -> mushroom(p, m, x, z, lx, ly, lz);
            case MONOLITH -> monolith(p, m, lx, ly, lz);
            case GLITCH_CUBES -> glitch(p, m, lx, ly, lz);
            case RIBCAGE -> ribcage(p, lx, ly, lz);
            case VOLCANO -> volcano(p, x, z, lx, ly, lz);
            case ARCH -> arch(p, m, x, y, z, lx, ly, lz);
            default -> null;
        };
    }

    private static BlockState tower(Placement p, MaterialSet m, int lx, int ly, int lz) {
        int W = (int) p.p[0];
        int H = (int) p.p[1];
        int ax = Math.abs(lx);
        int az = Math.abs(lz);
        int t1 = (int) (H * 0.55);
        int t2 = (int) (H * 0.8);
        if (ly < 0) {
            return ax <= W + 2 && az <= W + 2 ? m.structure : null;
        }
        if (ly > H) {
            if (ly <= H + 28 && ax <= 1 && az <= 1) {
                if (ly == H + 28) return m.accent2;
                if (ax == 0 && az == 0) return m.structure;
                return ly < H + 10 ? m.structure : null;
            }
            return null;
        }
        int w = ly < t1 ? W : ly < t2 ? (int) Math.round(W * 0.75) : (int) Math.round(W * 0.5);
        if ((ly == t1 && ax <= W && az <= W) || (ly == t2 && ax <= (int) Math.round(W * 0.75) && az <= (int) Math.round(W * 0.75))) {
            if (ax > w || az > w) {
                int edge = ly == t1 ? W : (int) Math.round(W * 0.75);
                return ax == edge || az == edge ? m.accent : m.structure;
            }
        }
        if (ax > w || az > w) return null;
        if (ly == H) return m.structure;
        boolean wallX = ax == w;
        boolean wallZ = az == w;
        if (!wallX && !wallZ) {
            return ly % 8 == 0 ? m.structure : AIR;
        }
        if (ly >= 1 && ly <= 4 && Math.abs(lx) <= 1 && lz == -w) return AIR;
        if (wallX && wallZ) return m.accent2;
        if (ly % 24 == 12 || ly % 24 == 13) return m.accent;
        if (ly % 4 == 0) return m.structure;
        int along = wallX ? lz : lx;
        if (Math.floorMod(along, 3) == 0) return m.structure2;
        return m.glass;
    }

    private static BlockState spires(Placement p, BlockState shell, BlockState core, int lx, int ly, int lz) {
        for (double[] s : p.parts) {
            double hh = s[3];
            if (ly < -6 || ly > hh) continue;
            double fy = Math.max(0, ly);
            double px = lx - s[0] - s[4] * fy;
            double pz = lz - s[1] - s[5] * fy;
            double rad = s[2] * Math.pow(1 - fy / hh, 0.85);
            double q = Math.max(Math.abs(px) * 0.866 + Math.abs(pz) * 0.5, Math.abs(pz));
            if (q <= rad + 0.3) return q < rad * 0.45 ? core : shell;
        }
        return null;
    }

    private static BlockState ziggurat(Placement p, MaterialSet m, int lx, int ly, int lz) {
        int S = (int) p.p[0];
        int top = (int) p.p[2];
        int ax = Math.abs(lx);
        int az = Math.abs(lz);
        int mx = Math.max(ax, az);
        if (ly < 0) return mx <= S ? m.structure : null;
        int k = ly / 6;
        int half = S - k * 4;
        if (half < 6 || ly >= top) {
            if (ly > top + 7 || mx > 4) return null;
            if (ly == top + 7) return mx <= 2 ? m.accent2 : null;
            if (ly == top + 6) return m.accent2;
            if (ly == top) return m.structure;
            if (mx == 4) {
                if (Math.abs(lx) <= 1 && lz == -4 && ly < top + 4) return AIR;
                return (ax == 4 && az == 4) ? m.accent : m.structure;
            }
            return ly == top + 1 && mx == 0 ? m.accent : AIR;
        }
        if (mx > half) return null;
        if (Math.abs(lx) <= 2 && lz < 0) {
            double stairTop = (S + lz) * 6.0 / 4.0;
            if (ly <= stairTop) return Math.abs(lx) == 2 ? m.accent : m.structure;
            return AIR;
        }
        if (mx >= half - 1 || ly % 6 == 0) {
            if (mx == half && ly % 6 == 3) return m.accent;
            if (mx == half && ly % 6 == 5 && (ax + az) % 4 == 0) return m.accent2;
            return m.structure;
        }
        return AIR;
    }

    private static BlockState pillars(Placement p, MaterialSet m, int lx, int ly, int lz) {
        double ring = p.p[0];
        int n = (int) p.p[1];
        double hgt = p.p[2];
        int pw = (int) p.p[3];
        double dist = Math.sqrt(lx * lx + lz * lz);
        double angle = Math.atan2(lz, lx);
        if (dist < 5 && ly >= 0 && ly <= 2) return ly == 2 && dist < 2 ? m.accent2 : m.structure;
        double step = Math.PI * 2 / n;
        int idx = (int) Math.round(angle / step);
        double ca = idx * step;
        double cx = Math.cos(ca) * ring;
        double cz = Math.sin(ca) * ring;
        double ddx = lx - cx;
        double ddz = lz - cz;
        int pillarTop = (int) (hgt * (Hash.unit(Hash.of(p.seed, idx)) < 0.25 ? 0.35 + Hash.unit(Hash.of(p.seed, idx, 1)) * 0.4 : 1.0));
        if (Math.abs(ddx) <= pw && Math.abs(ddz) <= pw && ly >= -8 && ly <= pillarTop) {
            if (ly == 3 || ly == pillarTop - 3) return m.accent;
            if (Math.abs(ddx) >= pw - 0.5 && Math.abs(ddz) >= pw - 0.5) return m.structure2;
            return m.structure;
        }
        if (ly >= hgt && ly <= hgt + 3 && Math.abs(dist - ring) <= pw) {
            int seg = (int) Math.floor((angle + Math.PI) / (step / 2));
            if (Hash.unit(Hash.of(p.seed, seg, 7)) < 0.3) return null;
            return ly == (int) hgt + 3 ? m.structure2 : m.structure;
        }
        return null;
    }

    private static BlockState halo(Placement p, MaterialSet m, int lx, int y, int lz) {
        double ring = p.p[0];
        double tube = p.p[1];
        double a = p.p[2];
        double u = lx * Math.cos(a) + lz * Math.sin(a);
        double w = -lx * Math.sin(a) + lz * Math.cos(a);
        double vy = y - p.p[3];
        double q = Math.sqrt(u * u + vy * vy) - ring;
        double d2 = q * q + w * w;
        if (d2 > tube * tube) return null;
        double phi = Math.atan2(vy, u);
        int band = (int) Math.floor((phi + Math.PI) / (Math.PI * 2) * 48);
        if (q < -tube * 0.45) return m.accent;
        if (band % 6 == 0) return m.accent2;
        return (band & 1) == 0 ? m.structure2 : m.structure;
    }

    private static BlockState tree(Placement p, MaterialSet m, int x, int y, int z, int lx, int ly, int lz) {
        double trunk = p.p[0];
        double hgt = p.p[1];
        double phase = p.p[2];
        if (ly >= -6 && ly <= hgt) {
            double fy = Math.max(0, ly);
            double bx = Math.sin(fy / 31 + phase) * trunk * 0.6;
            double bz = Math.cos(fy / 27 + phase) * trunk * 0.6;
            double rad = trunk * (1 - 0.4 * fy / hgt) + 7 * Math.exp(-fy / 5);
            double ddx = lx - bx;
            double ddz = lz - bz;
            double d = Math.sqrt(ddx * ddx + ddz * ddz);
            if (d <= rad) {
                if (d > rad - 1.2) {
                    double ang = Math.atan2(ddz, ddx);
                    if (Math.floorMod((int) Math.floor((ang / (Math.PI * 2)) * 10 + fy / 7.0), 10) == 0) return m.accent;
                }
                return m.log;
            }
        }
        for (double[] c : p.parts) {
            double ox = c[0], oy = c[1], oz = c[2], rx = c[3], ry = c[4];
            double sx = (lx - ox) / rx;
            double sy = (ly - oy) / ry;
            double sz = (lz - oz) / rx;
            double e = sx * sx + sy * sy + sz * sz;
            double wobble = (Hash.unit(Hash.of(p.seed, x >> 2, y >> 2, z >> 2)) - 0.5) * 0.35;
            if (e <= 1 + wobble) {
                if (jitter(p.seed, x, y, z, 0.018f)) return m.accent;
                return m.leaves.isAir() ? m.accent2 : m.leaves;
            }
            if (ly < oy && ly > oy - 20) {
                double t = (oy - ly) / 20.0;
                double bx = ox * (1 - t);
                double bz = oz * (1 - t);
                double ddx = lx - bx;
                double ddz = lz - bz;
                if (ddx * ddx + ddz * ddz <= 2.6) return m.log;
            }
        }
        return null;
    }

    private static BlockState mushroom(Placement p, MaterialSet m, int x, int z, int lx, int ly, int lz) {
        double stem = p.p[0];
        double hgt = p.p[1];
        double cap = p.p[2];
        double thick = p.p[3];
        double phase = p.p[4];
        double fy = Math.max(0, ly);
        double bend = Math.sin(fy / (hgt * 0.9) * Math.PI * 0.5) * stem * 1.6;
        double bx = Math.cos(phase) * bend;
        double bz = Math.sin(phase) * bend;
        if (ly >= -4 && ly < hgt) {
            double ddx = lx - bx;
            double ddz = lz - bz;
            if (ddx * ddx + ddz * ddz <= (stem + 2.0 * Math.exp(-fy / 3)) * (stem + 2.0 * Math.exp(-fy / 3))) return m.log;
        }
        double capX = lx - Math.cos(phase) * stem * 1.6;
        double capZ = lz - Math.sin(phase) * stem * 1.6;
        double rr = (capX * capX + capZ * capZ) / (cap * cap);
        double yy = (ly - hgt) / thick;
        if (ly >= hgt - 2 && rr + yy * yy <= 1.0) {
            if (ly == (int) hgt - 2 || ly == (int) hgt - 1) return rr > 0.15 ? m.accent2 : m.log;
            if (rr + yy * yy > 0.72 && Hash.unit(Hash.of(p.seed, Math.floorDiv(x, 3), Math.floorDiv(z, 3))) < 0.22) return m.accent;
            return m.leaves.isAir() ? m.structure : m.leaves;
        }
        return null;
    }

    private static BlockState monolith(Placement p, MaterialSet m, int lx, int ly, int lz) {
        int a = (int) p.p[0];
        int b = (int) p.p[1];
        int hgt = (int) p.p[2];
        double tilt = p.p[3];
        if (ly >= -8 && ly <= hgt) {
            int px = (int) Math.round(lx - tilt * Math.max(0, ly));
            if (Math.abs(px) <= a && Math.abs(lz) <= b) {
                if (ly == hgt) return m.accent2;
                if ((Math.abs(px) == a && lz == 0) || (Math.abs(lz) == b && px == 0)) return Math.floorMod(ly, 9) < 6 ? m.accent : m.structure;
                return m.structure;
            }
        }
        for (double[] s : p.parts) {
            int half = (int) s[3];
            if (Math.abs(lx - s[0]) <= half && Math.abs(ly - s[1]) <= half && Math.abs(lz - s[2]) <= half) return m.structure;
        }
        return null;
    }

    private static BlockState glitch(Placement p, MaterialSet m, int lx, int ly, int lz) {
        for (double[] c : p.parts) {
            double half = c[3];
            double dx = Math.abs(lx - c[0]);
            double dy = Math.abs(ly - c[1]);
            double dz = Math.abs(lz - c[2]);
            if (dx > half || dy > half || dz > half) continue;
            int type = (int) c[5];
            BlockState mat = type == 0 ? m.accent : type == 1 ? m.structure2 : m.structure;
            if (c[4] > 0) {
                int edges = (dx >= half - 0.5 ? 1 : 0) + (dy >= half - 0.5 ? 1 : 0) + (dz >= half - 0.5 ? 1 : 0);
                if (edges >= 2) return m.accent;
                continue;
            }
            return mat;
        }
        return null;
    }

    private static BlockState ribcage(Placement p, int lx, int ly, int lz) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        double len = p.p[0];
        double a = p.p[1];
        double rib = p.p[2];
        double s = lx * Math.cos(a) + lz * Math.sin(a);
        double w = -lx * Math.sin(a) + lz * Math.cos(a);
        double t = (s + len / 2) / len;
        if (t >= 0 && t <= 1) {
            double ribR = rib * Math.pow(Math.sin(t * Math.PI), 0.5) + 3;
            double spineY = ribR;
            if ((ly - spineY) * (ly - spineY) + w * w <= 5.8) return bone;
            int k = (int) Math.round((s + len / 2) / 9.0);
            double sk = k * 9.0 - len / 2;
            double tk = (sk + len / 2) / len;
            if (Math.abs(s - sk) <= 1.2 && tk > 0.12 && tk < 0.88) {
                double rk = rib * Math.pow(Math.sin(tk * Math.PI), 0.5) + 3;
                double d = Math.sqrt(w * w + ly * ly) - rk;
                if (Math.abs(d) <= 1.5 && ly >= -3) return bone;
            }
        }
        double s0 = -len / 2 - 8;
        double dx = s - s0;
        double dy = ly - 9;
        double dist = Math.sqrt(dx * dx + dy * dy + w * w);
        if (Math.abs(dist - 9) <= 1.3) {
            for (int side = -1; side <= 1; side += 2) {
                double ex = s - (s0 - 6.5);
                double ew = w - side * 3.5;
                double ey = ly - 11;
                if (ex * ex + ew * ew + ey * ey < 7.5) return null;
            }
            return bone;
        }
        return null;
    }

    private static BlockState volcano(Placement p, int x, int z, int lx, int ly, int lz) {
        double rad = p.p[0];
        double hgt = p.p[1];
        double d = Math.sqrt(lx * lx + lz * lz);
        if (d > rad) return null;
        double crater = rad * 0.16;
        if (ly < -12) return null;
        if (d < crater) {
            double floor = hgt - 16;
            if (ly <= floor) return Blocks.BASALT.defaultBlockState();
            if (ly <= hgt - 10) return Blocks.LAVA.defaultBlockState();
            return null;
        }
        double surf = hgt * Math.pow(1 - d / rad, 1.25);
        if (d < crater * 1.4) surf = Math.max(surf, hgt - 6);
        if (ly > surf) return null;
        if (ly > surf - 1.5) {
            double ang = Math.atan2(lz, lx);
            double streak = Math.abs(Math.sin(ang * 5 + p.seed % 7));
            if (streak < 0.07 + 0.05 * Hash.unit(Hash.of(p.seed, x >> 3, z >> 3))) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return Blocks.BASALT.defaultBlockState();
    }

    private static BlockState arch(Placement p, MaterialSet m, int x, int y, int z, int lx, int ly, int lz) {
        double ra = p.p[0];
        double tube = p.p[1];
        double a = p.p[2];
        if (ly < -6) return null;
        double u = lx * Math.cos(a) + lz * Math.sin(a);
        double w = -lx * Math.sin(a) + lz * Math.cos(a);
        double q = Math.sqrt(u * u + ly * ly) - ra;
        double wob = 0.85 + 0.3 * Hash.unit(Hash.of(p.seed, x >> 2, y >> 2, z >> 2));
        double lim = tube * wob;
        if (q * q + w * w > lim * lim) return null;
        if (jitter(p.seed, x, y, z, 0.035f)) return m.accent;
        if (q > tube * 0.4 && ly > ra * 0.6) return m.surface;
        return m.stone;
    }
}

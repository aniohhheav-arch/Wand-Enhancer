package dev.riftverse.world;

import dev.riftverse.registry.RvBlocks;
import dev.riftverse.universe.Archetype;
import dev.riftverse.util.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Multiverse Hub: a colossal floating citadel suspended in the space between realities. The layout is analytic so
 * it can be generated chunk by chunk; interactive pieces (gates, consoles, altar) are placed by {@link NexusChunkGenerator}.
 */
public final class NexusLayout {
    public static final int TOP = 100;
    public static final int PLATFORM_R = 64;
    public static final int GATE_SIDE = 46;
    public static final int SATELLITE_DIST = 117;
    public static final int SATELLITE_R = 18;
    public static final int ARENA_R = 26;
    public static final BlockPos ARRIVAL = new BlockPos(0, TOP + 1, 24);
    public static final BlockPos CORE = new BlockPos(0, 152, 0);

    /** Gate centres (bottom-centre block of the portal opening) and the axis the portal plane runs along. */
    public record Gate(int index, int x, int z, Direction.Axis axis) {}

    public static final Gate[] GATES = buildGates();

    private NexusLayout() {}

    /** Spreads one gate per archetype evenly around the four sides of the platform, however many realities exist. */
    private static Gate[] buildGates() {
        // up to 64 gates fit on the platform (40 outer, 24 inner); later realities are reached by rift, console and corridor
        int n = Math.min(Archetype.values().length, 64);
        Gate[] g = new Gate[n];
        // the outer ring holds 40 gates on the platform's edge; any further realities get an inner ring
        int outer = Math.min(n, 40);
        place(g, 0, outer, GATE_SIDE, 78);
        if (n > outer) place(g, outer, n - outer, 30, 48);
        return g;
    }

    private static void place(Gate[] g, int start, int n, int side, int span) {
        int perSide = (n + 3) / 4;
        for (int j = 0; j < n; j++) {
            int i = start + j;
            int s = j / perSide;
            int k = j % perSide;
            int count = Math.min(perSide, n - s * perSide);
            int off = count == 1 ? 0 : -span / 2 + Math.round(k * span / (float) (count - 1));
            g[i] = switch (s) {
                case 0 -> new Gate(i, off, -side, Direction.Axis.X);
                case 1 -> new Gate(i, side, off, Direction.Axis.Z);
                case 2 -> new Gate(i, -off, side, Direction.Axis.X);
                default -> new Gate(i, -side, -off, Direction.Axis.Z);
            };
        }
    }

    /**
     * Satellite centres, in order: NE (random gate), NW (boss arena), SE (home gate), SW (archive), then the expansion
     * ring on the cardinal axes: N (Infinite Corridor gate), E (event observatory), S (convergence spire), W (Reality
     * Architect's dais).
     */
    public static int[] satellite(int i) {
        if (i >= 4) {
            int d = SATELLITE_DIST;
            return switch (i) {
                case SAT_CORRIDOR -> new int[] {0, -d};
                case SAT_OBSERVATORY -> new int[] {d, 0};
                case SAT_CONVERGENCE -> new int[] {0, d};
                default -> new int[] {-d, 0};
            };
        }
        int sx = (i == 0 || i == 2) ? 1 : -1;
        int sz = (i == 0 || i == 1) ? -1 : 1;
        int d = (int) Math.round(SATELLITE_DIST / Math.sqrt(2));
        return new int[] {sx * d, sz * d};
    }

    public static final int SAT_RANDOM = 0;
    public static final int SAT_ARENA = 1;
    public static final int SAT_HOME = 2;
    public static final int SAT_ARCHIVE = 3;
    public static final int SAT_CORRIDOR = 4;
    public static final int SAT_OBSERVATORY = 5;
    public static final int SAT_CONVERGENCE = 6;
    public static final int SAT_ARCHITECT = 7;
    public static final int SATELLITES = 8;

    private static BlockState stone() {
        return RvBlocks.NEXUS_STONE.get().defaultBlockState();
    }

    private static BlockState bricks() {
        return RvBlocks.NEXUS_BRICKS.get().defaultBlockState();
    }

    private static BlockState glow() {
        return RvBlocks.NEXUS_GLOW.get().defaultBlockState();
    }

    /** Terrain-level block at a position, or null for empty space. */
    public static BlockState stateAt(int x, int y, int z, long seed) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        BlockState s = platform(x, y, z, r);
        if (s != null) return s;
        s = gateFrame(x, y, z);
        if (s != null) return s;
        for (int i = 0; i < SATELLITES; i++) {
            s = bridge(x, y, z, i);
            if (s != null) return s;
            s = satellitePlatform(x, y, z, i);
            if (s != null) return s;
        }
        if (r > 150 && r < 330) return fragment(x, y, z, seed);
        return null;
    }

    private static BlockState platform(int x, int y, int z, double r) {
        if (r > PLATFORM_R + 0.5) return null;
        if (y == TOP) {
            if (r >= PLATFORM_R - 2) return bricks();
            for (double ring : new double[] {12, 24, 36, 50, 58}) if (Math.abs(r - ring) < 0.55) return glow();
            if (r > 12 && r < 58) {
                double ang = Math.atan2(z, x);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    double perp = Math.abs(-Math.sin(a) * x + Math.cos(a) * z);
                    double along = Math.cos(a) * x + Math.sin(a) * z;
                    if (along > 0 && perp < 0.55) return glow();
                }
                if (Math.abs(Math.sin(ang * 24)) < 0.04 && r > 40 && r < 50) return glow();
            }
            return (((x >> 2) + (z >> 2)) & 1) == 0 ? bricks() : stone();
        }
        if (y > TOP) {
            if (r >= PLATFORM_R - 2 && y <= TOP + 2) {
                double ang = Math.atan2(z, x);
                int post = (int) Math.round(ang / (Math.PI / 18));
                double pa = post * Math.PI / 18;
                double pd = Math.abs(-Math.sin(pa) * x + Math.cos(pa) * z);
                if (pd < 0.8 && y == TOP + 2) return glow();
                return y == TOP + 1 ? bricks() : null;
            }
            if (r <= 9 && y <= TOP + 2) return r >= 8 && y == TOP + 2 ? glow() : bricks();
            if (r <= 3 && y == TOP + 3) return glow();
            return null;
        }
        if (y >= TOP - 6) return r >= PLATFORM_R - 1 ? bricks() : stone();
        if (y < 28) return null;
        double cone = PLATFORM_R * Math.pow((y - 28) / (double) (TOP - 6 - 28), 1.35);
        if (r > cone) return null;
        if (r > cone - 1.6) {
            double ang = Math.atan2(z, x);
            if (Math.abs(Math.sin(ang * 12)) < 0.07) return glow();
            return bricks();
        }
        return stone();
    }

    private static BlockState gateFrame(int x, int y, int z) {
        if (y < TOP + 1 || y > TOP + 9) return null;
        for (Gate g : GATES) {
            int along = g.axis() == Direction.Axis.X ? x - g.x() : z - g.z();
            int across = g.axis() == Direction.Axis.X ? z - g.z() : x - g.x();
            if (across != 0 || Math.abs(along) > 3) continue;
            boolean side = Math.abs(along) == 3;
            boolean top = y == TOP + 9 || y == TOP + 8;
            if (side || top) {
                if ((side && top) || (side && y == TOP + 1)) return glow();
                return bricks();
            }
        }
        return null;
    }

    private static BlockState bridge(int x, int y, int z, int i) {
        int[] c = satellite(i);
        double len = Math.sqrt(c[0] * (double) c[0] + c[1] * (double) c[1]);
        double dx = c[0] / len;
        double dz = c[1] / len;
        double t = x * dx + z * dz;
        double w = -x * dz + z * dx;
        if (t < PLATFORM_R - 3 || t > SATELLITE_DIST - (i == SAT_ARENA ? ARENA_R : SATELLITE_R) + 2) return null;
        if (Math.abs(w) > 3.0) return null;
        if (y == TOP) return Math.abs(w) < 0.6 ? glow() : bricks();
        if (y == TOP - 1) return stone();
        if (y == TOP + 1 && Math.abs(w) > 2.3) return ((int) t & 3) == 0 ? glow() : bricks();
        return null;
    }

    private static BlockState satellitePlatform(int x, int y, int z, int i) {
        int[] c = satellite(i);
        int rad = i == SAT_ARENA ? ARENA_R : SATELLITE_R;
        double dx = x - c[0];
        double dz = z - c[1];
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > rad + 0.5) return null;
        if (y == TOP) {
            if (Math.abs(r - rad * 0.6) < 0.55) return glow();
            if (r > rad - 1.5) return bricks();
            return stone();
        }
        if (y == TOP + 1 && r > rad - 1.5) {
            return ((int) Math.round(Math.atan2(dz, dx) * 6) & 3) == 0 ? glow() : bricks();
        }
        if (i == SAT_OBSERVATORY && y > TOP) {
            // a slender observatory spire with a glowing lens ring
            if (r <= 2.2 && y <= TOP + 30) return y % 6 == 0 || y == TOP + 30 ? glow() : bricks();
            if (y == TOP + 24 && Math.abs(r - 6) < 0.6) return glow();
            if (y == TOP + 23 && r < 6.5 && r > 2.2 && ((int) Math.round(Math.atan2(dz, dx) * 4) & 1) == 0) return bricks();
        }
        if (i == SAT_CONVERGENCE && y > TOP && y <= TOP + 12) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                if (Math.abs(dx - Math.cos(a) * 12) <= 0.5 && Math.abs(dz - Math.sin(a) * 12) <= 0.5) return y >= TOP + 11 ? glow() : bricks();
            }
        }
        if (i == SAT_ARCHITECT && y > TOP && y <= TOP + 2 && r > 4.5 && r <= 5.5) return y == TOP + 2 ? glow() : bricks();
        if (i == SAT_ARCHIVE && y > TOP && y <= TOP + 7) {
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                if (Math.abs(dx - Math.cos(a) * 10) <= 0.5 && Math.abs(dz - Math.sin(a) * 10) <= 0.5) return y == TOP + 7 ? glow() : bricks();
            }
        }
        if (y > TOP) return null;
        if (y < TOP - 30) return null;
        double cone = rad * Math.pow((y - (TOP - 30)) / 30.0, 1.6);
        if (r > cone) return null;
        return r > cone - 1.4 && ((int) Math.round(Math.atan2(dz, dx) * 4) & 1) == 0 ? bricks() : stone();
    }

    private static BlockState fragment(int x, int y, int z, long seed) {
        int cell = 44;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        long h = Hash.of(seed ^ 0xF2A6L, cx, cz);
        if (Hash.unit(h) > 0.3f) return null;
        double px = cx * cell + 10 + Hash.unit(Hash.mix(h + 1)) * (cell - 20);
        double pz = cz * cell + 10 + Hash.unit(Hash.mix(h + 2)) * (cell - 20);
        double py = 50 + Hash.unit(Hash.mix(h + 3)) * 130;
        double rad = 5 + Hash.unit(Hash.mix(h + 4)) * 10;
        double rr = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
        if (rr > rad) return null;
        double ly = y - py;
        double topY = 2 * (1 - rr / rad);
        double bottom = -rad * 1.4 * Math.pow(1 - rr / rad, 0.7);
        if (ly > topY || ly < bottom) return null;
        MaterialSet m = MaterialSet.of(Archetype.byId((int) Long.remainderUnsigned(Hash.mix(h + 5), Archetype.values().length)));
        if (ly > topY - 1) return m.surface.isAir() ? m.stone : m.surface;
        if (ly > topY - 3) return m.sub;
        if (Hash.unit(Hash.of(seed, x, y, z)) < 0.05f) return m.accent;
        return m.stone;
    }

    public static boolean isEmptyFar(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        return r > 340;
    }

    public static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }
}

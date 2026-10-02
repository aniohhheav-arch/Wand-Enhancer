package dev.mysticarts.world;

import com.mojang.serialization.Codec;
import dev.mysticarts.MaConfig;
import dev.mysticarts.registry.MaBlocks;
import dev.mysticarts.registry.MaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Mystic structures, generated procedurally (no templates): the Sanctum Sanctorum, Ancient Mystic Temple, Abandoned
 * Sorcerer Sanctuary, Dimensional Rift site, Cosmic Ruins and the Ancient Library. Five of them guard an Infinity Stone.
 * Every structure fits within the 3x3 chunks a feature may touch.
 */
public final class MysticStructures {
    public enum Kind {
        SANCTUM("sanctum", 260),
        TEMPLE("mystic_temple", 340),
        SANCTUARY("sorcerer_sanctuary", 300),
        RIFT("dimensional_rift", 240),
        RUINS("cosmic_ruins", 160),
        LIBRARY("ancient_library", 320);

        public final String id;
        public final int rarity;

        Kind(String id, int rarity) {
            this.id = id;
            this.rarity = rarity;
        }
    }

    private MysticStructures() {}

    public static final class StructureFeature extends Feature<NoneFeatureConfiguration> {
        private final Kind kind;

        public StructureFeature(Kind kind, Codec<NoneFeatureConfiguration> codec) {
            super(codec);
            this.kind = kind;
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if (!enabled()) return false;
            RandomSource r = ctx.random();
            if (r.nextInt(kind.rarity) != 0) return false;
            ChunkPos cp = new ChunkPos(ctx.origin());
            int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
            return generate(kind, ctx.level(), r, x, z, false);
        }
    }

    private static boolean enabled() {
        try {
            return MaConfig.STRUCTURES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Builds a structure centred on (x, z). {@code force} skips the terrain checks (used by the command). */
    public static boolean generate(Kind kind, WorldGenLevel level, RandomSource r, int x, int z, boolean force) {
        int y = Build.groundY(level, x, z);
        if (kind == Kind.RUINS && level.getLevel().dimension() == Level.NETHER) {
            y = netherFloor(level, x, z);
            if (y < 0) return false;
        }
        if (y <= level.getMinBuildHeight() + 4 || y > level.getMaxBuildHeight() - 40) return false;
        BlockPos origin = new BlockPos(x, y, z);
        if (!force) {
            if (!level.getBlockState(origin.below()).getFluidState().isEmpty() || !level.getBlockState(origin).getFluidState().isEmpty()) return false;
            int spread = 0;
            for (int[] o : new int[][] {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
                spread = Math.max(spread, Math.abs(Build.groundY(level, x + o[0], z + o[1]) - y));
            }
            int tolerance = kind == Kind.RIFT || kind == Kind.SANCTUARY || kind == Kind.RUINS ? 9 : 4;
            if (spread > tolerance && kind != Kind.RUINS) return false;
        }
        Build b = new Build(level, r, origin);
        switch (kind) {
            case SANCTUM -> sanctum(b);
            case TEMPLE -> temple(b);
            case SANCTUARY -> sanctuary(b);
            case RIFT -> rift(b);
            case RUINS -> ruins(b, level.getLevel().dimension());
            case LIBRARY -> library(b);
        }
        return true;
    }

    private static int netherFloor(WorldGenLevel level, int x, int z) {
        for (int y = 32; y < 110; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above(6)).isAir()) return y;
        }
        return -1;
    }

    // ============================================================================================ palettes

    private static BlockState s(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    private static BlockState bricks() {
        return MaBlocks.MYSTIC_STONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState cracked() {
        return MaBlocks.CRACKED_MYSTIC_STONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState carved() {
        return MaBlocks.CARVED_MYSTIC_STONE.get().defaultBlockState();
    }

    private static BlockState gilded() {
        return MaBlocks.GILDED_RUNESTONE.get().defaultBlockState();
    }

    private static BlockState floor() {
        return MaBlocks.SANCTUM_FLOOR.get().defaultBlockState();
    }

    private static BlockState planks() {
        return MaBlocks.SANCTUM_PLANKS.get().defaultBlockState();
    }

    private static BlockState shelf() {
        return MaBlocks.ARCANE_BOOKSHELF.get().defaultBlockState();
    }

    private static BlockState lamp() {
        return MaBlocks.RUNE_LAMP.get().defaultBlockState();
    }

    private static BlockState mandala() {
        return MaBlocks.MANDALA_GLASS.get().defaultBlockState();
    }

    private static BlockState stairs(net.minecraft.world.level.block.Block b, Direction facing, boolean top) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    // ============================================================================================ sanctum sanctorum

    /** A three-storey townhouse of the mystic arts, crowned by the Seal of the Vishanti window. */
    private static void sanctum(Build b) {
        int h = 15;
        b.foundation(-7, -7, 7, 7, bricks());
        b.clear(-7, 0, -7, 7, h + 4, 7);
        b.fill(-7, -1, -7, 7, -1, 7, floor());
        // facade: brick with plank bands and carved corners
        b.walls(-6, 0, -6, 6, h, 6, bricks());
        for (int y : new int[] {4, 9}) b.walls(-6, y, -6, 6, y, 6, planks());
        for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) b.fill(c[0], 0, c[1], c[0], h, c[1], carved());
        b.fill(-6, 5, -6, 6, 5, 6, floor());
        b.fill(-6, 10, -6, 6, 10, 6, floor());
        b.fill(-5, 5, -5, 5, 5, 5, floor());
        b.fill(-5, 10, -5, 5, 10, 5, floor());
        // roof: stepped parapet
        b.fill(-6, h + 1, -6, 6, h + 1, 6, floor());
        b.walls(-7, h + 1, -7, 7, h + 2, 7, bricks());
        for (int i = -7; i <= 7; i += 2) {
            b.set(i, h + 3, -7, gilded());
            b.set(i, h + 3, 7, gilded());
            b.set(-7, h + 3, i, gilded());
            b.set(7, h + 3, i, gilded());
        }
        // entrance with stairs and a lintel of gilded runestone
        b.clear(-1, 0, -6, 1, 3, -6);
        b.fill(-2, 4, -6, 2, 4, -6, gilded());
        b.set(0, 0, -7, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
        b.set(-1, 0, -7, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
        b.set(1, 0, -7, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
        // windows on every floor
        for (int y : new int[] {2, 7}) {
            for (int i : new int[] {-4, 4}) {
                b.fill(i, y, -6, i, y + 1, -6, mandala());
                b.fill(i, y, 6, i, y + 1, 6, mandala());
                b.fill(-6, y, i, -6, y + 1, i, mandala());
                b.fill(6, y, i, 6, y + 1, i, mandala());
            }
        }
        // the Seal of the Vishanti: a great round window on the top floor, front and back
        b.roundWindow(-6, 13, 0, 2.6, gilded(), mandala());
        b.roundWindow(6, 13, 0, 2.6, gilded(), mandala());
        // ladder shaft in the back corner
        for (int y = 0; y <= h; y++) b.set(5, y, 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        b.clear(5, 5, 5, 5, 5, 5);
        b.clear(5, 10, 5, 5, 10, 5);
        b.set(5, 5, 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        b.set(5, 10, 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        // ground floor: the foyer, with the ritual altar on a rug of floor tiles
        b.fill(-3, -1, -3, 3, -1, 3, gilded());
        b.fill(-2, -1, -2, 2, -1, 2, floor());
        b.set(0, 0, 1, MaBlocks.RITUAL_ALTAR.get().defaultBlockState());
        b.fill(-5, 0, 5, 3, 3, 5, shelf());
        b.fill(-5, 0, -5, -5, 3, 3, shelf());
        for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {3, 3}}) b.set(c[0], 4, c[1], lamp());
        // first floor: the library and the artefact chest
        b.fill(-5, 6, -5, -5, 9, 4, shelf());
        b.fill(5, 6, -5, 5, 9, 3, shelf());
        b.fill(-3, 6, 1, 3, 7, 1, shelf());
        b.chest(-4, 6, 5, Direction.SOUTH, "sanctum");
        b.pedestal(0, 6, -3, new ItemStack(MaItems.TOME_OF_THE_INITIATE.get()), false);
        b.set(0, 9, 0, lamp());
        // top floor: the Seal chamber
        b.disc(0, 10, 0, 3.5, gilded());
        b.ring(0, 10, 0, 3.5, mandala());
        b.pedestal(0, 11, 0, new ItemStack(MaItems.SLING_RING.get()), false);
        b.chest(-4, 11, -4, Direction.EAST, "sanctum_vault");
        for (int[] c : new int[][] {{-4, 4}, {4, -4}}) b.set(c[0], 14, c[1], lamp());
    }

    // ============================================================================================ ancient mystic temple

    /** A walled courtyard and a three-tiered pagoda. The Time Stone rests at its heart. */
    private static void temple(Build b) {
        BlockState roof = s(Blocks.RED_TERRACOTTA);
        BlockState trim = gilded();
        b.foundation(-12, -12, 12, 12, bricks());
        b.clear(-12, 0, -12, 12, 24, 12);
        b.fill(-12, -1, -12, 12, -1, 12, bricks());
        // courtyard: gravel training grounds, stone paths, lanterns
        b.fill(-10, -1, -10, 10, -1, 10, s(Blocks.SMOOTH_SANDSTONE));
        b.fill(-1, -1, -12, 1, -1, 10, floor());
        b.walls(-12, 0, -12, 12, 2, 12, bricks());
        b.walls(-12, 3, -12, 12, 3, 12, roof);
        b.clear(-1, 0, -12, 1, 2, -12);
        b.fill(-2, 3, -12, 2, 4, -12, trim);
        for (int[] c : new int[][] {{-9, -9}, {9, -9}, {-9, 3}, {9, 3}, {-4, -9}, {4, -9}}) {
            b.set(c[0], 0, c[1], s(Blocks.POLISHED_ANDESITE));
            b.set(c[0], 1, c[1], s(Blocks.STONE_BRICK_WALL));
            b.set(c[0], 2, c[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        // pagoda: three shrinking tiers
        int[] half = {5, 4, 3};
        int y = 0;
        for (int t = 0; t < 3; t++) {
            int hw = half[t];
            int height = t == 0 ? 6 : 4;
            b.walls(-hw, y, -hw + 2, hw, y + height - 1, hw + 2, bricks());
            for (int[] c : new int[][] {{-hw, -hw + 2}, {hw, -hw + 2}, {-hw, hw + 2}, {hw, hw + 2}}) b.fill(c[0], y, c[1], c[0], y + height - 1, c[1], carved());
            b.fill(-hw + 1, y - 1, -hw + 3, hw - 1, y - 1, hw + 1, floor());
            y += height;
            // overhanging roof with upturned eaves
            b.fill(-hw - 1, y, -hw + 1, hw + 1, y, hw + 3, roof);
            for (int[] c : new int[][] {{-hw - 2, -hw}, {hw + 2, -hw}, {-hw - 2, hw + 4}, {hw + 2, hw + 4}}) b.set(c[0], y + 1, c[1], trim);
            b.walls(-hw - 1, y, -hw + 1, hw + 1, y, hw + 3, trim);
            y += 1;
        }
        b.fill(0, y, 2, 0, y + 3, 2, trim);
        b.set(0, y + 4, 2, lamp());
        // doorway
        b.clear(-1, 0, -3, 1, 3, -3);
        // sanctum within: mandala floor, altar, and the guarded pedestal
        b.disc(0, -1, 2, 3, gilded());
        b.ring(0, -1, 2, 3, mandala());
        b.pedestal(0, 0, 4, new ItemStack(MaItems.TIME_STONE.get()), true);
        b.set(0, 0, 0, MaBlocks.RITUAL_ALTAR.get().defaultBlockState());
        b.fill(-4, 0, 6, -4, 3, 6, shelf());
        b.fill(4, 0, 6, 4, 3, 6, shelf());
        for (int[] c : new int[][] {{-3, -1}, {3, -1}, {-3, 5}, {3, 5}}) b.set(c[0], 4, c[1], lamp());
        b.chest(-3, 0, 6, Direction.EAST, "temple");
        b.chest(3, 6, 4, Direction.WEST, "temple");
    }

    // ============================================================================================ abandoned sanctuary

    /** A ruined two-storey sanctuary high in the mountains, overgrown and cold. The Soul Stone waits on its roof. */
    private static void sanctuary(Build b) {
        BlockState br = bricks(), cr = cracked();
        b.foundation(-6, -6, 6, 6, br);
        b.clear(-6, 0, -6, 6, 12, 6);
        b.ruin(-6, -1, -6, 6, -1, 6, floor(), cr, 0.3f, 0f);
        b.ruin(-5, 0, -5, 5, 4, -5, br, cr, 0.35f, 0.12f);
        b.ruin(-5, 0, 5, 5, 4, 5, br, cr, 0.35f, 0.12f);
        b.ruin(-5, 0, -5, -5, 4, 5, br, cr, 0.35f, 0.12f);
        b.ruin(5, 0, -5, 5, 4, 5, br, cr, 0.35f, 0.12f);
        b.ruin(-5, 5, -5, 5, 5, 5, floor(), cr, 0.3f, 0.2f);
        b.ruin(-4, 6, -4, 4, 8, -4, br, cr, 0.4f, 0.3f);
        b.ruin(-4, 6, 4, 4, 8, 4, br, cr, 0.4f, 0.3f);
        b.clear(-1, 0, -5, 1, 2, -5);
        // overgrowth and decay
        for (int i = 0; i < 40; i++) {
            int x = b.random.nextInt(11) - 5, z = b.random.nextInt(11) - 5, yy = b.random.nextInt(9);
            if (b.get(x, yy, z).isAir()) b.set(x, yy, z, b.random.nextBoolean() ? s(Blocks.COBWEB) : s(Blocks.MOSS_CARPET));
        }
        for (int i = 0; i < 12; i++) b.set(b.random.nextInt(9) - 4, 0, b.random.nextInt(9) - 4, s(Blocks.SOUL_SOIL));
        b.fill(-4, 0, 4, 4, 2, 4, shelf());
        b.chest(3, 0, -3, Direction.WEST, "sanctuary");
        b.set(-3, 1, 3, lamp());
        // the roof shrine: a ring of soul fire around the stone
        b.disc(0, 5, 0, 2.5, gilded());
        for (int[] c : new int[][] {{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
            b.set(c[0], 6, c[1], s(Blocks.SOUL_SOIL));
            b.set(c[0], 7, c[1], s(Blocks.SOUL_FIRE));
        }
        b.pedestal(0, 6, 0, new ItemStack(MaItems.SOUL_STONE.get()), true);
    }

    // ============================================================================================ dimensional rift

    /** A crater where reality is torn; shattered terrain hangs above it. The Reality Stone pulses at the bottom. */
    private static void rift(Build b) {
        BlockState ruin = MaBlocks.COSMIC_RUIN_STONE.get().defaultBlockState();
        BlockState crystal = MaBlocks.REALITY_CRYSTAL.get().defaultBlockState();
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 9) continue;
                int depth = (int) Math.round(4 * (1 - d / 9));
                b.clear(x, -depth, z, x, 8, z);
                b.set(x, -depth - 1, z, b.random.nextInt(5) == 0 ? crystal : ruin);
            }
        }
        // floating shards of broken ground
        for (int i = 0; i < 7; i++) {
            int cx = b.random.nextInt(13) - 6, cz = b.random.nextInt(13) - 6, cy = 8 + b.random.nextInt(8);
            int size = 1 + b.random.nextInt(2);
            for (int x = -size; x <= size; x++)
                for (int z = -size; z <= size; z++)
                    for (int yy = -size; yy <= 0; yy++)
                        if (x * x + z * z + yy * yy * 2 <= size * size + 1) b.set(cx + x, cy + yy, cz + z, yy == 0 ? s(Blocks.GRASS_BLOCK) : (b.random.nextInt(4) == 0 ? crystal : ruin));
        }
        for (int yy = -3; yy <= 0; yy++) b.set(0, yy, 3, MaBlocks.RIFT_FISSURE.get().defaultBlockState());
        b.set(0, 1, 3, MaBlocks.RIFT_FISSURE.get().defaultBlockState());
        b.pedestal(0, -4, -1, new ItemStack(MaItems.REALITY_STONE.get()), true);
        b.chest(3, -3, -2, Direction.WEST, "rift");
    }

    // ============================================================================================ cosmic ruins

    /** A broken ring of obelisks around a collapsed observatory. Space Stone in the End, Power Stone in the Nether. */
    private static void ruins(Build b, net.minecraft.resources.ResourceKey<Level> dim) {
        BlockState br = MaBlocks.COSMIC_RUIN_BRICKS.get().defaultBlockState();
        BlockState st = MaBlocks.COSMIC_RUIN_STONE.get().defaultBlockState();
        b.foundation(-10, -10, 10, 10, st);
        b.clear(-10, 0, -10, 10, 14, 10);
        b.ruin(-10, -1, -10, 10, -1, 10, br, st, 0.4f, 0f);
        b.disc(0, -1, 0, 4, gilded());
        b.ring(0, -1, 0, 4, s(Blocks.CRYING_OBSIDIAN));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 8), z = (int) Math.round(Math.sin(a) * 8);
            int height = 3 + b.random.nextInt(6);
            b.ruin(x, 0, z, x, height, z, br, st, 0.3f, 0.05f);
            if (b.random.nextBoolean()) b.set(x, height + 1, z, MaBlocks.REALITY_CRYSTAL.get().defaultBlockState());
        }
        // collapsed dome: arcs of brick
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + 0.4;
            for (int t = 0; t < 8; t++) {
                double r = 6 - t * 0.6;
                int yy = (int) (Math.sin(t / 8.0 * Math.PI * 0.5) * 7);
                if (b.random.nextInt(4) != 0) b.set((int) Math.round(Math.cos(a) * r), yy, (int) Math.round(Math.sin(a) * r), br);
            }
        }
        ItemStack stone = dim == Level.END ? new ItemStack(MaItems.SPACE_STONE.get()) : dim == Level.NETHER ? new ItemStack(MaItems.POWER_STONE.get())
                : new ItemStack(MaItems.COSMIC_SHARD.get(), 3);
        b.pedestal(0, 0, 0, stone, dim == Level.END || dim == Level.NETHER);
        b.chest(-2, 0, 3, Direction.SOUTH, "ruins");
    }

    // ============================================================================================ ancient library

    /** A long vaulted hall of shelves under a mandala skylight. The Mind Stone glows at the far end. */
    private static void library(Build b) {
        b.foundation(-6, -9, 6, 9, bricks());
        b.clear(-6, 0, -9, 6, 12, 9);
        b.fill(-6, -1, -9, 6, -1, 9, floor());
        b.walls(-6, 0, -9, 6, 8, 9, bricks());
        for (int z = -9; z <= 9; z += 3) {
            b.fill(-6, 0, z, -6, 8, z, carved());
            b.fill(6, 0, z, 6, 8, z, carved());
        }
        // vaulted roof with a mandala skylight
        for (int x = -6; x <= 6; x++) {
            int rise = 3 - Math.abs(x) / 2;
            b.fill(x, 9, -9, x, 9 + rise, -9, bricks());
            b.fill(x, 9, 9, x, 9 + rise, 9, bricks());
            b.fill(x, 9 + rise, -9, x, 9 + rise, 9, Math.abs(x) <= 1 ? mandala() : planks());
        }
        // aisles of shelves with reading desks
        for (int z = -6; z <= 6; z += 4) {
            b.fill(-5, 0, z, -2, 4, z, shelf());
            b.fill(2, 0, z, 5, 4, z, shelf());
            b.set(-3, 0, z + 2, s(Blocks.LECTERN));
            b.set(3, 0, z + 2, s(Blocks.LECTERN));
        }
        for (int z = -7; z <= 7; z += 5) b.set(0, 7, z, lamp());
        b.clear(-1, 0, -9, 1, 3, -9);
        b.fill(-2, 4, -9, 2, 4, -9, gilded());
        b.disc(0, -1, 7, 1.6, gilded());
        b.pedestal(0, 0, 7, new ItemStack(MaItems.MIND_STONE.get()), true);
        b.chest(-5, 0, 8, Direction.EAST, "library");
        b.chest(5, 0, -8, Direction.WEST, "library");
    }
}

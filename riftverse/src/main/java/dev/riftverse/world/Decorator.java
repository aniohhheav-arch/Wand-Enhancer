package dev.riftverse.world;

import dev.riftverse.Riftverse;
import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftType;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.MegaKind;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.util.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/** Small-scale features layered on top of terrain: the details that give each reality its texture. */
public final class Decorator {
    public static final ResourceKey<LootTable> RIFT_CACHE = ResourceKey.create(Registries.LOOT_TABLE, Riftverse.id("chests/rift_cache"));

    private Decorator() {}

    public static void decorate(WorldGenLevel level, ChunkAccess chunk, UniverseSpec spec) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        RandomSource random = RandomSource.create(Hash.of(spec.seed, chunk.getPos().x, chunk.getPos().z, 0xDEC0L));
        MaterialSet m = MaterialSet.of(spec.materials);
        TerrainSampler sampler = TerrainSampler.of(spec);
        TerrainSampler.Column col = new TerrainSampler.Column();
        sampler.sample(minX + 8, minZ + 8, col);
        if (col.frayed) return;

        int attempts = Math.max(1, Math.round(7 * spec.decorDensity));
        for (int i = 0; i < attempts; i++) {
            int x = minX + 1 + random.nextInt(14);
            int z = minZ + 1 + random.nextInt(14);
            BlockPos pos = surface(level, sampler, col, x, z);
            if (pos == null) continue;
            feature(level, spec, m, random, pos);
        }

        if (random.nextInt(42) == 0) {
            BlockPos pos = surface(level, sampler, col, minX + 4 + random.nextInt(8), minZ + 4 + random.nextInt(8));
            if (pos != null) naturalRift(level, random, pos.above(1 + random.nextInt(2)), riftFor(spec, random), m);
        }
        if (random.nextInt(70) == 0) {
            BlockPos pos = surface(level, sampler, col, minX + 4 + random.nextInt(8), minZ + 4 + random.nextInt(8));
            if (pos != null) cache(level, random, pos, m);
        }
        if (spec.naturalBlackHoles && random.nextInt(320) == 0) {
            BlockPos pos = surface(level, sampler, col, minX + 8, minZ + 8);
            int y = pos == null ? 110 + random.nextInt(40) : pos.getY() + 45 + random.nextInt(50);
            BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), level.getLevel());
            hole.moveTo(minX + 8.5, Math.min(300, y), minZ + 8.5, 0, 0);
            hole.setHorizonRadius(2.5f + random.nextFloat() * 3.0f);
            hole.setNatural(true);
            level.addFreshEntity(hole);
        }

        for (Megastructures.Placement p : Megastructures.touching(spec, sampler, minX, minZ, minX + 15, minZ + 15)) {
            int[] heart = p.heart();
            if (heart[0] < minX || heart[0] > minX + 15 || heart[2] < minZ || heart[2] > minZ + 15) continue;
            BlockPos at = new BlockPos(heart[0], heart[1], heart[2]);
            if (p.kind == MegaKind.HALO_RING) {
                naturalRift(level, random, at, random.nextInt(4) == 0 ? RiftType.NEXUS : RiftType.STELLAR, null);
            } else if (p.kind == MegaKind.ZIGGURAT) {
                setChest(level, random, at.above());
                naturalRift(level, random, at.above(4), riftFor(spec, random), null);
            } else if (p.kind == MegaKind.COLOSSAL_PILLARS || p.kind == MegaKind.MONOLITH) {
                naturalRift(level, random, at.above(2), p.kind == MegaKind.MONOLITH ? RiftType.VOID : riftFor(spec, random), null);
            } else if (p.kind == MegaKind.NEON_MEGATOWER) {
                setChest(level, random, at);
            }
        }
    }

    /** First open block above the walkable surface in this column (ignores ceilings), or null. */
    private static BlockPos surface(WorldGenLevel level, TerrainSampler sampler, TerrainSampler.Column col, int x, int z) {
        int y;
        if (sampler.spec.terrain == TerrainMode.INVERTED) {
            sampler.sample(x, z, col);
            int start = col.ceilingBottom == TerrainSampler.NONE ? TerrainSampler.MAX_Y - 40 : col.ceilingBottom - 2;
            y = TerrainSampler.NONE;
            for (int yy = start; yy > level.getMinBuildHeight() + 2; yy--) {
                BlockState s = level.getBlockState(new BlockPos(x, yy, z));
                if (!s.isAir()) {
                    y = yy + 1;
                    break;
                }
            }
            if (y == TerrainSampler.NONE) return null;
        } else {
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        }
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 24) return null;
        return new BlockPos(x, y, z);
    }

    private static boolean solidBelow(WorldGenLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return !below.isAir() && below.getFluidState().isEmpty() && level.getBlockState(pos).isAir();
    }

    private static boolean underwater(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER);
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    private static void setIfAir(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, 2);
    }

    private static void column(WorldGenLevel level, BlockPos base, int height, BlockState state) {
        for (int i = 0; i < height; i++) setIfAir(level, base.above(i), state);
    }

    private static void blob(WorldGenLevel level, BlockPos c, float rx, float ry, float rz, BlockState state, boolean onlyAir) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        for (int x = -ix; x <= ix; x++) {
            for (int y = -iy; y <= iy; y++) {
                for (int z = -iz; z <= iz; z++) {
                    float e = (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz);
                    if (e > 1f) continue;
                    BlockPos p = c.offset(x, y, z);
                    if (onlyAir) setIfAir(level, p, state);
                    else set(level, p, state);
                }
            }
        }
    }

    private static void spike(WorldGenLevel level, RandomSource r, BlockPos base, int height, float radius, BlockState body, BlockState tip) {
        float tx = (r.nextFloat() - 0.5f) * 0.5f;
        float tz = (r.nextFloat() - 0.5f) * 0.5f;
        for (int y = 0; y < height; y++) {
            float rad = radius * (1f - (float) y / height);
            int ox = Math.round(tx * y);
            int oz = Math.round(tz * y);
            int ir = (int) Math.ceil(rad);
            for (int x = -ir; x <= ir; x++) {
                for (int z = -ir; z <= ir; z++) {
                    if (x * x + z * z > rad * rad + 0.5f) continue;
                    set(level, base.offset(x + ox, y, z + oz), y >= height - 2 ? tip : body);
                }
            }
        }
    }

    private static void smallTree(WorldGenLevel level, RandomSource r, BlockPos base, BlockState log, BlockState leaves, BlockState glow) {
        int h = 4 + r.nextInt(6);
        for (int i = 0; i < h; i++) set(level, base.above(i), log);
        float rad = 2.2f + r.nextFloat() * 1.6f;
        blob(level, base.above(h), rad, rad * 0.75f, rad, leaves, true);
        if (glow != null) {
            for (int i = 0; i < 4; i++) {
                BlockPos p = base.above(h - 1).offset(r.nextInt(5) - 2, r.nextInt(3) - 1, r.nextInt(5) - 2);
                if (level.getBlockState(p).equals(leaves)) set(level, p, glow);
            }
        }
    }

    private static void scatter(WorldGenLevel level, RandomSource r, BlockPos center, int count, int radius, BlockState plant) {
        for (int i = 0; i < count; i++) {
            BlockPos p = center.offset(r.nextInt(radius * 2 + 1) - radius, 0, r.nextInt(radius * 2 + 1) - radius);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos q = p.above(dy);
                if (solidBelow(level, q) && plant.canSurvive(level, q)) {
                    set(level, q, plant);
                    break;
                }
            }
        }
    }

    private static void feature(WorldGenLevel level, UniverseSpec spec, MaterialSet m, RandomSource r, BlockPos pos) {
        boolean wet = underwater(level, pos);
        if (!wet && !solidBelow(level, pos)) return;
        Archetype a = spec.materials;
        int roll = r.nextInt(10);
        switch (a) {
            case NEON_SPRAWL -> {
                if (roll < 5) {
                    int h = 6 + r.nextInt(4);
                    column(level, pos, h, m.structure);
                    set(level, pos.above(h), m.accent);
                    set(level, pos.above(h).east(), m.accent);
                } else if (roll < 8) {
                    int h = 4 + r.nextInt(6);
                    column(level, pos, h, m.structure);
                    BlockState panel = r.nextBoolean() ? m.accent : m.accent2;
                    for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) set(level, pos.above(h + dy).offset(dx, 0, 0), panel);
                } else {
                    blob(level, pos, 1.5f, 1f, 1.5f, m.structure2, true);
                }
            }
            case XENOFLORA -> {
                if (roll < 4) scatter(level, r, pos, 10, 3, RvBlocks.GLOWCAP.get().defaultBlockState());
                else if (roll < 8) smallTree(level, r, pos, m.log, m.leaves, m.accent);
                else {
                    column(level, pos, 2 + r.nextInt(3), RvBlocks.ALIEN_SOIL.get().defaultBlockState());
                    set(level, pos.above(4), m.accent);
                }
            }
            case SKYSHATTER, INVERTED -> {
                if (roll < 4 && !wet) smallTree(level, r, pos, m.log, m.leaves, null);
                else if (roll < 7 && m.hasPlant()) scatter(level, r, pos, 12, 3, m.plant);
                else if (roll < 9) blob(level, pos.above(8 + r.nextInt(18)), 3 + r.nextFloat() * 3, 1.5f, 3 + r.nextFloat() * 3,
                        a == Archetype.INVERTED ? m.stone : RvBlocks.DREAM_CLOUD.get().defaultBlockState(), true);
                else if (a == Archetype.INVERTED) {
                    set(level, pos.below(), RvBlocks.GRAVITY_LIFT.get().defaultBlockState());
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) set(level, pos.below().offset(dx, 0, dz), m.accent);
                }
            }
            case THALASSIC -> {
                if (wet) {
                    if (roll < 5) {
                        Block[] corals = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
                        blob(level, pos, 1.5f + r.nextFloat() * 2, 1.2f + r.nextFloat(), 1.5f + r.nextFloat() * 2, corals[r.nextInt(corals.length)].defaultBlockState(), false);
                    } else {
                        int h = 3 + r.nextInt(8);
                        for (int i = 0; i < h; i++) set(level, pos.above(i), m.structure);
                        set(level, pos.above(h), m.accent);
                    }
                } else if (roll < 6) {
                    int h = 6 + r.nextInt(4);
                    int dx = r.nextInt(3) - 1;
                    for (int i = 0; i < h; i++) set(level, pos.offset(i > h / 2 ? dx : 0, i, 0), m.log);
                    BlockPos top = pos.offset(dx, h, 0);
                    for (int d = 1; d <= 3; d++) {
                        setIfAir(level, top.north(d), m.leaves);
                        setIfAir(level, top.south(d), m.leaves);
                        setIfAir(level, top.east(d), m.leaves);
                        setIfAir(level, top.west(d), m.leaves);
                    }
                    setIfAir(level, top, m.leaves);
                }
            }
            case PRISMATIC -> {
                if (roll < 6) spike(level, r, pos.below(), 4 + r.nextInt(8), 1.2f + r.nextFloat() * 1.3f, m.accent, m.accent2);
                else scatter(level, r, pos, 6, 3, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
            case ASHEN -> {
                if (roll < 5) blob(level, pos, 1.5f + r.nextFloat() * 2, 1.2f + r.nextFloat() * 1.5f, 1.5f + r.nextFloat() * 2, m.sub, false);
                else if (roll < 8) {
                    BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
                    int len = 3 + r.nextInt(4);
                    for (int i = 0; i < len; i++) set(level, pos.offset(i, (int) (Math.sin(i / (double) len * Math.PI) * 3), 0), bone);
                } else blob(level, pos.below(), 2.5f, 1f, 2.5f, m.structure, false);
            }
            case ASTRAL -> {
                if (roll < 6) spike(level, r, pos.below(), 3 + r.nextInt(7), 1.0f + r.nextFloat(), m.stone, m.accent);
                else blob(level, pos.below(), 3f, 1f, 3f, RvBlocks.STARDUST_SAND.get().defaultBlockState(), false);
            }
            case CORRUPTED -> {
                if (roll < 6) {
                    int h = 3 + r.nextInt(10);
                    BlockState[] mats = {m.accent, m.surface, m.accent2, m.structure2};
                    for (int i = 0; i < h; i++) if (r.nextInt(5) != 0) set(level, pos.above(i), mats[r.nextInt(mats.length)]);
                } else {
                    BlockPos c = pos.above(4 + r.nextInt(9)).offset(r.nextInt(3) - 1, 0, r.nextInt(3) - 1);
                    for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++) set(level, c.offset(dx, dy, dz), m.accent);
                }
            }
            case ELDER -> {
                if (roll < 4) {
                    int h = 2 + r.nextInt(6);
                    for (int i = 0; i < h; i++) set(level, pos.above(i), i == h / 2 ? m.accent : m.structure);
                    if (r.nextBoolean()) set(level, pos.above(h), m.accent2);
                } else if (roll < 7) {
                    boolean alongX = r.nextBoolean();
                    int len = 5 + r.nextInt(5);
                    for (int i = 0; i < len; i++) {
                        int h = 1 + r.nextInt(4);
                        BlockPos b = alongX ? pos.east(i) : pos.south(i);
                        for (int y = 0; y < h; y++) setIfAir(level, b.above(y), y == 0 && r.nextInt(6) == 0 ? m.accent : m.structure);
                    }
                } else if (m.hasPlant()) scatter(level, r, pos, 10, 3, m.plant);
            }
            case HOLLOW -> {
                if (roll < 6) spike(level, r, pos.below(), 3 + r.nextInt(9), 1.0f + r.nextFloat() * 1.2f, m.structure, m.accent);
                else blob(level, pos.below(), 2.5f, 1f, 2.5f, m.accent2, false);
            }
            case SOMNIUM -> {
                if (roll < 4) {
                    int h = 3 + r.nextInt(5);
                    column(level, pos, h, Blocks.WHITE_CONCRETE.defaultBlockState());
                    Block[] candy = {Blocks.PINK_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIME_WOOL};
                    float rad = 2f + r.nextFloat() * 1.5f;
                    blob(level, pos.above(h + 1), rad, rad, rad, candy[r.nextInt(candy.length)].defaultBlockState(), true);
                } else if (roll < 7) scatter(level, r, pos, 8, 3, RvBlocks.GLOWCAP.get().defaultBlockState());
                else blob(level, pos.above(10 + r.nextInt(20)), 3 + r.nextFloat() * 4, 1.6f, 3 + r.nextFloat() * 4, m.accent, true);
            }
            case CINDER -> {
                if (roll < 6) {
                    int h = 3 + r.nextInt(10);
                    column(level, pos, h, Blocks.BASALT.defaultBlockState());
                    if (r.nextInt(3) == 0) set(level, pos.above(h), m.accent2);
                } else blob(level, pos.below(), 2.2f, 1f, 2.2f, m.accent, false);
            }
            case RIME -> {
                if (roll < 5) spike(level, r, pos.below(), 4 + r.nextInt(11), 1.3f + r.nextFloat() * 1.4f, m.structure, m.accent);
                else if (roll < 8) {
                    for (int i = 0; i < 12; i++) {
                        BlockPos p = pos.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3);
                        if (solidBelow(level, p)) set(level, p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + r.nextInt(3)));
                    }
                } else blob(level, pos, 1.6f, 1.2f, 1.6f, m.accent, false);
            }
        }
    }

    public static RiftType riftFor(UniverseSpec spec, RandomSource r) {
        if (r.nextInt(9) == 0) return RiftType.NEXUS;
        if (r.nextInt(3) == 0) return RiftType.AZURE;
        return switch (spec.archetype) {
            case NEON_SPRAWL, CORRUPTED -> r.nextBoolean() ? RiftType.GLITCH : RiftType.CRIMSON;
            case XENOFLORA, SKYSHATTER, THALASSIC, SOMNIUM -> RiftType.VERDANT;
            case PRISMATIC, RIME -> RiftType.PRISMATIC;
            case ASHEN, CINDER -> RiftType.CRIMSON;
            case ASTRAL, INVERTED -> RiftType.STELLAR;
            case HOLLOW -> RiftType.VOID;
            case ELDER -> r.nextBoolean() ? RiftType.PRISMATIC : RiftType.VERDANT;
            case SUNSCAR, RADIANCE -> RiftType.SOLAR;
            case CORAL, MIRE -> RiftType.ABYSSAL;
            case MYCELIA, BLOOM -> RiftType.FUNGAL;
            case SANGUINE -> RiftType.SANGUINE;
            case CLOCKWORK, FERROUS -> RiftType.BRASS;
            case CONFECTION -> RiftType.SACCHARINE;
            case TEMPEST -> RiftType.TEMPEST;
            case OBSIDIAN -> RiftType.UMBRAL;
            case VERDIGRIS, MESA -> RiftType.PATINA;
            case AURORA, MIRROR -> RiftType.AURORAL;
            case MAGMA, WASTELAND -> RiftType.MOLTEN;
            case PRIMEVAL, SAVANNA, HIVE -> RiftType.PRIMAL;
            case CHROME -> RiftType.CHROME;
            case DEEPDARK, GEODE -> RiftType.SCULK;
            case NEBULA, LUNAR -> RiftType.NEBULAR;
            case STARFORGE, MOLTENSEA, EMBERSTEPPE -> RiftType.FORGE;
            case FROSTGLASS, CRYSTALOCEAN, DUSKHIGHLANDS, CORALKING -> RiftType.FROST;
            case ECHO, DROWNED, TOXIC, VOIDGLASS, RUNIC -> RiftType.RUNIC;
            case CLOUDKINGDOM, GOLDENTEMPLE, RAINBOW, PASTEL, CELESTIAL, LUNARCOLONY -> RiftType.CELESTIAL;
            case CRIMSONWEALD, WARPEDWEALD, PETRIFIED, NEONJUNGLE -> RiftType.WEALD;
        };
    }

    public static void naturalRift(WorldGenLevel level, RandomSource r, BlockPos pos, RiftType type, MaterialSet scar) {
        if (pos.getY() >= level.getMaxBuildHeight() - 2) return;
        set(level, pos, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, type));
        if (scar == null) return;
        BlockState crystal = RvBlocks.RIFT_CRYSTAL.get().defaultBlockState();
        for (int i = 0; i < 5; i++) {
            BlockPos p = pos.offset(r.nextInt(9) - 4, -3, r.nextInt(9) - 4);
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos q = p.above(dy);
                if (solidBelow(level, q)) {
                    int h = 1 + r.nextInt(3);
                    for (int k = 0; k < h; k++) setIfAir(level, q.above(k), crystal);
                    break;
                }
            }
        }
    }

    public static void cache(WorldGenLevel level, RandomSource r, BlockPos pos, MaterialSet m) {
        if (!solidBelow(level, pos)) return;
        setChest(level, r, pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                set(level, pos.below().offset(dx, 0, dz), (dx + dz) % 2 == 0 ? m.accent : m.structure);
            }
        }
    }

    private static void setChest(WorldGenLevel level, RandomSource r, BlockPos pos) {
        set(level, pos, Blocks.CHEST.defaultBlockState());
        RandomizableContainer.setBlockEntityLootTable(level, r, pos, RIFT_CACHE);
    }

    /** Lets callers check a column against a list of landmarks (e.g. to avoid placing on top of one). */}

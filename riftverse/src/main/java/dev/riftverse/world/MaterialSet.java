package dev.riftverse.world;

import dev.riftverse.registry.RvBlocks;
import dev.riftverse.universe.Archetype;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The block palette a reality is built from. */
public final class MaterialSet {
    private static volatile Map<Archetype, MaterialSet> sets;

    public final BlockState surface;
    public final BlockState sub;
    public final BlockState stone;
    public final BlockState liquid;
    public final BlockState accent;
    public final BlockState accent2;
    public final BlockState structure;
    public final BlockState structure2;
    public final BlockState glass;
    public final BlockState plant;
    public final BlockState log;
    public final BlockState leaves;
    public final boolean frozenSea;

    private MaterialSet(Block surface, Block sub, Block stone, Block liquid, Block accent, Block accent2, Block structure, Block structure2,
                        Block glass, Block plant, Block log, BlockState leaves, boolean frozenSea) {
        this.surface = surface.defaultBlockState();
        this.sub = sub.defaultBlockState();
        this.stone = stone.defaultBlockState();
        this.liquid = liquid.defaultBlockState();
        this.accent = accent.defaultBlockState();
        this.accent2 = accent2.defaultBlockState();
        this.structure = structure.defaultBlockState();
        this.structure2 = structure2.defaultBlockState();
        this.glass = glass.defaultBlockState();
        this.plant = plant.defaultBlockState();
        this.log = log.defaultBlockState();
        this.leaves = leaves;
        this.frozenSea = frozenSea;
    }

    public static MaterialSet of(Archetype archetype) {
        Map<Archetype, MaterialSet> m = sets;
        if (m == null) {
            synchronized (MaterialSet.class) {
                if (sets == null) sets = build();
                m = sets;
            }
        }
        return m.get(archetype);
    }

    private static BlockState persistent(Block leaves) {
        BlockState s = leaves.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    private static Map<Archetype, MaterialSet> build() {
        Map<Archetype, MaterialSet> m = new EnumMap<>(Archetype.class);
        m.put(Archetype.NEON_SPRAWL, new MaterialSet(RvBlocks.CYBER_PLATING.get(), RvBlocks.CYBER_PLATING.get(), Blocks.DEEPSLATE, Blocks.WATER,
                RvBlocks.NEON_PANEL_CYAN.get(), RvBlocks.NEON_PANEL_MAGENTA.get(), RvBlocks.CYBER_PLATING.get(), Blocks.GRAY_CONCRETE,
                RvBlocks.CYBER_GLASS.get(), Blocks.AIR, RvBlocks.CYBER_PLATING.get(), RvBlocks.NEON_PANEL_CYAN.get().defaultBlockState(), false));
        m.put(Archetype.XENOFLORA, new MaterialSet(RvBlocks.ALIEN_MOSS.get(), RvBlocks.ALIEN_SOIL.get(), Blocks.TUFF, Blocks.WATER,
                Blocks.VERDANT_FROGLIGHT, RvBlocks.LUMEN_LEAVES.get(), RvBlocks.LUMEN_LOG.get(), RvBlocks.ALIEN_SOIL.get(),
                Blocks.GLASS, RvBlocks.GLOWCAP.get(), RvBlocks.LUMEN_LOG.get(), RvBlocks.LUMEN_LEAVES.get().defaultBlockState(), false));
        m.put(Archetype.SKYSHATTER, new MaterialSet(Blocks.GRASS_BLOCK, Blocks.DIRT, RvBlocks.SKYSTONE.get(), Blocks.WATER,
                RvBlocks.ABYSSAL_GLOW.get(), Blocks.AMETHYST_BLOCK, RvBlocks.SKYSTONE.get(), Blocks.QUARTZ_BLOCK,
                Blocks.GLASS, Blocks.SHORT_GRASS, Blocks.BIRCH_LOG, persistent(Blocks.BIRCH_LEAVES), false));
        m.put(Archetype.THALASSIC, new MaterialSet(Blocks.SAND, Blocks.SAND, Blocks.PRISMARINE, Blocks.WATER,
                RvBlocks.ABYSSAL_GLOW.get(), Blocks.SEA_LANTERN, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                Blocks.GLASS, Blocks.AIR, Blocks.JUNGLE_LOG, persistent(Blocks.JUNGLE_LEAVES), false));
        m.put(Archetype.PRISMATIC, new MaterialSet(Blocks.CALCITE, Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.WATER,
                RvBlocks.RIFT_CRYSTAL.get(), Blocks.AMETHYST_BLOCK, RvBlocks.RIFT_CRYSTAL.get(), RvBlocks.VOID_CRYSTAL.get(),
                RvBlocks.CYBER_GLASS.get(), Blocks.AIR, Blocks.CALCITE, Blocks.AMETHYST_BLOCK.defaultBlockState(), false));
        m.put(Archetype.ASHEN, new MaterialSet(RvBlocks.DEAD_REGOLITH.get(), RvBlocks.ASHEN_ROCK.get(), RvBlocks.ASHEN_ROCK.get(), Blocks.AIR,
                Blocks.BONE_BLOCK, RvBlocks.VOID_CRYSTAL.get(), Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.BONE_BLOCK, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.ASTRAL, new MaterialSet(RvBlocks.STARDUST_SAND.get(), RvBlocks.COSMIC_OBSIDIAN.get(), RvBlocks.COSMIC_OBSIDIAN.get(), Blocks.AIR,
                RvBlocks.VOID_CRYSTAL.get(), RvBlocks.RIFT_CRYSTAL.get(), RvBlocks.NEXUS_BRICKS.get(), RvBlocks.COSMIC_OBSIDIAN.get(),
                RvBlocks.CYBER_GLASS.get(), Blocks.AIR, RvBlocks.COSMIC_OBSIDIAN.get(), RvBlocks.NEXUS_GLOW.get().defaultBlockState(), false));
        m.put(Archetype.CORRUPTED, new MaterialSet(Blocks.BLACK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.DEEPSLATE, Blocks.AIR,
                RvBlocks.GLITCH_BLOCK.get(), Blocks.LIME_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.MAGENTA_CONCRETE,
                RvBlocks.CYBER_GLASS.get(), Blocks.AIR, RvBlocks.GLITCH_BLOCK.get(), Blocks.LIME_STAINED_GLASS.defaultBlockState(), false));
        m.put(Archetype.ELDER, new MaterialSet(Blocks.MOSS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.WATER,
                RvBlocks.ANCIENT_GLYPH.get(), RvBlocks.ANCIENT_GOLD.get(), RvBlocks.ANCIENT_BRICKS.get(), Blocks.SMOOTH_SANDSTONE,
                Blocks.GLASS, Blocks.SHORT_GRASS, Blocks.DARK_OAK_LOG, persistent(Blocks.AZALEA_LEAVES), false));
        m.put(Archetype.INVERTED, new MaterialSet(Blocks.MOSS_BLOCK, RvBlocks.SKYSTONE.get(), RvBlocks.SKYSTONE.get(), Blocks.WATER,
                RvBlocks.ABYSSAL_GLOW.get(), Blocks.AMETHYST_BLOCK, RvBlocks.SKYSTONE.get(), Blocks.QUARTZ_BLOCK,
                RvBlocks.CYBER_GLASS.get(), Blocks.SHORT_GRASS, Blocks.WARPED_STEM, Blocks.WARPED_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.HOLLOW, new MaterialSet(RvBlocks.VOID_STONE.get(), RvBlocks.VOID_STONE.get(), RvBlocks.VOID_STONE.get(), Blocks.AIR,
                RvBlocks.VOID_CRYSTAL.get(), Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, RvBlocks.VOID_STONE.get(),
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.OBSIDIAN, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.SOMNIUM, new MaterialSet(RvBlocks.DREAM_TURF.get(), Blocks.WHITE_TERRACOTTA, Blocks.CALCITE, Blocks.WATER,
                RvBlocks.DREAM_CLOUD.get(), Blocks.PEARLESCENT_FROGLIGHT, Blocks.PURPUR_BLOCK, Blocks.LIGHT_BLUE_CONCRETE,
                Blocks.PINK_STAINED_GLASS, RvBlocks.GLOWCAP.get(), Blocks.MUSHROOM_STEM, Blocks.PINK_WOOL.defaultBlockState(), false));
        m.put(Archetype.CINDER, new MaterialSet(Blocks.BLACKSTONE, Blocks.BASALT, Blocks.BASALT, Blocks.LAVA,
                Blocks.MAGMA_BLOCK, Blocks.SHROOMLIGHT, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE,
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.RIME, new MaterialSet(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.STONE, Blocks.WATER,
                Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.PACKED_ICE, Blocks.BLUE_ICE,
                Blocks.ICE, Blocks.AIR, Blocks.SPRUCE_LOG, persistent(Blocks.SPRUCE_LEAVES), true));
        m.put(Archetype.SUNSCAR, new MaterialSet(Blocks.SAND, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.WATER,
                Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE,
                Blocks.GLASS, Blocks.DEAD_BUSH, Blocks.SMOOTH_SANDSTONE, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.MESA, new MaterialSet(Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, Blocks.WATER,
                Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
                Blocks.TINTED_GLASS, Blocks.DEAD_BUSH, Blocks.ACACIA_LOG, persistent(Blocks.ACACIA_LEAVES), false));
        m.put(Archetype.CORAL, new MaterialSet(Blocks.SAND, Blocks.SAND, Blocks.PRISMARINE, Blocks.WATER,
                Blocks.TUBE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN,
                Blocks.GLASS, Blocks.AIR, Blocks.MANGROVE_LOG, persistent(Blocks.MANGROVE_LEAVES), false));
        m.put(Archetype.MYCELIA, new MaterialSet(Blocks.MYCELIUM, Blocks.DIRT, Blocks.TUFF, Blocks.WATER,
                RvBlocks.GLOWCAP.get(), Blocks.SHROOMLIGHT, Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK,
                Blocks.GLASS, RvBlocks.GLOWCAP.get(), Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), false));
        m.put(Archetype.CLOCKWORK, new MaterialSet(Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.DEEPSLATE, Blocks.WATER,
                Blocks.WAXED_COPPER_BLOCK, RvBlocks.ANCIENT_GOLD.get(), Blocks.CUT_COPPER, Blocks.POLISHED_DEEPSLATE,
                Blocks.GLASS, Blocks.AIR, Blocks.COPPER_BLOCK, Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), false));
        m.put(Archetype.SANGUINE, new MaterialSet(Blocks.RED_CONCRETE, Blocks.NETHERRACK, Blocks.DEEPSLATE, Blocks.LAVA,
                Blocks.CRIMSON_NYLIUM, Blocks.BONE_BLOCK, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS,
                Blocks.RED_STAINED_GLASS, Blocks.AIR, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.CONFECTION, new MaterialSet(Blocks.PINK_WOOL, Blocks.WHITE_WOOL, Blocks.WHITE_CONCRETE, Blocks.WATER,
                Blocks.PINK_CONCRETE, Blocks.PEARLESCENT_FROGLIGHT, Blocks.PURPUR_BLOCK, Blocks.LIGHT_BLUE_WOOL,
                Blocks.PINK_STAINED_GLASS, RvBlocks.GLOWCAP.get(), Blocks.MUSHROOM_STEM, Blocks.WHITE_WOOL.defaultBlockState(), false));
        m.put(Archetype.TEMPEST, new MaterialSet(Blocks.MOSS_BLOCK, Blocks.DIRT, RvBlocks.SKYSTONE.get(), Blocks.WATER,
                RvBlocks.ABYSSAL_GLOW.get(), Blocks.LIGHTNING_ROD, RvBlocks.SKYSTONE.get(), Blocks.QUARTZ_BLOCK,
                RvBlocks.CYBER_GLASS.get(), Blocks.SHORT_GRASS, Blocks.BIRCH_LOG, persistent(Blocks.BIRCH_LEAVES), false));
        m.put(Archetype.MIRE, new MaterialSet(Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.PACKED_MUD, Blocks.WATER,
                Blocks.SLIME_BLOCK, RvBlocks.GLOWCAP.get(), Blocks.MOSSY_COBBLESTONE, Blocks.MANGROVE_ROOTS,
                Blocks.SLIME_BLOCK, Blocks.SHORT_GRASS, Blocks.MANGROVE_LOG, persistent(Blocks.MANGROVE_LEAVES), false));
        m.put(Archetype.OBSIDIAN, new MaterialSet(Blocks.OBSIDIAN, Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.AIR,
                RvBlocks.VOID_CRYSTAL.get(), Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE, Blocks.OBSIDIAN,
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.BLACKSTONE, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.VERDIGRIS, new MaterialSet(Blocks.MOSS_BLOCK, Blocks.DIRT, Blocks.OXIDIZED_COPPER, Blocks.WATER,
                Blocks.OXIDIZED_CUT_COPPER, RvBlocks.ANCIENT_GLYPH.get(), RvBlocks.ANCIENT_BRICKS.get(), Blocks.MOSSY_STONE_BRICKS,
                Blocks.GLASS, Blocks.SHORT_GRASS, Blocks.OAK_LOG, persistent(Blocks.AZALEA_LEAVES), false));
        m.put(Archetype.RADIANCE, new MaterialSet(Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK, Blocks.WATER,
                RvBlocks.ANCIENT_GOLD.get(), Blocks.PEARLESCENT_FROGLIGHT, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ,
                Blocks.GLASS, Blocks.SHORT_GRASS, Blocks.BIRCH_LOG, persistent(Blocks.BIRCH_LEAVES), false));
        m.put(Archetype.FERROUS, new MaterialSet(RvBlocks.DEAD_REGOLITH.get(), Blocks.IRON_BLOCK, Blocks.DEEPSLATE, Blocks.AIR,
                Blocks.COPPER_BLOCK, Blocks.LANTERN, Blocks.IRON_BLOCK, Blocks.GRAY_CONCRETE,
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.IRON_BLOCK, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.BLOOM, new MaterialSet(Blocks.MOSS_BLOCK, Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.WATER,
                Blocks.AMETHYST_BLOCK, RvBlocks.RIFT_CRYSTAL.get(), RvBlocks.RIFT_CRYSTAL.get(), Blocks.CALCITE,
                Blocks.PINK_STAINED_GLASS, Blocks.PINK_PETALS, Blocks.CHERRY_LOG, persistent(Blocks.CHERRY_LEAVES), false));
        m.put(Archetype.AURORA, new MaterialSet(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.CALCITE, Blocks.WATER,
                Blocks.BLUE_ICE, Blocks.SEA_LANTERN, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK,
                Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.AIR, Blocks.SPRUCE_LOG, persistent(Blocks.SPRUCE_LEAVES), true));
        m.put(Archetype.MAGMA, new MaterialSet(Blocks.MAGMA_BLOCK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.LAVA,
                Blocks.SHROOMLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                Blocks.ORANGE_STAINED_GLASS, Blocks.AIR, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.PRIMEVAL, new MaterialSet(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MOSSY_COBBLESTONE, Blocks.WATER,
                Blocks.MOSS_BLOCK, Blocks.VERDANT_FROGLIGHT, Blocks.MOSSY_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
                Blocks.GREEN_STAINED_GLASS, Blocks.FERN, Blocks.JUNGLE_LOG, persistent(Blocks.JUNGLE_LEAVES), false));
        m.put(Archetype.NEBULA, new MaterialSet(RvBlocks.STARDUST_SAND.get(), RvBlocks.SKYSTONE.get(), Blocks.PURPUR_BLOCK, Blocks.AIR,
                Blocks.AMETHYST_BLOCK, Blocks.PEARLESCENT_FROGLIGHT, Blocks.PURPUR_PILLAR, RvBlocks.COSMIC_OBSIDIAN.get(),
                Blocks.MAGENTA_STAINED_GLASS, Blocks.AIR, Blocks.PURPUR_PILLAR, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.WASTELAND, new MaterialSet(Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.WATER,
                Blocks.RAW_IRON_BLOCK, Blocks.REDSTONE_LAMP, Blocks.CRACKED_STONE_BRICKS, Blocks.GRAY_CONCRETE,
                Blocks.TINTED_GLASS, Blocks.DEAD_BUSH, Blocks.STRIPPED_OAK_LOG, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.GEODE, new MaterialSet(Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT, Blocks.AIR,
                Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.CALCITE, Blocks.AMETHYST_BLOCK,
                Blocks.PURPLE_STAINED_GLASS, Blocks.AIR, Blocks.CALCITE, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.DEEPDARK, new MaterialSet(Blocks.SCULK, Blocks.DEEPSLATE, Blocks.DEEPSLATE, Blocks.AIR,
                Blocks.SCULK_CATALYST, Blocks.SOUL_LANTERN, Blocks.DEEPSLATE_TILES, Blocks.REINFORCED_DEEPSLATE,
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.DEEPSLATE_BRICKS, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.SAVANNA, new MaterialSet(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.WATER,
                Blocks.HAY_BLOCK, Blocks.LANTERN, Blocks.SMOOTH_SANDSTONE, Blocks.ORANGE_TERRACOTTA,
                Blocks.GLASS, Blocks.SHORT_GRASS, Blocks.ACACIA_LOG, persistent(Blocks.ACACIA_LEAVES), false));
        m.put(Archetype.CHROME, new MaterialSet(Blocks.WHITE_CONCRETE, Blocks.SMOOTH_STONE, Blocks.STONE, Blocks.WATER,
                Blocks.SEA_LANTERN, Blocks.END_ROD, Blocks.IRON_BLOCK, Blocks.QUARTZ_BLOCK,
                Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.AIR, Blocks.QUARTZ_PILLAR, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.LUNAR, new MaterialSet(RvBlocks.DEAD_REGOLITH.get(), Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.STONE, Blocks.AIR,
                Blocks.END_STONE, Blocks.SEA_LANTERN, Blocks.END_STONE_BRICKS, Blocks.SMOOTH_STONE,
                Blocks.GLASS, Blocks.AIR, Blocks.END_STONE_BRICKS, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.HIVE, new MaterialSet(Blocks.HONEYCOMB_BLOCK, Blocks.HONEY_BLOCK, Blocks.YELLOW_TERRACOTTA, Blocks.WATER,
                Blocks.BEEHIVE, Blocks.OCHRE_FROGLIGHT, Blocks.HONEYCOMB_BLOCK, Blocks.YELLOW_CONCRETE,
                Blocks.YELLOW_STAINED_GLASS, Blocks.SUNFLOWER, Blocks.OAK_LOG, persistent(Blocks.FLOWERING_AZALEA_LEAVES), false));
        m.put(Archetype.STARFORGE, new MaterialSet(RvBlocks.STARMETAL_BLOCK.get(), Blocks.IRON_BLOCK, Blocks.DEEPSLATE, Blocks.LAVA,
                RvBlocks.MAGMA_CRUST.get(), Blocks.SHROOMLIGHT, RvBlocks.STARMETAL_BLOCK.get(), RvBlocks.CHROME_PLATING.get(),
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.IRON_BLOCK, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.FROSTGLASS, new MaterialSet(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, RvBlocks.FROST_CRYSTAL.get(), Blocks.WATER,
                RvBlocks.FROST_CRYSTAL.get(), Blocks.SEA_LANTERN, Blocks.BLUE_ICE, Blocks.PACKED_ICE,
                Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.AIR, Blocks.PACKED_ICE, Blocks.AIR.defaultBlockState(), true));
        m.put(Archetype.ECHO, new MaterialSet(Blocks.SCULK, Blocks.DEEPSLATE, Blocks.DEEPSLATE, Blocks.AIR,
                RvBlocks.SCULK_CRYSTAL.get(), Blocks.SOUL_LANTERN, Blocks.DEEPSLATE_TILES, Blocks.SCULK_CATALYST,
                Blocks.TINTED_GLASS, Blocks.AIR, Blocks.DEEPSLATE_BRICKS, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.MOLTENSEA, new MaterialSet(Blocks.BASALT, Blocks.BLACKSTONE, RvBlocks.MAGMA_CRUST.get(), Blocks.LAVA,
                Blocks.MAGMA_BLOCK, Blocks.SHROOMLIGHT, RvBlocks.EMBER_BRICKS.get(), Blocks.POLISHED_BLACKSTONE,
                Blocks.ORANGE_STAINED_GLASS, Blocks.AIR, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.CLOUDKINGDOM, new MaterialSet(RvBlocks.DREAM_CLOUD.get(), RvBlocks.DREAM_CLOUD.get(), Blocks.CALCITE, Blocks.AIR,
                Blocks.QUARTZ_BLOCK, Blocks.PEARLESCENT_FROGLIGHT, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ,
                Blocks.WHITE_STAINED_GLASS, Blocks.AIR, Blocks.BIRCH_LOG, persistent(Blocks.BIRCH_LEAVES), false));
        m.put(Archetype.DROWNED, new MaterialSet(Blocks.SAND, Blocks.GRAVEL, Blocks.PRISMARINE, Blocks.WATER,
                Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, RvBlocks.ANCIENT_BRICKS.get(), Blocks.PRISMARINE_BRICKS,
                Blocks.GLASS, Blocks.AIR, Blocks.DARK_PRISMARINE, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.TOXIC, new MaterialSet(Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.PACKED_MUD, Blocks.WATER,
                Blocks.SLIME_BLOCK, Blocks.VERDANT_FROGLIGHT, Blocks.MOSSY_COBBLESTONE, Blocks.GREEN_CONCRETE,
                Blocks.LIME_STAINED_GLASS, Blocks.SHORT_GRASS, Blocks.MANGROVE_LOG, persistent(Blocks.MANGROVE_LEAVES), false));
        m.put(Archetype.CRIMSONWEALD, new MaterialSet(Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, Blocks.NETHERRACK, Blocks.LAVA,
                Blocks.SHROOMLIGHT, Blocks.NETHER_WART_BLOCK, Blocks.CRIMSON_PLANKS, Blocks.RED_NETHER_BRICKS,
                Blocks.RED_STAINED_GLASS, Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.WARPEDWEALD, new MaterialSet(Blocks.WARPED_NYLIUM, Blocks.NETHERRACK, Blocks.NETHERRACK, Blocks.AIR,
                Blocks.SHROOMLIGHT, Blocks.WARPED_WART_BLOCK, Blocks.WARPED_PLANKS, RvBlocks.SCULK_CRYSTAL.get(),
                Blocks.CYAN_STAINED_GLASS, Blocks.WARPED_ROOTS, Blocks.WARPED_STEM, Blocks.WARPED_WART_BLOCK.defaultBlockState(), false));
        m.put(Archetype.GOLDENTEMPLE, new MaterialSet(Blocks.SAND, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.WATER,
                RvBlocks.ANCIENT_GOLD.get(), RvBlocks.RUNE_TILE.get(), RvBlocks.ANCIENT_BRICKS.get(), RvBlocks.ANCIENT_GLYPH.get(),
                Blocks.YELLOW_STAINED_GLASS, Blocks.DEAD_BUSH, Blocks.SMOOTH_SANDSTONE, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.RAINBOW, new MaterialSet(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.WHITE_CONCRETE, Blocks.WATER,
                Blocks.RED_STAINED_GLASS, Blocks.PEARLESCENT_FROGLIGHT, Blocks.LIME_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE,
                Blocks.MAGENTA_STAINED_GLASS, Blocks.PINK_PETALS, Blocks.CHERRY_LOG, persistent(Blocks.CHERRY_LEAVES), false));
        m.put(Archetype.VOIDGLASS, new MaterialSet(RvBlocks.VOIDGLASS.get(), RvBlocks.VOID_STONE.get(), RvBlocks.VOID_STONE.get(), Blocks.AIR,
                RvBlocks.VOID_CRYSTAL.get(), RvBlocks.NEBULA_STONE.get(), RvBlocks.VOIDGLASS.get(), RvBlocks.COSMIC_OBSIDIAN.get(),
                RvBlocks.VOIDGLASS.get(), Blocks.AIR, RvBlocks.VOID_STONE.get(), Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.RUNIC, new MaterialSet(Blocks.STONE, Blocks.ANDESITE, RvBlocks.RUNE_TILE.get(), Blocks.WATER,
                RvBlocks.ANCIENT_GLYPH.get(), RvBlocks.RUNE_TILE.get(), RvBlocks.ANCIENT_BRICKS.get(), RvBlocks.RUNE_TILE.get(),
                Blocks.PURPLE_STAINED_GLASS, Blocks.AIR, Blocks.DARK_OAK_LOG, persistent(Blocks.DARK_OAK_LEAVES), false));
        m.put(Archetype.EMBERSTEPPE, new MaterialSet(Blocks.RED_SAND, Blocks.RED_SANDSTONE, RvBlocks.EMBER_BRICKS.get(), Blocks.LAVA,
                RvBlocks.MAGMA_CRUST.get(), Blocks.SHROOMLIGHT, RvBlocks.EMBER_BRICKS.get(), Blocks.CRACKED_NETHER_BRICKS,
                Blocks.ORANGE_STAINED_GLASS, Blocks.DEAD_BUSH, Blocks.CRIMSON_STEM, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.PASTEL, new MaterialSet(Blocks.PINK_WOOL, Blocks.WHITE_WOOL, Blocks.LIGHT_BLUE_CONCRETE, Blocks.WATER,
                Blocks.YELLOW_WOOL, Blocks.PEARLESCENT_FROGLIGHT, Blocks.LIGHT_BLUE_WOOL, Blocks.LIME_WOOL,
                Blocks.PINK_STAINED_GLASS, Blocks.PINK_PETALS, Blocks.CHERRY_LOG, persistent(Blocks.CHERRY_LEAVES), false));
        m.put(Archetype.PETRIFIED, new MaterialSet(Blocks.MOSS_BLOCK, Blocks.DIRT, RvBlocks.STARWOOD_PLANKS.get(), Blocks.WATER,
                Blocks.AMETHYST_BLOCK, RvBlocks.NEBULA_STONE.get(), RvBlocks.STARWOOD_PLANKS.get(), Blocks.CALCITE,
                Blocks.PURPLE_STAINED_GLASS, Blocks.FERN, RvBlocks.STARWOOD_PLANKS.get(), persistent(Blocks.AZALEA_LEAVES), false));
        m.put(Archetype.CRYSTALOCEAN, new MaterialSet(Blocks.SAND, RvBlocks.FROST_CRYSTAL.get(), Blocks.CALCITE, Blocks.WATER,
                RvBlocks.FROST_CRYSTAL.get(), Blocks.SEA_LANTERN, Blocks.QUARTZ_BRICKS, RvBlocks.AURORA_GLASS.get(),
                RvBlocks.AURORA_GLASS.get(), Blocks.AIR, Blocks.QUARTZ_PILLAR, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.DUSKHIGHLANDS, new MaterialSet(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.WATER,
                Blocks.AMETHYST_BLOCK, Blocks.LANTERN, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
                Blocks.ORANGE_STAINED_GLASS, Blocks.SHORT_GRASS, Blocks.SPRUCE_LOG, persistent(Blocks.SPRUCE_LEAVES), false));
        m.put(Archetype.NEONJUNGLE, new MaterialSet(RvBlocks.ALIEN_MOSS.get(), RvBlocks.ALIEN_SOIL.get(), Blocks.DEEPSLATE, Blocks.WATER,
                RvBlocks.NEON_PANEL_CYAN.get(), RvBlocks.NEON_PANEL_MAGENTA.get(), RvBlocks.CYBER_PLATING.get(), RvBlocks.LUMEN_LOG.get(),
                RvBlocks.CYBER_GLASS.get(), RvBlocks.GLOWCAP.get(), RvBlocks.LUMEN_LOG.get(), persistent(RvBlocks.LUMEN_LEAVES.get()), false));
        m.put(Archetype.CELESTIAL, new MaterialSet(Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK, Blocks.AIR,
                RvBlocks.ANCIENT_GOLD.get(), Blocks.PEARLESCENT_FROGLIGHT, Blocks.QUARTZ_BRICKS, RvBlocks.STARMETAL_BLOCK.get(),
                Blocks.WHITE_STAINED_GLASS, Blocks.AIR, Blocks.QUARTZ_PILLAR, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.LUNARCOLONY, new MaterialSet(RvBlocks.LUNAR_DUST.get(), RvBlocks.LUNAR_DUST.get(), Blocks.STONE, Blocks.AIR,
                RvBlocks.CHROME_PLATING.get(), Blocks.SEA_LANTERN, RvBlocks.CHROME_PLATING.get(), Blocks.WHITE_CONCRETE,
                Blocks.GLASS, Blocks.AIR, Blocks.IRON_BLOCK, Blocks.AIR.defaultBlockState(), false));
        m.put(Archetype.CORALKING, new MaterialSet(Blocks.SAND, Blocks.SAND, RvBlocks.CORAL_STONE.get(), Blocks.WATER,
                Blocks.BRAIN_CORAL_BLOCK, RvBlocks.CORAL_STONE.get(), Blocks.PRISMARINE_BRICKS, Blocks.HORN_CORAL_BLOCK,
                Blocks.PINK_STAINED_GLASS, Blocks.AIR, Blocks.MANGROVE_LOG, persistent(Blocks.MANGROVE_LEAVES), false));
        m.put(Archetype.MIRROR, new MaterialSet(Blocks.WHITE_CONCRETE, Blocks.QUARTZ_BLOCK, Blocks.CALCITE, Blocks.WATER,
                Blocks.GLASS, Blocks.SEA_LANTERN, Blocks.SMOOTH_QUARTZ, Blocks.GLASS,
                Blocks.GLASS, Blocks.AIR, Blocks.BIRCH_LOG, Blocks.AIR.defaultBlockState(), false));
        return m;
    }

    public boolean hasPlant() {
        return !plant.isAir();
    }
}

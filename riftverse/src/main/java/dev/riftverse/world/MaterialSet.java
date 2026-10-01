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
        return m;
    }

    public boolean hasPlant() {
        return !plant.isAir();
    }
}

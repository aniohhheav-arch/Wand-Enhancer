package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import dev.riftverse.block.ClearBlock;
import dev.riftverse.block.CloudBlock;
import dev.riftverse.block.GlowcapBlock;
import dev.riftverse.block.GravityLiftBlock;
import dev.riftverse.block.MultiverseConsoleBlock;
import dev.riftverse.block.PortalFieldBlock;
import dev.riftverse.block.RiftAltarBlock;
import dev.riftverse.block.RiftBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Riftverse.MODID);
    /** Blocks that receive a matching BlockItem, in creative-tab order. */
    public static final List<DeferredBlock<? extends Block>> WITH_ITEMS = new ArrayList<>();

    public static final DeferredBlock<RiftBlock> RIFT = BLOCKS.register("rift", () -> new RiftBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).noCollission().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F)
            .lightLevel(s -> 13).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)));

    public static final DeferredBlock<PortalFieldBlock> PORTAL_FIELD = BLOCKS.register("portal_field", () -> new PortalFieldBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN).noCollission().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F)
            .lightLevel(s -> 11).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)));

    public static final DeferredBlock<Block> RIFT_FRAME = item("rift_frame", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 4)));

    public static final DeferredBlock<MultiverseConsoleBlock> MULTIVERSE_CONSOLE = item("multiverse_console", () -> new MultiverseConsoleBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 12).noOcclusion()));

    public static final DeferredBlock<RiftAltarBlock> RIFT_ALTAR = item("rift_altar", () -> new RiftAltarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_TILES).lightLevel(s -> 9).noOcclusion()));

    public static final DeferredBlock<GravityLiftBlock> GRAVITY_LIFT = item("gravity_lift", () -> new GravityLiftBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 12)));

    public static final DeferredBlock<Block> NEXUS_STONE = simple("nexus_stone", MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> NEXUS_BRICKS = simple("nexus_bricks", MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE_BRICKS, 0);
    public static final DeferredBlock<Block> NEXUS_GLOW = simple("nexus_glow", MapColor.COLOR_CYAN, 3.0F, SoundType.GLASS, 15);
    public static final DeferredBlock<Block> VOID_STONE = simple("void_stone", MapColor.COLOR_BLACK, 2.5F, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> VOID_CRYSTAL = simple("void_crystal", MapColor.COLOR_PURPLE, 1.5F, SoundType.AMETHYST, 10);
    public static final DeferredBlock<Block> RIFT_CRYSTAL = simple("rift_crystal", MapColor.COLOR_MAGENTA, 1.5F, SoundType.AMETHYST, 12);
    public static final DeferredBlock<Block> NEON_PANEL_CYAN = simple("neon_panel_cyan", MapColor.COLOR_CYAN, 1.0F, SoundType.GLASS, 15);
    public static final DeferredBlock<Block> NEON_PANEL_MAGENTA = simple("neon_panel_magenta", MapColor.COLOR_MAGENTA, 1.0F, SoundType.GLASS, 15);
    public static final DeferredBlock<Block> CYBER_PLATING = simple("cyber_plating", MapColor.COLOR_GRAY, 4.0F, SoundType.METAL, 0);
    public static final DeferredBlock<ClearBlock> CYBER_GLASS = item("cyber_glass", () -> new ClearBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6F).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 3)
            .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<Block> ALIEN_MOSS = simple("alien_moss", MapColor.COLOR_GREEN, 0.6F, SoundType.MOSS, 3);
    public static final DeferredBlock<Block> ALIEN_SOIL = simple("alien_soil", MapColor.TERRACOTTA_PURPLE, 0.5F, SoundType.ROOTED_DIRT, 0);
    public static final DeferredBlock<GlowcapBlock> GLOWCAP = item("glowcap", () -> new GlowcapBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GREEN).noCollission().instabreak().sound(SoundType.FUNGUS).lightLevel(s -> 12).noOcclusion()
            .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<RotatedPillarBlock> LUMEN_LOG = item("lumen_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(2.0F).sound(SoundType.STEM).lightLevel(s -> 4)));
    public static final DeferredBlock<ClearBlock> LUMEN_LEAVES = item("lumen_leaves", () -> new ClearBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.2F).sound(SoundType.AZALEA_LEAVES).noOcclusion().lightLevel(s -> 10)
            .isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<Block> SKYSTONE = simple("skystone", MapColor.QUARTZ, 2.0F, SoundType.CALCITE, 0);
    public static final DeferredBlock<Block> DREAM_TURF = simple("dream_turf", MapColor.COLOR_PINK, 0.6F, SoundType.MOSS, 2);
    public static final DeferredBlock<CloudBlock> DREAM_CLOUD = item("dream_cloud", () -> new CloudBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.WOOL).lightLevel(s -> 6).noOcclusion()
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<Block> DEAD_REGOLITH = simple("dead_regolith", MapColor.TERRACOTTA_BROWN, 0.7F, SoundType.SUSPICIOUS_GRAVEL, 0);
    public static final DeferredBlock<Block> ASHEN_ROCK = simple("ashen_rock", MapColor.COLOR_GRAY, 2.0F, SoundType.TUFF, 0);
    public static final DeferredBlock<Block> COSMIC_OBSIDIAN = item("cosmic_obsidian", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(25.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).lightLevel(s -> 4)));
    public static final DeferredBlock<Block> STARDUST_SAND = simple("stardust_sand", MapColor.COLOR_BLUE, 0.5F, SoundType.SAND, 5);
    public static final DeferredBlock<Block> GLITCH_BLOCK = simple("glitch_block", MapColor.COLOR_LIGHT_GREEN, 1.5F, SoundType.METAL, 8);
    public static final DeferredBlock<Block> ANCIENT_BRICKS = simple("ancient_bricks", MapColor.SAND, 3.0F, SoundType.STONE, 0);
    public static final DeferredBlock<Block> ANCIENT_GLYPH = simple("ancient_glyph", MapColor.GOLD, 3.0F, SoundType.STONE, 9);
    public static final DeferredBlock<Block> ANCIENT_GOLD = simple("ancient_gold", MapColor.GOLD, 4.0F, SoundType.METAL, 0);
    public static final DeferredBlock<Block> ABYSSAL_GLOW = simple("abyssal_glow", MapColor.COLOR_CYAN, 0.8F, SoundType.GLASS, 15);

    private RvBlocks() {}

    private static <B extends Block> DeferredBlock<B> item(String name, Supplier<B> supplier) {
        DeferredBlock<B> block = BLOCKS.register(name, supplier);
        WITH_ITEMS.add(block);
        return block;
    }

    private static DeferredBlock<Block> simple(String name, MapColor color, float strength, SoundType sound, int light) {
        return item(name, () -> {
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(color).strength(strength, strength * 3f)
                    .sound(sound).lightLevel(s -> light);
            if (strength >= 1.5F) props.requiresCorrectToolForDrops();
            return new Block(props);
        });
    }
}

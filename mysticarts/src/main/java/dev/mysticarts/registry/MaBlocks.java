package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.block.ClearBlock;
import dev.mysticarts.block.RelicPedestalBlock;
import dev.mysticarts.block.RiftFissureBlock;
import dev.mysticarts.block.RitualAltarBlock;
import dev.mysticarts.block.RuneLampBlock;
import dev.mysticarts.block.SpellBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MysticArts.MODID);
    /** Blocks that receive a matching BlockItem, in creative-tab order. */
    public static final List<DeferredBlock<? extends Block>> WITH_ITEMS = new ArrayList<>();

    public static final DeferredBlock<RitualAltarBlock> RITUAL_ALTAR = item("ritual_altar", () -> new RitualAltarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD).strength(4.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).lightLevel(s -> 10).noOcclusion()));
    public static final DeferredBlock<RelicPedestalBlock> RELIC_PEDESTAL = item("relic_pedestal", () -> new RelicPedestalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(3.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).lightLevel(s -> 7).noOcclusion()));

    public static final DeferredBlock<Block> MYSTIC_STONE_BRICKS = simple("mystic_stone_bricks", MapColor.TERRACOTTA_WHITE, 2.0F, SoundType.STONE, 0);
    public static final DeferredBlock<Block> CRACKED_MYSTIC_STONE_BRICKS = simple("cracked_mystic_stone_bricks", MapColor.TERRACOTTA_WHITE, 2.0F, SoundType.STONE, 0);
    public static final DeferredBlock<Block> CARVED_MYSTIC_STONE = simple("carved_mystic_stone", MapColor.TERRACOTTA_WHITE, 2.0F, SoundType.STONE, 0);
    public static final DeferredBlock<Block> GILDED_RUNESTONE = simple("gilded_runestone", MapColor.GOLD, 2.5F, SoundType.DEEPSLATE_TILES, 6);
    public static final DeferredBlock<Block> SANCTUM_FLOOR = simple("sanctum_floor", MapColor.COLOR_RED, 2.0F, SoundType.POLISHED_DEEPSLATE, 0);
    public static final DeferredBlock<Block> SANCTUM_PLANKS = simple("sanctum_planks", MapColor.COLOR_BROWN, 1.5F, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> ARCANE_BOOKSHELF = simple("arcane_bookshelf", MapColor.COLOR_BROWN, 1.5F, SoundType.WOOD, 3);
    public static final DeferredBlock<Block> COSMIC_RUIN_STONE = simple("cosmic_ruin_stone", MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE, 0);
    public static final DeferredBlock<Block> COSMIC_RUIN_BRICKS = simple("cosmic_ruin_bricks", MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE_BRICKS, 0);
    public static final DeferredBlock<Block> REALITY_CRYSTAL = simple("reality_crystal", MapColor.COLOR_RED, 1.5F, SoundType.AMETHYST, 10);
    public static final DeferredBlock<RuneLampBlock> RUNE_LAMP = item("rune_lamp", () -> new RuneLampBlock(0xFF9A2E, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).strength(1.5F).sound(SoundType.GLASS).lightLevel(s -> 15)));
    public static final DeferredBlock<ClearBlock> MANDALA_GLASS = item("mandala_glass", () -> new ClearBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).strength(0.6F).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 6)
            .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<ClearBlock> MIRROR_SHARD = item("mirror_shard", () -> new ClearBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIAMOND).strength(0.8F).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 9)
            .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<RiftFissureBlock> RIFT_FISSURE = item("rift_fissure", () -> new RiftFissureBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().lightLevel(s -> 13)
            .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)));

    public static final DeferredBlock<SpellBlock> SPELL_BARRIER = spell("spell_barrier", SpellBlock.Kind.BARRIER, 10);
    public static final DeferredBlock<SpellBlock> SPELL_ILLUSION = spell("spell_illusion", SpellBlock.Kind.ILLUSION, 4);
    public static final DeferredBlock<SpellBlock> SPELL_HAZARD = spell("spell_hazard", SpellBlock.Kind.HAZARD, 12);

    private MaBlocks() {}

    private static DeferredBlock<SpellBlock> spell(String name, SpellBlock.Kind kind, int light) {
        return BLOCKS.register(name, () -> new SpellBlock(kind, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().sound(SoundType.AMETHYST).lightLevel(s -> light)
                .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    }

    private static <B extends Block> DeferredBlock<B> item(String name, Supplier<B> supplier) {
        DeferredBlock<B> block = BLOCKS.register(name, supplier);
        WITH_ITEMS.add(block);
        return block;
    }

    private static DeferredBlock<Block> simple(String name, MapColor color, float strength, SoundType sound, int light) {
        return item(name, () -> {
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(color).strength(strength, strength * 3f)
                    .sound(sound).lightLevel(s -> light);
            if (strength >= 1.5F && sound != SoundType.WOOD) props.requiresCorrectToolForDrops();
            return new Block(props);
        });
    }
}

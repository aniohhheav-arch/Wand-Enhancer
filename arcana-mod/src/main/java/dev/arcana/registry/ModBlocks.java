package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.block.CrystalBlock;
import dev.arcana.magic.School;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArcanaMod.MODID);
    public static final Map<School, DeferredBlock<CrystalBlock>> CRYSTALS = new EnumMap<>(School.class);
    public static final Map<School, DeferredItem<BlockItem>> CRYSTAL_ITEMS = new EnumMap<>(School.class);

    static {
        for (School s : School.real()) {
            DeferredBlock<CrystalBlock> b = BLOCKS.register(s.id + "_crystal_cluster", () -> new CrystalBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE).strength(1.5f).sound(SoundType.AMETHYST_CLUSTER).lightLevel(st -> 9)
                .noOcclusion().pushReaction(PushReaction.DESTROY), s));
            CRYSTALS.put(s, b);
            CRYSTAL_ITEMS.put(s, ModItems.ITEMS.registerSimpleBlockItem(b, new Item.Properties()));
        }
    }

    public static void init() {}

    private ModBlocks() {}
}

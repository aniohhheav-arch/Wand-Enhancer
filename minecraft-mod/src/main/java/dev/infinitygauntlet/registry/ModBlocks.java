package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.nexus.ConsciousnessNexusBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(InfinityGauntlet.MOD_ID);

    public static final DeferredBlock<ConsciousnessNexusBlock> CONSCIOUSNESS_NEXUS = REGISTER.register("consciousness_nexus",
            () -> new ConsciousnessNexusBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(5f, 1200f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(s -> 10)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> CONSCIOUSNESS_NEXUS_ITEM = ModItems.REGISTER.register("consciousness_nexus",
            () -> new BlockItem(CONSCIOUSNESS_NEXUS.get(), new Item.Properties().rarity(Rarity.EPIC)));

    private ModBlocks() {
    }
}

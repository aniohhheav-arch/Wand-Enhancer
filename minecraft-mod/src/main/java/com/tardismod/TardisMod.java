package com.tardismod;

import com.tardismod.block.TardisBlock;
import com.tardismod.block.TardisBlockEntity;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Common entrypoint: registers the TARDIS block, its item and its GeckoLib-animated block entity. */
public class TardisMod implements ModInitializer {
    public static final String MOD_ID = "tardismod";

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    public static Block TARDIS_BLOCK;
    public static BlockEntityType<TardisBlockEntity> TARDIS_BE;

    @Override
    public void onInitialize() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("tardis"));
        TARDIS_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new TardisBlock(BlockBehaviour.Properties.of().setId(blockKey).strength(4.0f, 1200.0f).noOcclusion().lightLevel(s -> 8)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("tardis"));
        Item item = Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BlockItem(TARDIS_BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        TARDIS_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("tardis"),
            new BlockEntityType<>(TardisBlockEntity::new, Set.of(TARDIS_BLOCK)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(item));
    }
}

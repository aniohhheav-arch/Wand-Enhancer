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
        // Dev command: /tardis build  — generates the interior console room (also happens automatically on first entry).
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) ->
            dispatcher.register(net.minecraft.commands.Commands.literal("tardis")
                .requires(net.minecraft.commands.Commands.hasPermission(new net.minecraft.server.permissions.PermissionCheck.Require(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)))
                .then(net.minecraft.commands.Commands.literal("build").executes(c -> {
                    var level = c.getSource().getServer().getLevel(Interior.KEY);
                    if (level == null) { c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("interior dimension not loaded")); return 0; }
                    Interior.build(level);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("TARDIS interior built: " + level.getBlockState(new net.minecraft.core.BlockPos(0, 65, 9)).getBlock()), false);
                    return 1; }))));
    }
}

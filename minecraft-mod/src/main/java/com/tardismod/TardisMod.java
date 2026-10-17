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
    public static Block CONSOLE_BLOCK;
    public static BlockEntityType<TardisBlockEntity> TARDIS_BE;

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(FlightPayload.TYPE, FlightPayload.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Travel::tick);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("tardis"));
        TARDIS_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new TardisBlock(BlockBehaviour.Properties.of().setId(blockKey).strength(4.0f, 1200.0f).noOcclusion().lightLevel(s -> 8)));
        ResourceKey<Block> consoleKey = ResourceKey.create(Registries.BLOCK, id("console"));
        CONSOLE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, consoleKey, new com.tardismod.block.ConsoleBlock(BlockBehaviour.Properties.of().setId(consoleKey).strength(3.0f, 1200.0f).lightLevel(s -> 10)));
        ResourceKey<Item> consoleItemKey = ResourceKey.create(Registries.ITEM, id("console"));
        Item consoleItem = Registry.register(BuiltInRegistries.ITEM, consoleItemKey, new BlockItem(CONSOLE_BLOCK, new Item.Properties().setId(consoleItemKey).useBlockDescriptionPrefix()));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("tardis"));
        Item item = Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BlockItem(TARDIS_BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        TARDIS_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("tardis"),
            new BlockEntityType<>(TardisBlockEntity::new, Set.of(TARDIS_BLOCK)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> { output.accept(item); output.accept(consoleItem); });
        // Dev command: /tardis build  — generates the interior console room (also happens automatically on first entry).
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) ->
            dispatcher.register(net.minecraft.commands.Commands.literal("tardis")
                .requires(net.minecraft.commands.Commands.hasPermission(new net.minecraft.server.permissions.PermissionCheck.Require(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)))
                .then(net.minecraft.commands.Commands.literal("build").executes(c -> {
                    var level = c.getSource().getServer().getLevel(Interior.KEY);
                    if (level == null) { c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("interior dimension not loaded")); return 0; }
                    Interior.build(level, 0);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("TARDIS interior built: " + level.getBlockState(Interior.doorPos(0)).getBlock()), false);
                    return 1; }))
                .then(net.minecraft.commands.Commands.literal("dest")
                    .then(net.minecraft.commands.Commands.argument("dim", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                    .then(net.minecraft.commands.Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(net.minecraft.commands.Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(c -> {
                        var level = c.getSource().getServer().getLevel(Interior.KEY); Interior.build(level, 0);
                        var door = (TardisBlockEntity) level.getBlockEntity(Interior.doorPos(0));
                        door.setDestination(Travel.DIMENSIONS.get(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "dim")), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"));
                        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("dest: " + Travel.describe(door)), false); return 1; })))))
                .then(net.minecraft.commands.Commands.literal("slot").executes(c -> {
                    var level = c.getSource().getServer().getLevel(Interior.KEY); int id = Interior.allocate(level);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("allocated interior #" + id + " door at " + Interior.doorPos(id).toShortString()), false); return 1; }))
                .then(net.minecraft.commands.Commands.literal("flight").executes(c -> {
                    var level = c.getSource().getServer().getLevel(Interior.KEY); Interior.build(level, 0);
                    var door = (TardisBlockEntity) level.getBlockEntity(Interior.doorPos(0));
                    String r = Travel.start(level, door); c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("flight: " + r), false); return 1; }))
                .then(net.minecraft.commands.Commands.literal("fly").executes(c -> {
                    var level = c.getSource().getServer().getLevel(Interior.KEY); Interior.build(level, 0);
                    var door = (TardisBlockEntity) level.getBlockEntity(Interior.doorPos(0));
                    String r = Travel.fly(level, door); c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("fly: " + r + " now at " + door.returnDimension() + " " + door.returnPos().toShortString()), false); return 1; }))));
    }
}

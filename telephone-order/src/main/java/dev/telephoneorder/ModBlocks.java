package dev.telephoneorder;

import dev.telephoneorder.block.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;
import java.util.function.Function;

public final class ModBlocks {
    public static Block TELEPHONE_ROTARY, TELEPHONE_TERMINAL, TELEPHONE_UPLINK, WORKBENCH, SENTRY;
    public static BlockEntityType<SentryBlockEntity> SENTRY_BE;

    private ModBlocks() {}

    private static Block reg(String name, Function<BlockBehaviour.Properties, Block> f, BlockBehaviour.Properties p) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ModItems.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, f.apply(p.setId(key)));
    }

    public static void register() {
        TELEPHONE_ROTARY = reg("telephone_rotary", p -> new TelephoneBlock(p, 0), BlockBehaviour.Properties.of().strength(1.2f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 0));
        TELEPHONE_TERMINAL = reg("telephone_terminal", p -> new TelephoneBlock(p, 1), BlockBehaviour.Properties.of().strength(1.2f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5));
        TELEPHONE_UPLINK = reg("telephone_uplink", p -> new TelephoneBlock(p, 2), BlockBehaviour.Properties.of().strength(1.6f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 3));
        WORKBENCH = reg("weapons_workbench", WorkbenchBlock::new, BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 4));
        SENTRY = reg("sentinel_turret", SentryBlock::new, BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 3));
        SENTRY_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ModItems.id("sentinel_turret"),
                new BlockEntityType<>(SentryBlockEntity::new, Set.of(SENTRY)));
    }
}

package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.block.RelicPedestalBlockEntity;
import dev.mysticarts.block.RitualAltarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MysticArts.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RitualAltarBlockEntity>> RITUAL_ALTAR = BLOCK_ENTITIES.register("ritual_altar",
            () -> BlockEntityType.Builder.of(RitualAltarBlockEntity::new, MaBlocks.RITUAL_ALTAR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelicPedestalBlockEntity>> RELIC_PEDESTAL = BLOCK_ENTITIES.register("relic_pedestal",
            () -> BlockEntityType.Builder.of(RelicPedestalBlockEntity::new, MaBlocks.RELIC_PEDESTAL.get()).build(null));

    private MaBlockEntities() {}
}

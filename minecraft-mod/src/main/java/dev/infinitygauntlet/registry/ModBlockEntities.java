package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.nexus.NexusBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InfinityGauntlet.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NexusBlockEntity>> CONSCIOUSNESS_NEXUS =
            REGISTER.register("consciousness_nexus", () -> BlockEntityType.Builder.of(NexusBlockEntity::new, ModBlocks.CONSCIOUSNESS_NEXUS.get()).build(null));

    private ModBlockEntities() {
    }
}

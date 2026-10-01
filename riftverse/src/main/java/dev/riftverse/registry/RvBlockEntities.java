package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import dev.riftverse.block.GravityLiftBlockEntity;
import dev.riftverse.block.PortalFieldBlockEntity;
import dev.riftverse.block.RiftBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Riftverse.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftBlockEntity>> RIFT = BLOCK_ENTITIES.register("rift",
            () -> BlockEntityType.Builder.of(RiftBlockEntity::new, RvBlocks.RIFT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortalFieldBlockEntity>> PORTAL_FIELD = BLOCK_ENTITIES.register("portal_field",
            () -> BlockEntityType.Builder.of(PortalFieldBlockEntity::new, RvBlocks.PORTAL_FIELD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityLiftBlockEntity>> GRAVITY_LIFT = BLOCK_ENTITIES.register("gravity_lift",
            () -> BlockEntityType.Builder.of(GravityLiftBlockEntity::new, RvBlocks.GRAVITY_LIFT.get()).build(null));

    private RvBlockEntities() {}
}

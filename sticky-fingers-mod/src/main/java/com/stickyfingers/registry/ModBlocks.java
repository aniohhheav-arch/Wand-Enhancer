package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.world.ZipperPlatformBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(StickyFingersMod.MODID);

    /** Temporary floating platform stitched out of an aerial zipper. Never drops and always gets removed again. */
    public static final DeferredBlock<Block> ZIPPER_PLATFORM = REGISTER.register("zipper_platform", () ->
            new ZipperPlatformBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().lightLevel(s -> 7)
                    .sound(SoundType.CHAIN).pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false)));

    private ModBlocks() {}
}

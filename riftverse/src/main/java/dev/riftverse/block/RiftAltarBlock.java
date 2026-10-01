package dev.riftverse.block;

import dev.riftverse.entity.boss.RiftWardenEntity;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Where the Rift Warden, guardian of the boundaries, can be called forth. */
public class RiftAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(3, 4, 3, 13, 12, 13), Block.box(1, 12, 1, 15, 15, 15));

    public RiftAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(RvItems.RIFT_SIGIL.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel server) {
            AABB area = new AABB(pos).inflate(96);
            if (!server.getEntitiesOfClass(RiftWardenEntity.class, area).isEmpty()) {
                player.displayClientMessage(Component.translatable("message.riftverse.warden_present"), true);
                return ItemInteractionResult.FAIL;
            }
            RiftWardenEntity.summon(server, pos.above(), player);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.riftverse.altar_hint"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 1.2 + random.nextDouble() * 1.5;
            level.addParticle(RvParticles.INFALL.get().with(random.nextBoolean() ? 0xFFC14D : 0x8F6BFF, 0.3f, 50),
                    pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 1 + random.nextDouble() * 2, pos.getZ() + 0.5 + Math.sin(a) * r,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        }
    }
}

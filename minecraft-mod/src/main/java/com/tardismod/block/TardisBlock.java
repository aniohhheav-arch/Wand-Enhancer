package com.tardismod.block;

import com.tardismod.Interior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The police box. Right-click toggles the doors (animated by GeckoLib on the client). */
public class TardisBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.box(0.02, 0, 0.02, 0.98, 2.0, 0.98);

    public TardisBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TardisBlockEntity(pos, state); }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; } // drawn by the GeckoLib renderer

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    /** Right-click opens/closes the doors. With the doors open, walk into the doorway to go through. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TardisBlockEntity be) be.setOpen(!be.isOpen());
        return InteractionResult.SUCCESS;
    }

    /** With the doors open the front slab of the box is passable, so the player can step into the doorway. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!(level.getBlockEntity(pos) instanceof TardisBlockEntity be) || !be.isOpen()) return SHAPE;
        return switch (state.getValue(HorizontalDirectionalBlock.FACING)) {
            case NORTH -> Shapes.box(0.02, 0, 0.32, 0.98, 2.0, 0.98);
            case SOUTH -> Shapes.box(0.02, 0, 0.02, 0.98, 2.0, 0.68);
            case WEST -> Shapes.box(0.32, 0, 0.02, 0.98, 2.0, 0.98);
            default -> Shapes.box(0.02, 0, 0.02, 0.68, 2.0, 0.98);
        };
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() ? null : (lvl, p, st, be) -> { if (be instanceof TardisBlockEntity t) t.serverTick(lvl, p, st); };
    }
}

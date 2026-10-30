package dev.arcana.block;

import dev.arcana.magic.School;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A naturally generated school crystal formation: glows, sits on solid ground, drops school crystals. */
public class CrystalBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 13, 13);
    public final School school;

    public CrystalBlock(Properties props, School school) {
        super(props);
        this.school = school;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState nb, LevelAccessor level, BlockPos pos, BlockPos nbPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, nb, level, pos, nbPos);
    }
}

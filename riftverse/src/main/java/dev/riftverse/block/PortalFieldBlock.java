package dev.riftverse.block;

import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The shimmering membrane inside a Rift Frame or a Nexus gate. */
public class PortalFieldBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);

    public PortalFieldBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    public static boolean isFrame(BlockState s) {
        return s.is(RvBlocks.RIFT_FRAME.get()) || s.is(RvBlocks.NEXUS_BRICKS.get()) || s.is(RvBlocks.NEXUS_GLOW.get())
                || s.is(RvBlocks.NEXUS_STONE.get()) || s.is(RvBlocks.PORTAL_FIELD.get());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean inPlane = direction.getAxis() == Direction.Axis.Y || direction.getAxis() == axis;
        if (inPlane && !isFrame(neighbor)) return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || player.isSpectator()) return;
        if (!(level.getBlockEntity(pos) instanceof PortalFieldBlockEntity field)) return;
        int color = field.color();
        TransitManager.begin(player, TransitKind.PORTAL, field.destination(), Vec3.atCenterOf(pos), color, 0xFFFFFF, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        int color = 0x7DF9FF;
        if (level.getBlockEntity(pos) instanceof PortalFieldBlockEntity field) color = field.color();
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        double px = pos.getX() + (x ? random.nextDouble() : 0.5);
        double pz = pos.getZ() + (x ? 0.5 : random.nextDouble());
        double py = pos.getY() + random.nextDouble();
        double out = (random.nextDouble() - 0.5) * 0.1;
        level.addParticle(RvParticles.MOTE.get().with(color, 0.3f, 30), px, py, pz, x ? 0 : out, 0.02, x ? out : 0);
        if (random.nextInt(80) == 0) level.playLocalSound(px, py, pz, RvSounds.RIFT_AMBIENT.get(), SoundSource.BLOCKS, 0.5f, 1.2f, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalFieldBlockEntity(pos, state);
    }
}

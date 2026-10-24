package dev.telephoneorder.block;

import dev.telephoneorder.phone.PhoneService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A physical, interactive telephone: you pick up the receiver (the model changes), it rings, it has a keypad / dial. */
public class TelephoneBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<PhoneStateProp> PHONE = EnumProperty.create("phone", PhoneStateProp.class);
    private static final VoxelShape SHAPE = Block.box(1, 0, 0, 15, 11, 15);
    public final int model;

    public TelephoneBlock(BlockBehaviour.Properties props, int model) {
        super(props);
        this.model = model;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(PHONE, PhoneStateProp.IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PHONE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) PhoneService.interact(sp, sl, pos, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        PhoneService.ringTick(level, pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        PhoneStateProp s = state.getValue(PHONE);
        if (s.ringing() && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.9, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, random.nextInt(24) / 24.0, 0, 0);
        }
        if (model == 1 && s == PhoneStateProp.IDLE && random.nextInt(60) == 0) {
            level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.15, 0, 0.01, 0);
        }
    }
}

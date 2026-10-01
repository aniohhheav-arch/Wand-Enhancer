package dev.riftverse.block;

import dev.riftverse.registry.RvBlockEntities;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A tear in reality. Rendered entirely by shaders; the block itself is only an anchor. */
public class RiftBlock extends Block implements EntityBlock {
    public static final EnumProperty<RiftType> TYPE = EnumProperty.create("type", RiftType.class);
    private static final VoxelShape SHAPE = Block.box(4, 2, 4, 12, 14, 12);

    public RiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, RiftType.AZURE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift) || !rift.isHarvestable()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            int count = 1 + level.random.nextInt(2);
            ItemStack shards = new ItemStack(RvItems.RIFT_SHARD.get(), count);
            if (!player.addItem(shards)) player.drop(shards, false);
            RiftType type = state.getValue(TYPE);
            server.sendParticles(RvParticles.SPARK.get().with(type.colorA, 0.9f, 24), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.25);
            level.playSound(null, pos, RvSounds.RIFT_OPEN.get(), SoundSource.BLOCKS, 0.8f, 1.6f);
            rift.harvest();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        RiftType type = state.getValue(TYPE);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 1.5 + random.nextDouble() * 2.5;
            double y = (random.nextDouble() - 0.5) * 3.5;
            level.addParticle(RvParticles.INFALL.get().with(random.nextBoolean() ? type.colorA : type.colorB, 0.35f + random.nextFloat() * 0.4f, 40),
                    cx + Math.cos(a) * r, cy + y, cz + Math.sin(a) * r, cx, cy, cz);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(RvParticles.SPARK.get().with(type.colorB, 0.25f, 20), cx + (random.nextDouble() - 0.5) * 1.2, cy + (random.nextDouble() - 0.5) * 2.4,
                    cz + (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15);
        }
        if (random.nextInt(60) == 0) {
            level.playLocalSound(cx, cy, cz, RvSounds.RIFT_AMBIENT.get(), SoundSource.BLOCKS, 0.7f, 0.8f + random.nextFloat() * 0.4f, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? Tickers.of(type, RvBlockEntities.RIFT.get(), RiftBlockEntity::clientTick)
                : Tickers.of(type, RvBlockEntities.RIFT.get(), RiftBlockEntity::serverTick);
    }
}

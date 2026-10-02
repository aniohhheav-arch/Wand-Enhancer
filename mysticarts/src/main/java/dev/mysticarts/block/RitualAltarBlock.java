package dev.mysticarts.block;

import dev.mysticarts.registry.MaBlockEntities;
import dev.mysticarts.registry.MaParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The mystic crafting station. Ingredients are dropped onto it; invoking it performs the matching ritual. */
public class RitualAltarBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(3, 3, 3, 13, 10, 13), Block.box(1, 10, 1, 15, 13, 15));

    public RitualAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RitualAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return Tickers.of(type, MaBlockEntities.RITUAL_ALTAR.get(), RitualAltarBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RitualAltarBlockEntity altar) {
            Component result = altar.invoke(player);
            player.displayClientMessage(result, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2;
        level.addParticle(MaParticles.RUNE.get().with(0xFF9A2E, 0.3f, 40), pos.getX() + 0.5 + Math.cos(a) * 0.9, pos.getY() + 0.9,
                pos.getZ() + 0.5 + Math.sin(a) * 0.9, 0, 0.02, 0);
    }
}

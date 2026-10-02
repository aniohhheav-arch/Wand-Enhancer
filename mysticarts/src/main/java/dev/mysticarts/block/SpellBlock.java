package dev.mysticarts.block;

import dev.mysticarts.power.Source;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.world.TemporaryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A conjured block: barriers (solid), illusions (no collision) and reality walls (hurt what touches them). Always
 * placed by {@link TemporaryBlocks}, which restores the original block when the spell ends; a scheduled tick removes
 * strays left behind by a crash.
 */
public class SpellBlock extends ClearBlock {
    public static final IntegerProperty TINT = IntegerProperty.create("tint", 0, 7);

    public enum Kind { BARRIER, ILLUSION, HAZARD }

    /** Inset by a pixel so anything pressing against the wall is "inside" it and gets hurt. */
    private static final VoxelShape HAZARD_SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    private final Kind kind;

    public SpellBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(TINT, 0));
    }

    public static int tintOf(Source source) {
        return source.ordinal();
    }

    public static int color(int tint) {
        return Source.byId(tint).color;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TINT);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case ILLUSION -> Shapes.empty();
            case HAZARD -> HAZARD_SHAPE;
            case BARRIER -> Shapes.block();
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 20 * 60 * 5);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!TemporaryBlocks.get(level).tracks(pos)) level.removeBlock(pos, false);
        else level.scheduleTick(pos, this, 20 * 60);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (kind == Kind.HAZARD && !level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof Player)) {
            living.hurt(level.damageSources().magic(), 3f);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        entityInside(state, level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        int c = color(state.getValue(TINT));
        level.addParticle(MaParticles.MOTE.get().with(c, 0.35f, 30), pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(), 0, 0.01, 0);
    }
}

package dev.mysticarts.block;

import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A crack in reality at dimensional rift sites. Hums, bleeds light and gently drags things toward it. */
public class RiftFissureBlock extends Block {
    public RiftFissureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(2, 0, 2, 14, 16, 14);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity living && level.getGameTime() % 10 == 0) {
            living.hurt(level.damageSources().magic(), 1f);
        }
        Vec3 c = Vec3.atCenterOf(pos).subtract(entity.position());
        entity.setDeltaMovement(entity.getDeltaMovement().add(c.scale(0.02)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 1 + random.nextDouble() * 2;
            level.addParticle(MaParticles.INFALL.get().with(random.nextBoolean() ? 0xB04CFF : 0x4CA0FF, 0.3f, 40),
                    pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + random.nextDouble() * 2, pos.getZ() + 0.5 + Math.sin(a) * r,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        if (random.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, MaSounds.PORTAL_AMBIENT.get(), SoundSource.AMBIENT, 0.6f, 0.6f, false);
        }
    }
}

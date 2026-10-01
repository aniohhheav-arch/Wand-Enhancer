package dev.riftverse.block;

import dev.riftverse.registry.RvBlockEntities;
import dev.riftverse.registry.RvParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GravityLiftBlockEntity extends BlockEntity {
    public static final int RANGE = 16;

    public GravityLiftBlockEntity(BlockPos pos, BlockState state) {
        super(RvBlockEntities.GRAVITY_LIFT.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GravityLiftBlockEntity be) {
        AABB column = new AABB(pos.getX() + 0.05, pos.getY() + 1, pos.getZ() + 0.05, pos.getX() + 0.95, pos.getY() + 1 + RANGE, pos.getZ() + 0.95);
        for (Entity e : level.getEntitiesOfClass(Entity.class, column)) {
            boolean player = e instanceof Player;
            // Players move client-side; everything else is simulated on the server.
            if (player != level.isClientSide) continue;
            if (e.isShiftKeyDown()) continue;
            Vec3 v = e.getDeltaMovement();
            double lift = 0.11 * (1.0 - (e.getY() - pos.getY()) / (RANGE + 2.0));
            e.setDeltaMovement(v.x * 0.9, Math.min(0.85, v.y + lift + 0.03), v.z * 0.9);
            e.fallDistance = 0;
            if (!player) e.hurtMarked = true;
        }
        if (level.isClientSide && level.random.nextInt(2) == 0) {
            level.addParticle(RvParticles.MOTE.get().with(0x7AF0D0, 0.25f, 50),
                    pos.getX() + 0.2 + level.random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + level.random.nextDouble() * 0.6, 0, 0.25, 0);
        }
    }
}

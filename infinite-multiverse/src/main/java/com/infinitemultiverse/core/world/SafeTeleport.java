package com.infinitemultiverse.core.world;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Teleport destination validation shared by every movement ability, portal and dimension transition.
 * A destination is safe when the entity's full bounding box fits without collisions, inside the world border and
 * build limits, in a loaded chunk, and away from lava and fire.
 */
public final class SafeTeleport {
    private static final int[] VERTICAL_ORDER = {0, 1, -1, 2, -2, 3, -3, 4, -4};
    private static final double GROUND_SNAP_DISTANCE = 3.0;

    private SafeTeleport() {
    }

    /**
     * Finds the closest safe feet position to {@code desiredFeet}, searching outward in horizontal rings and
     * alternating up/down. Positions with ground shortly below are snapped onto it so blinks land cleanly.
     */
    public static Optional<Vec3> findSafeSpot(ServerLevel level, Entity entity, Vec3 desiredFeet, int horizontalRadius, int verticalRadius) {
        if (isSafe(level, entity, desiredFeet)) {
            return Optional.of(snapToGround(level, entity, desiredFeet));
        }
        BlockPos base = BlockPos.containing(desiredFeet);
        for (int r = 0; r <= horizontalRadius; r++) {
            for (int dy : VERTICAL_ORDER) {
                if (Math.abs(dy) > verticalRadius) {
                    continue;
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        Vec3 candidate = new Vec3(base.getX() + 0.5 + dx, base.getY() + dy, base.getZ() + 0.5 + dz);
                        if (isSafe(level, entity, candidate)) {
                            return Optional.of(snapToGround(level, entity, candidate));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isSafe(ServerLevel level, Entity entity, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet);
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) {
            return false;
        }
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(feet);
        if (!level.noCollision(entity, box)) {
            return false;
        }
        return level.getBlockStates(box.inflate(0.0, 0.5, 0.0))
                .noneMatch(state -> state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE));
    }

    private static Vec3 snapToGround(ServerLevel level, Entity entity, Vec3 feet) {
        BlockHitResult hit = level.clip(new ClipContext(feet, feet.subtract(0.0, GROUND_SNAP_DISTANCE, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) {
            return feet;
        }
        Vec3 grounded = new Vec3(feet.x, hit.getLocation().y, feet.z);
        return isSafe(level, entity, grounded) ? grounded : feet;
    }
}

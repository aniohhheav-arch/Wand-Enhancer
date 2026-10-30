package dev.infinitygauntlet.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Collision-safe, dimension-aware teleportation used by every Space Stone ability. */
public final class TeleportUtil {
    /**
     * Finds the closest position near {@code target} (feet position) where {@code entity}'s hitbox fits without
     * colliding. Searches vertically first (most common: aiming into a floor or ceiling), then in rings outward.
     */
    @Nullable
    public static Vec3 findSafeSpot(ServerLevel level, Entity entity, Vec3 target, int searchRadius) {
        if (fits(level, entity, target)) {
            return target;
        }
        for (int dy = 1; dy <= searchRadius + 1; dy++) {
            for (int sign : new int[]{1, -1}) {
                Vec3 p = target.add(0, dy * sign, 0);
                if (fits(level, entity, p)) {
                    return p;
                }
            }
        }
        for (int r = 1; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 2; dy++) {
                        Vec3 p = target.add(dx, dy, dz);
                        if (fits(level, entity, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean fits(ServerLevel level, Entity entity, Vec3 feet) {
        if (!level.isInWorldBounds(BlockPos.containing(feet))) {
            return false;
        }
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(feet).deflate(1.0E-4);
        return level.noCollision(entity, box) && !level.getFluidState(BlockPos.containing(feet)).is(FluidTags.LAVA);
    }

    /** Snaps a point down onto the first solid surface within {@code maxDrop} blocks (keeps group teleports from landing mid-air). */
    public static Vec3 groundBelow(ServerLevel level, Vec3 pos, int maxDrop) {
        BlockPos.MutableBlockPos m = BlockPos.containing(pos).mutable();
        for (int i = 0; i < maxDrop; i++) {
            BlockPos below = m.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(pos.x, m.getY(), pos.z);
            }
            m.move(0, -1, 0);
        }
        return pos;
    }

    /**
     * Moves {@code entity} to {@code destLevel} at {@code pos}, keeping (or replacing) its velocity. Handles players,
     * mobs and projectiles, including cross-dimension travel. Returns false if the entity could not be moved.
     */
    public static boolean teleport(Entity entity, ServerLevel destLevel, Vec3 pos, float yRot, float xRot, @Nullable Vec3 velocity) {
        Vec3 motion = velocity != null ? velocity : entity.getDeltaMovement();
        destLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(BlockPos.containing(pos)), 3, BlockPos.containing(pos));
        if (entity.isPassenger()) {
            entity.stopRiding();
        }
        entity.ejectPassengers();
        boolean ok = entity.teleportTo(destLevel, pos.x, pos.y, pos.z, Set.<RelativeMovement>of(), yRot, xRot);
        if (!ok) {
            return false;
        }
        entity.fallDistance = 0;
        if (entity.level() == destLevel && !entity.isRemoved()) {
            entity.setDeltaMovement(motion);
            entity.hurtMarked = true;
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
        } else {
            // cross-dimension: the entity was re-created in the destination level with its NBT (including Motion)
            Entity moved = destLevel.getEntity(entity.getUUID());
            if (moved != null) {
                moved.setDeltaMovement(motion);
                moved.hurtMarked = true;
            }
        }
        return true;
    }

    private TeleportUtil() {
    }
}

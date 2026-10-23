package com.wandcraft.magic.util;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Crosshair raycasting for spells. */
public final class Aim {
    public record Result(Vec3 pos, LivingEntity entity, BlockHitResult block) {
        public boolean hitEntity() { return entity != null; }
        public boolean hitBlock() { return block != null && block.getType() == HitResult.Type.BLOCK; }
    }

    private Aim() {}

    public static BlockHitResult block(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition();
        Vec3 to = from.add(p.getLookAngle().scale(range));
        return p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    }

    public static Result trace(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition();
        Vec3 to = from.add(p.getLookAngle().scale(range));
        BlockHitResult b = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double maxSqr = b.getType() == HitResult.Type.MISS ? range * range : b.getLocation().distanceToSqr(from);
        EntityHitResult e = ProjectileUtil.getEntityHitResult(p, from, to, new AABB(from, to).inflate(1.0),
                en -> en instanceof LivingEntity le && Combat.canTarget(p, le), maxSqr);
        if (e != null && e.getEntity() instanceof LivingEntity le) return new Result(e.getLocation(), le, null);
        return new Result(b.getLocation(), null, b);
    }

    /** Aim entity only, or null. */
    public static LivingEntity entity(ServerPlayer p, double range) { return trace(p, range).entity(); }

    /** Ground position the crosshair points at (top of the first solid block), or null when out of range. */
    public static Vec3 ground(ServerPlayer p, double range) {
        BlockHitResult b = block(p, range);
        if (b.getType() != HitResult.Type.BLOCK) {
            // Look down from the end of the ray to find terrain beneath it.
            Vec3 end = b.getLocation();
            BlockHitResult down = p.level().clip(new ClipContext(end, end.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            return down.getType() == HitResult.Type.BLOCK ? down.getLocation() : null;
        }
        return b.getDirection() == Direction.UP ? b.getLocation() : b.getLocation().add(Vec3.atLowerCornerOf(b.getDirection().getNormal()).scale(0.1));
    }

    /** Highest solid ground at/below {@code from} within {@code range} blocks, as the standing position. */
    public static Optional<BlockPos> floorBelow(ServerLevel l, BlockPos from, int range) {
        BlockPos.MutableBlockPos m = from.mutable();
        for (int i = 0; i <= range; i++) {
            if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty()) return Optional.of(m.above().immutable());
            m.move(Direction.DOWN);
        }
        return Optional.empty();
    }

    /** Finds the standing level for a wave/line near {@code pos}, stepping up or down at most 2 blocks. */
    public static Optional<BlockPos> stepGround(ServerLevel l, BlockPos pos) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = pos.offset(0, dy, 0);
            if (!l.getBlockState(p.below()).getCollisionShape(l, p.below()).isEmpty()
                    && l.getBlockState(p).getCollisionShape(l, p).isEmpty()
                    && l.getBlockState(p.above()).getCollisionShape(l, p.above()).isEmpty()) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}

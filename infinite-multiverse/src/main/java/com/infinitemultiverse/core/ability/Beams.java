package com.infinitemultiverse.core.ability;

import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Line traces for beam-like powers: stops at solid blocks, collects hostile entities near the line. */
public final class Beams {
    public record Result(Vec3 start, Vec3 end, List<LivingEntity> hits) {
        public Vec3 vector() {
            return end.subtract(start);
        }
    }

    private Beams() {
    }

    public static Result trace(ServerPlayer owner, double length, double radius, int maxHits) {
        Vec3 start = owner.getEyePosition();
        Vec3 dir = owner.getLookAngle();
        BlockHitResult block = owner.serverLevel().clip(new ClipContext(start, start.add(dir.scale(length)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = block.getType() == HitResult.Type.MISS ? start.add(dir.scale(length)) : block.getLocation();
        AABB box = new AABB(start, end).inflate(radius + 1.0);
        List<LivingEntity> hits = owner.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                        e -> AbilityTargeting.isHostileTarget(owner, e) && distanceToSegment(e.getBoundingBox().getCenter(), start, end) <= radius + e.getBbWidth() * 0.5)
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(start))).limit(maxHits).toList();
        return new Result(start, end, hits);
    }

    public static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-8 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / lengthSqr));
        return point.distanceTo(a.add(ab.scale(t)));
    }
}

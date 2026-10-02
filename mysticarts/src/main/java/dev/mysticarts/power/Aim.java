package dev.mysticarts.power;

import dev.mysticarts.MaConfig;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.jetbrains.annotations.Nullable;

/** Targeting, area queries and safe-teleport helpers shared by every ability. */
public final class Aim {
    private Aim() {}

    public static Vec3 eye(LivingEntity e) {
        return e.getEyePosition();
    }

    public static Vec3 look(LivingEntity e) {
        return e.getLookAngle();
    }

    /** Point in front of the caster's hands, where spells leave from. */
    public static Vec3 hands(LivingEntity e) {
        return e.getEyePosition().add(e.getLookAngle().scale(0.8)).add(0, -0.35, 0);
    }

    public static BlockHitResult block(LivingEntity e, double range) {
        Vec3 from = eye(e);
        Vec3 to = from.add(look(e).scale(range));
        return e.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
    }

    /** First living entity along the caster's view (with generous aim assist), blocked by terrain. */
    @Nullable
    public static LivingEntity entity(LivingEntity caster, double range) {
        return entity(caster, range, e -> true);
    }

    @Nullable
    public static LivingEntity entity(LivingEntity caster, double range, Predicate<LivingEntity> filter) {
        Vec3 from = eye(caster);
        Vec3 dir = look(caster);
        BlockHitResult wall = block(caster, range);
        double max = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from);
        Vec3 to = from.add(dir.scale(max));
        AABB sweep = caster.getBoundingBox().expandTowards(dir.scale(max)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, from, to, sweep,
                e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e != caster && filter.test(l), 0.6f);
        if (hit != null && hit.getEntity() instanceof LivingEntity l) return l;
        // aim assist: closest to the view ray within a narrow cone
        LivingEntity best = null;
        double bestScore = 0.985;
        for (LivingEntity l : caster.level().getEntitiesOfClass(LivingEntity.class, sweep.inflate(2), x -> x != caster && x.isAlive() && filter.test(x))) {
            Vec3 to2 = l.getBoundingBox().getCenter().subtract(from);
            double d = to2.length();
            if (d > max + 1) continue;
            double dot = to2.normalize().dot(dir);
            if (dot > bestScore && caster.hasLineOfSight(l)) {
                bestScore = dot;
                best = l;
            }
        }
        return best;
    }

    /** Any entity (including items, projectiles) along the view. */
    @Nullable
    public static Entity anyEntity(LivingEntity caster, double range) {
        Vec3 from = eye(caster);
        Vec3 dir = look(caster);
        BlockHitResult wall = block(caster, range);
        double max = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from);
        AABB sweep = caster.getBoundingBox().expandTowards(dir.scale(max)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, from, from.add(dir.scale(max)), sweep,
                e -> e != caster && !e.isSpectator() && e.isAlive(), 0.8f);
        return hit == null ? null : hit.getEntity();
    }

    /** Where the caster is looking: the block face hit, or the point at {@code range}. */
    public static Vec3 point(LivingEntity e, double range) {
        BlockHitResult hit = block(e, range);
        return hit.getType() == HitResult.Type.MISS ? eye(e).add(look(e).scale(range)) : hit.getLocation();
    }

    public static List<LivingEntity> around(LivingEntity caster, Vec3 center, double radius, Predicate<LivingEntity> filter) {
        return caster.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e != caster && e.isAlive() && !e.isSpectator() && e.position().distanceToSqr(center) <= radius * radius && filter.test(e));
    }

    /** Living things in a cone in front of the caster. */
    public static List<LivingEntity> cone(LivingEntity caster, double range, double cosHalfAngle) {
        Vec3 from = eye(caster);
        Vec3 dir = look(caster);
        return around(caster, from, range, e -> {
            Vec3 to = e.getBoundingBox().getCenter().subtract(from);
            return to.normalize().dot(dir) >= cosHalfAngle;
        });
    }

    public static boolean hostile(LivingEntity e) {
        return e instanceof Enemy;
    }

    /** Things a spell may treat as a foe of the caster: hostiles, and mobs targeting the caster. */
    public static boolean foe(LivingEntity caster, LivingEntity e) {
        if (e == caster || e.isAlliedTo(caster)) return false;
        if (e instanceof Player) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && m.getTarget() == caster;
    }

    public static boolean boss(Entity e) {
        return e instanceof EnderDragon || e instanceof EnderDragonPart || e instanceof WitherBoss || e instanceof Warden;
    }

    /** Whether control-type spells (control, banish, transform, snap) may affect this entity. */
    public static boolean controllable(Entity e) {
        return !boss(e) || MaConfig.AFFECT_BOSSES.get();
    }

    private static boolean dangerous(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
    }

    /** A safe standing spot: room for the entity, solid non-hazardous ground below. */
    public static boolean safe(ServerLevel level, Entity entity, Vec3 feet) {
        if (level.isOutsideBuildHeight(BlockPos.containing(feet))) return false;
        if (!level.noCollision(entity, entity.getDimensions(entity.getPose()).makeBoundingBox(feet))) return false;
        BlockPos at = BlockPos.containing(feet);
        BlockState here = level.getBlockState(at);
        BlockState below = level.getBlockState(at.below());
        if (dangerous(here) || dangerous(below)) return false;
        if (!here.getFluidState().isEmpty() && here.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return false;
        return below.isFaceSturdy(level, at.below(), Direction.UP) || !here.getFluidState().isEmpty();
    }

    /**
     * Nearest safe standing spot around {@code target}: searches the column first, then a widening ring. Returns empty
     * when nothing safe exists within {@code radius}.
     */
    public static Optional<Vec3> safeSpot(ServerLevel level, Entity entity, Vec3 target, int radius) {
        BlockPos base = BlockPos.containing(target);
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 0; dy <= 6; dy++) {
                        for (int sign : new int[] {1, -1}) {
                            if (dy == 0 && sign < 0) continue;
                            BlockPos p = base.offset(dx, dy * sign, dz);
                            Vec3 feet = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                            if (r == 0 && dy == 0) feet = new Vec3(target.x, p.getY(), target.z);
                            if (safe(level, entity, feet)) return Optional.of(feet);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Safe landing near the block the caster is looking at (on top of the hit face). */
    public static Optional<Vec3> safeLookSpot(ServerLevel level, LivingEntity caster, double range) {
        BlockHitResult hit = block(caster, range);
        Vec3 target;
        if (hit.getType() == HitResult.Type.MISS) {
            target = eye(caster).add(look(caster).scale(range));
        } else {
            BlockPos p = hit.getBlockPos().relative(hit.getDirection());
            target = new Vec3(hit.getLocation().x, p.getY(), hit.getLocation().z);
            if (hit.getDirection() == Direction.UP) target = new Vec3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        }
        return safeSpot(level, caster, target, 4);
    }

    /** Moves an entity within a level, resetting fall damage. */
    public static void moveTo(Entity e, Vec3 pos) {
        e.teleportTo(pos.x, pos.y, pos.z);
        e.resetFallDistance();
        e.hurtMarked = true;
    }

    /** Teleport across dimensions (or within one) keeping the entity's facing. */
    @Nullable
    public static Entity teleport(Entity e, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        e.resetFallDistance();
        if (e.level() == level) {
            e.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), yaw, pitch);
            return e;
        }
        boolean ok = e.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), yaw, pitch);
        if (!ok) return null;
        return e instanceof Player ? e : level.getEntity(e.getUUID());
    }

    public static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }
}

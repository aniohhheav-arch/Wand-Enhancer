package dev.infinitygauntlet.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** Everything an ability needs for one cast, plus server-side aiming helpers. */
public record AbilityContext(ServerPlayer player, ServerLevel level, ItemStack gauntlet, InteractionHand hand) {
    public boolean sneaking() {
        return player.isShiftKeyDown();
    }

    public Vec3 eye() {
        return player.getEyePosition();
    }

    public Vec3 look() {
        return player.getLookAngle();
    }

    /** Point in front of the gauntlet hand where cast effects originate. */
    public Vec3 handPos() {
        Vec3 look = look();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(hand == InteractionHand.MAIN_HAND ? 0.35 : -0.35);
        return eye().add(look.scale(0.7)).add(side).add(0, -0.3, 0);
    }

    public BlockHitResult pickBlock(double range) {
        Vec3 eye = eye();
        return level.clip(new ClipContext(eye, eye.add(look().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    /** The point the player is aiming at: the struck block face or the end of the ray. */
    public Vec3 aimPoint(double range) {
        BlockHitResult hit = pickBlock(range);
        return hit.getType() == HitResult.Type.MISS ? eye().add(look().scale(range)) : hit.getLocation();
    }

    @Nullable
    public LivingEntity pickLiving(double range) {
        Entity e = pickEntity(range, entity -> entity instanceof LivingEntity living && living.isAlive() && TargetRules.canTarget(player, living));
        return e instanceof LivingEntity living ? living : null;
    }

    @Nullable
    public Entity pickEntity(double range, Predicate<Entity> filter) {
        Vec3 eye = eye();
        BlockHitResult block = pickBlock(range);
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look().scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look().scale(reach)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e != player && !e.isSpectator() && e.isPickable() && filter.test(e), reach * reach);
        if (hit != null) {
            return hit.getEntity();
        }
        // forgiving aim assist: nearest valid entity within a narrow cone
        Entity best = null;
        double bestScore = 0.985;
        for (Entity e : level.getEntities(player, box, e -> !e.isSpectator() && filter.test(e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > reach || dist < 0.5) {
                continue;
            }
            double dot = to.scale(1 / dist).dot(look());
            if (dot > bestScore && player.hasLineOfSight(e)) {
                bestScore = dot;
                best = e;
            }
        }
        return best;
    }
}

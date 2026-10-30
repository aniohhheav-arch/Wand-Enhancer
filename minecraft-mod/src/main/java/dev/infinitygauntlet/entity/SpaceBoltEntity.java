package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/** A homing bolt of folded space fired out of a barrage portal. */
public class SpaceBoltEntity extends Projectile {
    private static final double SPEED = 1.4;
    private int targetId = -1;

    public SpaceBoltEntity(EntityType<? extends SpaceBoltEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public SpaceBoltEntity(Level level, Entity owner, Vec3 pos, Vec3 dir, @Nullable Entity target) {
        this(ModEntities.SPACE_BOLT.get(), level);
        setOwner(owner);
        setPos(pos);
        setDeltaMovement(dir.normalize().scale(SPEED));
        ProjectileUtil.rotateTowardsMovement(this, 1f);
        this.targetId = target == null ? -1 : target.getId();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount > 70) {
                discard();
                return;
            }
            Entity target = targetId < 0 ? null : level().getEntity(targetId);
            if (target != null && target.isAlive()) {
                Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED);
                setDeltaMovement(getDeltaMovement().lerp(want, 0.18).normalize().scale(SPEED));
            }
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            onHit(hit);
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5f);
        if (level().isClientSide) {
            ClientEffects.boltTick(this);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || target instanceof TimedEffectEntity || target instanceof RupturePortalEntity) {
            return false;
        }
        return !(getOwner() instanceof Player owner) || TargetRules.canTarget(owner, target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level() instanceof ServerLevel level && result.getEntity() instanceof LivingEntity living) {
            living.hurt(level.damageSources().indirectMagic(this, getOwner()), ServerConfig.BARRAGE_BOLT_DAMAGE.get().floatValue());
            living.knockback(0.4, -getDeltaMovement().x, -getDeltaMovement().z);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            Vec3 at = result.getLocation();
            Vfx.send(level, VfxType.SPARK_BURST, at, Vec3.ZERO, InfinityStone.SPACE.color(), InfinityStone.SPACE.highlight(), 0.9f, 12);
            Vfx.shockwave(level, at, result instanceof BlockHitResult b ? Vec3.atLowerCornerOf(b.getDirection().getNormal()) : getDeltaMovement().normalize(),
                    InfinityStone.SPACE, 1.6f, 8);
            Vfx.sound(level, at, ModSounds.SPACE_BOLT_IMPACT, 1f, 1f + random.nextFloat() * 0.3f);
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}

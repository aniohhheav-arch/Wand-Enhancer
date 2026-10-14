package com.stickyfingers.entity;

import com.stickyfingers.ability.AbilityType;
import com.stickyfingers.ability.Fx;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModEntities;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.SynchedEntityData;

/** A thrown zipper slider dragging a glowing seam behind it. */
public class ZipperProjectileEntity extends ThrowableProjectile {
    public ZipperProjectileEntity(EntityType<? extends ZipperProjectileEntity> type, Level level) {
        super(type, level);
    }

    public static ZipperProjectileEntity shoot(ServerPlayer owner, float speed) {
        ZipperProjectileEntity p = new ZipperProjectileEntity(ModEntities.ZIPPER_PROJECTILE.get(), owner.level());
        p.setOwner(owner);
        p.setPos(owner.getX(), owner.getEyeY() - 0.15, owner.getZ());
        p.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, speed, 0.2f);
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected double getDefaultGravity() { return 0.005; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                double f = i / 3.0;
                level().addParticle(ModParticles.SEAM_GLOW.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0, 0);
            }
            if (random.nextInt(2) == 0) level().addParticle(ModParticles.ZIPPER_SPARK.get(), getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > 60) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel sl) || !(getOwner() instanceof ServerPlayer owner)) return;
        Entity target = hit.getEntity();
        if (target == owner || target instanceof StandEntity) return;
        int duration = SFConfig.duration(AbilityType.ZIPPER_PROJECTILE);
        if (target instanceof LivingEntity le) {
            le.hurt(sl.damageSources().thrown(this, owner), (float) (5.0 * SFConfig.DAMAGE_MULTIPLIER.get()));
            if (!(le instanceof ServerPlayer) || SFConfig.PVP_EFFECTS.get()) {
                le.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, duration, 0, false, true));
            }
            ZipperEntity z = ZipperEntity.create(sl, owner, ZipperKind.MOB, le.position().add(0, le.getBbHeight() * 0.55, 0),
                    owner.position().subtract(le.position()).multiply(1, 0, 1), 0f, le.getBbWidth() + 0.6f, 0.5f)
                    .attachTo(le).life(duration).effectDuration(duration);
            sl.addFreshEntity(z);
            z.open();
            StandData.get(owner).mark(le.getId(), sl.getGameTime() + duration + 40);
        }
        impact(sl);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel sl) || !(getOwner() instanceof ServerPlayer owner)) return;
        Vec3 n = ZipperEntity.normalOf(hit.getDirection());
        ZipperEntity z = ZipperEntity.create(sl, owner, ZipperKind.SURFACE, hit.getLocation().add(n.scale(0.02)), n,
                random.nextFloat() * 180f, 1.2f, 0.6f).life(60);
        sl.addFreshEntity(z);
        z.open();
        impact(sl);
    }

    private void impact(ServerLevel sl) {
        Fx.sound(sl, position(), ModSounds.PROJECTILE_HIT.get(), 1f, 1f);
        Fx.particles(sl, ModParticles.PUNCH_IMPACT.get(), position(), 1, 0, 0);
        Fx.particles(sl, ModParticles.ZIPPER_TOOTH.get(), position(), 10, 0.2, 0.15);
        discard();
    }
}

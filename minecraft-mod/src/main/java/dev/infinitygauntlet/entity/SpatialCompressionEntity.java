package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A floating sphere that folds space inward: it drags entities and loose objects into itself while shrinking and
 * brightening, crushes anything caught at its core, then releases everything in a spatial shockwave.
 */
public class SpatialCompressionEntity extends TimedEffectEntity {
    public SpatialCompressionEntity(EntityType<? extends SpatialCompressionEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.SPACE_COMPRESSION, 3f, 1f);
        }
        ServerPlayer owner = owner();
        float life = life(0);
        double reach = visualRadius * 4;
        double strength = 0.06 + 0.16 * life;
        AABB box = new AABB(position(), position()).inflate(reach);
        for (Entity e : level.getEntities(this, box, e -> affected(owner, e))) {
            Vec3 to = position().subtract(e.getBoundingBox().getCenter());
            double dist = to.length();
            if (dist > reach || dist < 1.0E-3) {
                continue;
            }
            if (dist < 1.4) {
                e.setDeltaMovement(to.scale(0.3));
                if (tickCount % 10 == 0 && e instanceof LivingEntity living) {
                    living.hurt(level.damageSources().indirectMagic(this, owner), ServerConfig.COMPRESSION_CORE_DAMAGE.get().floatValue());
                }
            } else {
                e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(to.scale(strength * (1.2 - dist / reach) / dist)));
            }
            e.fallDistance = 0;
            e.hurtMarked = true;
        }
        if (tickCount % 7 == 0) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vfx.arc(level, position().add(dir.scale(reach * 0.8)), position(), InfinityStone.SPACE, 5);
        }
    }

    private static boolean affected(ServerPlayer owner, Entity e) {
        if (e instanceof TimedEffectEntity || e instanceof RupturePortalEntity || e.isSpectator()) {
            return false;
        }
        if (e instanceof ItemEntity || e instanceof Projectile) {
            return true;
        }
        return e instanceof LivingEntity && (owner == null || TargetRules.canTarget(owner, e));
    }

    @Override
    protected void onExpire(ServerLevel level) {
        ServerPlayer owner = owner();
        double reach = visualRadius * 4;
        for (Entity e : level.getEntities(this, new AABB(position(), position()).inflate(reach), e -> affected(owner, e))) {
            Vec3 away = e.getBoundingBox().getCenter().subtract(position());
            double dist = Math.max(0.5, away.length());
            double force = 2.2 * (1.1 - Math.min(1, dist / reach));
            e.setDeltaMovement(away.normalize().scale(force).add(0, 0.45, 0));
            e.hurtMarked = true;
            if (e instanceof LivingEntity living) {
                living.hurt(level.damageSources().indirectMagic(this, owner), ServerConfig.COMPRESSION_BURST_DAMAGE.get().floatValue());
            }
        }
        Vfx.send(level, VfxType.SPHERE_PULSE, position(), Vec3.ZERO, 0x2A6EFF, 0xDDF6FF, (float) reach * 1.3f, 16);
        Vfx.shockwave(level, position(), new Vec3(0, 1, 0), InfinityStone.SPACE, (float) reach * 1.6f, 18);
        Vfx.shockwave(level, position(), new Vec3(0.4, 1, 0.2).normalize(), InfinityStone.SPACE, (float) reach * 1.2f, 14);
        Vfx.send(level, VfxType.SPARK_BURST, position(), Vec3.ZERO, 0x2A6EFF, 0x9FE8FF, (float) reach * 0.6f, 20);
        Vfx.sound(level, position(), ModSounds.SPACE_SHOCKWAVE, 4f, 0.8f);
        Vfx.camera(level, position(), 40, 1.1f, 0, 0, 0xBFEFFF, 0.35f, 8);
    }

    @Override
    protected void clientTick() {
        ClientEffects.compressionTick(this);
    }

    /** Current sphere radius: shrinks to a hot point over its lifetime. */
    public float sphereRadius(float partialTick) {
        float life = life(partialTick);
        float grow = Math.min(1f, (tickCount + partialTick) / 8f);
        return visualRadius * grow * (1f - 0.82f * life * life);
    }
}

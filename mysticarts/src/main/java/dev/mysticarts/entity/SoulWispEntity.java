package dev.mysticarts.entity;

import dev.mysticarts.power.Aim;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.fx.Fx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A soul wisp that orbits its summoner and fires homing soul bolts at nearby foes. */
public class SoulWispEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SoulWispEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(SoulWispEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SoulWispEntity.class, EntityDataSerializers.INT);

    public SoulWispEntity(EntityType<? extends SoulWispEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static void summon(ServerLevel level, LivingEntity owner, int slot, int life) {
        SoulWispEntity w = new SoulWispEntity(MaEntities.SOUL_WISP.get(), level);
        w.entityData.set(OWNER, owner.getId());
        w.entityData.set(SLOT, slot);
        w.entityData.set(LIFE, life);
        w.moveTo(owner.getX(), owner.getY() + 2, owner.getZ(), 0, 0);
        level.addFreshEntity(w);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(SLOT, 0);
        builder.define(LIFE, 400);
    }

    public int slot() {
        return entityData.get(SLOT);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = level().getEntity(entityData.get(OWNER));
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        double a = (tickCount + slot() * 40) * 0.09;
        Vec3 want = owner.position().add(Math.cos(a) * 1.6, owner.getBbHeight() + 0.4 + Math.sin(tickCount * 0.12 + slot()) * 0.25, Math.sin(a) * 1.6);
        setPos(position().lerp(want, 0.35));
        if (level().isClientSide) {
            level().addParticle(MaParticles.MOTE.get().with(0xFF7A12, 0.25f, 18), getX(), getY(), getZ(), 0, 0.01, 0);
            return;
        }
        int life = entityData.get(LIFE) - 1;
        entityData.set(LIFE, life);
        if (life <= 0) {
            discard();
            return;
        }
        if (tickCount % 25 == slot() * 8 % 25 && owner instanceof LivingEntity lo) {
            LivingEntity target = null;
            double best = 16 * 16;
            for (LivingEntity e : Aim.around(lo, position(), 16, e -> Aim.foe(lo, e))) {
                double d = e.distanceToSqr(this);
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
            if (target != null) {
                Vec3 dir = target.getBoundingBox().getCenter().subtract(position()).normalize();
                MysticBoltEntity.shoot((ServerLevel) level(), lo, position(), dir, MysticBoltEntity.WISP, 0xFF7A12, 0.25f, 1.2f, 3.5f).homing(target).life(60);
                Fx.sound((ServerLevel) level(), position(), MaSounds.WISP_CHIME.get(), 0.5f, 1.4f);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}

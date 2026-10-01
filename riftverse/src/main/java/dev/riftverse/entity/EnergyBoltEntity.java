package dev.riftverse.entity;

import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A coloured bolt of pure energy: drone lasers, wraith rift-bolts, warden barrages, rift-blade waves. */
public class EnergyBoltEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(EnergyBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(EnergyBoltEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 4f;
    private int maxAge = 80;

    public EnergyBoltEntity(EntityType<? extends EnergyBoltEntity> type, Level level) {
        super(type, level);
    }

    public static EnergyBoltEntity create(Level level, LivingEntity shooter, int color, float size, float damage) {
        EnergyBoltEntity bolt = new EnergyBoltEntity(RvEntities.ENERGY_BOLT.get(), level);
        bolt.setOwner(shooter);
        bolt.setPos(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());
        bolt.entityData.set(COLOR, color);
        bolt.entityData.set(SIZE, size);
        bolt.damage = damage;
        return bolt;
    }

    public EnergyBoltEntity maxAge(int ticks) {
        this.maxAge = ticks;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xFF2BD6);
        builder.define(SIZE, 0.4f);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) level().addParticle(RvParticles.SPARK.get().with(color(), size() * 0.5f, 8), getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > maxAge) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner != null && (target == owner || target.isAlliedTo(owner))) return false;
        if (owner != null && target.getType() == owner.getType()) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        target.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), damage);
        burst();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        burst();
    }

    private void burst() {
        if (level() instanceof ServerLevel server) {
            Vec3 p = position();
            server.sendParticles(RvParticles.SPARK.get().with(color(), size(), 12), p.x, p.y, p.z, 12, 0.1, 0.1, 0.1, 0.2);
            server.sendParticles(RvParticles.RING.get().with(color(), size() * 3f, 10), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            server.playSound(null, p.x, p.y, p.z, RvSounds.ENERGY_HIT.get(), SoundSource.HOSTILE, 0.6f, 1.2f + random.nextFloat() * 0.3f);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("color", color());
        tag.putFloat("size", size());
        tag.putFloat("damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(COLOR, tag.getInt("color"));
        entityData.set(SIZE, tag.getFloat("size"));
        damage = tag.getFloat("damage");
    }
}

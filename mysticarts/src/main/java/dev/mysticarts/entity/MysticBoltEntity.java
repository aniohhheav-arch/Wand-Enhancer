package dev.mysticarts.entity;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Blast;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Every spell projectile: eldritch blasts, Power Stone blasts and orbs, homing soul bolts, the spectral projection. */
public class MysticBoltEntity extends ThrowableProjectile {
    public static final int MYSTIC = 0;
    public static final int MYSTIC_CHARGED = 1;
    public static final int POWER = 2;
    public static final int POWER_ORB = 3;
    public static final int SOUL = 4;
    public static final int SOUL_PROJECTION = 5;
    public static final int WISP = 6;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(MysticBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MysticBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MysticBoltEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 5f;
    private float blastRadius;
    private boolean terrain;
    private int maxAge = 80;
    private int homing = -1;
    private final Set<Integer> pierced = new HashSet<>();

    public MysticBoltEntity(EntityType<? extends MysticBoltEntity> type, Level level) {
        super(type, level);
    }

    public static MysticBoltEntity shoot(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, int kind, int color, float size, float speed, float damage) {
        MysticBoltEntity b = new MysticBoltEntity(MaEntities.MYSTIC_BOLT.get(), level);
        b.setOwner(shooter);
        b.setPos(from.x, from.y, from.z);
        b.entityData.set(KIND, kind);
        b.entityData.set(COLOR, color);
        b.entityData.set(SIZE, size);
        b.damage = damage;
        b.shoot(dir.x, dir.y, dir.z, speed, 0f);
        level.addFreshEntity(b);
        return b;
    }

    public MysticBoltEntity blast(float radius, boolean terrain) {
        this.blastRadius = radius;
        this.terrain = terrain;
        return this;
    }

    public MysticBoltEntity homing(Entity target) {
        this.homing = target.getId();
        return this;
    }

    public MysticBoltEntity life(int ticks) {
        this.maxAge = ticks;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, MYSTIC);
        builder.define(COLOR, 0xFF9A2E);
        builder.define(SIZE, 0.4f);
    }

    public int kind() {
        return entityData.get(KIND);
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
        if (!level().isClientSide && homing >= 0 && tickCount > 4) {
            Entity t = level().getEntity(homing);
            if (t != null && t.isAlive()) {
                Vec3 want = t.getBoundingBox().getCenter().subtract(position()).normalize();
                Vec3 v = getDeltaMovement();
                double speed = v.length();
                setDeltaMovement(v.normalize().scale(0.75).add(want.scale(0.25)).normalize().scale(speed));
            }
        }
        super.tick();
        if (level().isClientSide) {
            int k = kind();
            int n = k == POWER_ORB || k == MYSTIC_CHARGED ? 3 : 1;
            for (int i = 0; i < n; i++) {
                level().addParticle((k == SOUL || k == WISP || k == SOUL_PROJECTION ? MaParticles.MOTE : MaParticles.SPARK).get().with(color(), size() * 0.6f, 10),
                        getX() + (random.nextDouble() - 0.5) * size(), getY() + (random.nextDouble() - 0.5) * size(), getZ() + (random.nextDouble() - 0.5) * size(),
                        -getDeltaMovement().x * 0.1, -getDeltaMovement().y * 0.1, -getDeltaMovement().z * 0.1);
            }
        } else if (tickCount > maxAge) {
            if (blastRadius > 0) detonate(position());
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner != null && (target == owner || target.isAlliedTo(owner))) return false;
        if (target instanceof MysticBoltEntity || pierced.contains(target.getId())) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel server)) return;
        Entity owner = getOwner();
        Entity target = hit.getEntity();
        float dealt = damage;
        boolean hurt = target.hurt(damageSources().indirectMagic(this, owner), dealt);
        if (target instanceof LivingEntity living) {
            Vec3 push = getDeltaMovement().normalize().scale(kind() == POWER || kind() == MYSTIC_CHARGED ? 1.0 : 0.4);
            living.push(push.x, 0.15, push.z);
            if (hurt && (kind() == SOUL || kind() == WISP) && owner instanceof LivingEntity o) o.heal(dealt * 0.35f);
        }
        if (kind() == SOUL_PROJECTION) {
            pierced.add(target.getId());
            Fx.send(server, FxKind.SOUL_RIP, position(), Vec3.ZERO, color(), 0, 0, target.getId());
            return;
        }
        if (blastRadius > 0) detonate(hit.getLocation());
        else Fx.send(server, FxKind.IMPACT, hit.getLocation(), Vec3.ZERO, color(), size() * 2f, 0, -1);
        Fx.sound(server, position(), MaSounds.BLAST_IMPACT.get(), 0.8f, 1.1f);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel server)) return;
        if (blastRadius > 0) detonate(hit.getLocation());
        else Fx.send(server, FxKind.IMPACT, hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()), color(), size() * 2f, 0, -1);
        Fx.sound(server, position(), MaSounds.BLAST_IMPACT.get(), 0.7f, 1.2f);
        discard();
    }

    @Override
    protected void onHit(HitResult result) {
        if (kind() == SOUL_PROJECTION && result.getType() == HitResult.Type.BLOCK && tickCount < maxAge) {
            // the projection drifts through walls like a ghost
            return;
        }
        super.onHit(result);
    }

    private void detonate(Vec3 at) {
        Blast.detonate((ServerLevel) level(), getOwner(), at, blastRadius, damage, 1.2f, color(), terrain);
        discard();
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("kind", kind());
        tag.putInt("color", color());
        tag.putFloat("size", size());
        tag.putFloat("damage", damage);
        tag.putFloat("blast", blastRadius);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("kind"));
        entityData.set(COLOR, tag.getInt("color"));
        entityData.set(SIZE, tag.getFloat("size"));
        damage = tag.getFloat("damage");
        blastRadius = tag.getFloat("blast");
    }

    /** Players should be able to see where bolts come from even at range. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 96 * 96;
    }
}

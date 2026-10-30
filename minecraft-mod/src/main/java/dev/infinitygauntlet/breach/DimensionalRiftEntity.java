package dev.infinitygauntlet.breach;

import dev.infinitygauntlet.client.breach.BreachClientEffects;
import dev.infinitygauntlet.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * A tear in reality. The breach rift clings to its target (it follows the target's centre every tick) and leads
 * into the owner's observation chamber; the return rift hangs in the chamber and leads back. Only the owner passes
 * through: walking into the rift's core is the transition.
 */
public class DimensionalRiftEntity extends Entity implements GeoEntity {
    public static final byte OPENING = 0, OPEN = 1, CLOSING = 2;
    public static final int OPEN_TICKS = 32, CLOSE_TICKS = 20;

    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(DimensionalRiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETURN = SynchedEntityData.defineId(DimensionalRiftEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(DimensionalRiftEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DimensionalRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DimensionalRiftEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlay("animation.dimensional_rift.open")
            .thenLoop("animation.dimensional_rift.idle");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.dimensional_rift.idle");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlay("animation.dimensional_rift.close");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int phaseAge;

    public DimensionalRiftEntity(EntityType<? extends DimensionalRiftEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static DimensionalRiftEntity breach(ServerLevel level, Entity target, ServerPlayer owner) {
        DimensionalRiftEntity rift = new DimensionalRiftEntity(ModEntities.DIMENSIONAL_RIFT.get(), level);
        rift.entityData.set(TARGET, target.getId());
        rift.entityData.set(OWNER, Optional.of(owner.getUUID()));
        rift.entityData.set(RADIUS, radiusFor(target));
        Vec3 c = target.getBoundingBox().getCenter();
        rift.moveTo(c.x, c.y, c.z);
        level.addFreshEntity(rift);
        return rift;
    }

    public static DimensionalRiftEntity returning(ServerLevel level, Vec3 at, UUID owner) {
        DimensionalRiftEntity rift = new DimensionalRiftEntity(ModEntities.DIMENSIONAL_RIFT.get(), level);
        rift.entityData.set(RETURN, true);
        rift.entityData.set(OWNER, Optional.of(owner));
        rift.entityData.set(RADIUS, 1.5f);
        rift.moveTo(at.x, at.y, at.z);
        level.addFreshEntity(rift);
        return rift;
    }

    public static float radiusFor(Entity target) {
        return Math.max(1.3f, Math.max(target.getBbHeight(), target.getBbWidth()) * 0.75f + 0.55f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET, -1);
        builder.define(RETURN, false);
        builder.define(PHASE, OPENING);
        builder.define(RADIUS, 1.5f);
        builder.define(OWNER, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key)) {
            phaseAge = 0;
        }
    }

    public boolean isReturn() {
        return entityData.get(RETURN);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public byte phase() {
        return entityData.get(PHASE);
    }

    public int phaseAge() {
        return phaseAge;
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    @Nullable
    public Entity target() {
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    public float openProgress(float pt) {
        return phase() == OPENING ? Math.min(1f, (phaseAge + pt) / OPEN_TICKS) : 1f;
    }

    public float closeProgress(float pt) {
        return phase() == CLOSING ? Math.min(1f, (phaseAge + pt) / CLOSE_TICKS) : 0f;
    }

    public void startClosing() {
        if (phase() != CLOSING) {
            entityData.set(PHASE, CLOSING);
            phaseAge = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        phaseAge++;
        Entity target = target();
        if (target != null && target.isAlive() && phase() != CLOSING) {
            // cling to the target: snap the centre onto it on both sides so the tear never lags behind
            Vec3 c = target.getBoundingBox().getCenter();
            setPos(c.x, c.y, c.z);
            float r = radiusFor(target);
            if (!level().isClientSide && Math.abs(r - radius()) > 0.05f) {
                entityData.set(RADIUS, r);
            }
        }
        if (level().isClientSide) {
            BreachClientEffects.riftTick(this);
            return;
        }
        if (phase() == OPENING && phaseAge >= OPEN_TICKS) {
            entityData.set(PHASE, OPEN);
            phaseAge = 0;
        } else if (phase() == CLOSING) {
            if (phaseAge >= CLOSE_TICKS) {
                discard();
            }
            return;
        }
        if (phase() == OPEN && tickCount % 2 == 0) {
            BreachManager.riftTouch(this);
        }
    }

    /** Whether {@code e} has stepped into the rift's core. */
    public boolean isInside(Entity e) {
        return e.getBoundingBox().getCenter().distanceTo(position()) < radius() * 0.8;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(radius() * 2.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> switch (phase()) {
            case OPENING -> state.setAndContinue(OPEN_ANIM);
            case CLOSING -> state.setAndContinue(CLOSE_ANIM);
            default -> state.setAndContinue(IDLE_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }
}

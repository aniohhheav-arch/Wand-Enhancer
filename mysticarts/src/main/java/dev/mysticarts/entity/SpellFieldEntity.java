package dev.mysticarts.entity;

import dev.mysticarts.power.spells.Fields;
import dev.mysticarts.registry.MaEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A spell's area of effect: time fields, gravity wells, rifts, domes, ultimates. Behaviour lives in {@link Fields}
 * (server) and the field renderer (client); this entity only carries the shared state.
 */
public class SpellFieldEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FOLLOW = SynchedEntityData.defineId(SpellFieldEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable public UUID ownerUuid;
    @Nullable public Vec3 target;
    public float damage;
    public final Set<Integer> affected = new HashSet<>();
    private boolean ended;

    public SpellFieldEntity(EntityType<? extends SpellFieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static SpellFieldEntity spawn(ServerLevel level, @Nullable Entity owner, Vec3 pos, int kind, float radius, int duration, int color) {
        SpellFieldEntity f = new SpellFieldEntity(MaEntities.SPELL_FIELD.get(), level);
        f.entityData.set(KIND, kind);
        f.entityData.set(RADIUS, radius);
        f.entityData.set(DURATION, duration);
        f.entityData.set(COLOR, color);
        if (owner != null) {
            f.ownerUuid = owner.getUUID();
            f.entityData.set(OWNER, owner.getId());
        }
        f.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addFreshEntity(f);
        return f;
    }

    public SpellFieldEntity follow() {
        entityData.set(FOLLOW, true);
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(COLOR, 0xFFFFFF);
        builder.define(RADIUS, 4f);
        builder.define(DURATION, 100);
        builder.define(AGE, 0);
        builder.define(OWNER, -1);
        builder.define(FOLLOW, false);
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int duration() {
        return entityData.get(DURATION);
    }

    public int age() {
        return entityData.get(AGE);
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    public boolean follows() {
        return entityData.get(FOLLOW);
    }

    /** 0..1 fade in over the first 10 ticks and out over the last 10. */
    public float strength(float partial) {
        float a = age() + partial;
        return Mth.clamp(Math.min(a / 10f, (duration() - a) / 10f), 0f, 1f);
    }

    public float progress(float partial) {
        return Mth.clamp((age() + partial) / duration(), 0f, 1f);
    }

    @Nullable
    public Entity owner() {
        int id = ownerId();
        return id < 0 ? null : level().getEntity(id);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = owner();
        if (follows() && owner != null) setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ());
        if (level().isClientSide) return;
        int age = age() + 1;
        entityData.set(AGE, age);
        ServerLevel level = (ServerLevel) level();
        if (age >= duration()) {
            ended = true;
            Fields.end(this, level, owner);
            discard();
            return;
        }
        Fields.tick(this, level, owner, age);
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
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 192 * 192;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !ended) {
            ended = true;
            Fields.end(this, (ServerLevel) level(), owner());
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}

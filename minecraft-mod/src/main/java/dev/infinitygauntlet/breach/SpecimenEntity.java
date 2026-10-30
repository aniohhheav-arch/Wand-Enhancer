package dev.infinitygauntlet.breach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The target's representation in the chamber: a containment unit (GeckoLib) holding a live mirror of the real
 * target. The server copies the target's type, pose, movement, health, size and every active manipulation into
 * this entity's synced data each tick; clients render the target's actual model from it.
 */
public class SpecimenEntity extends Entity implements GeoEntity {
    public static final int FLAG_LEVITATING = 1, FLAG_BLIND = 2, FLAG_GLOWING = 4, FLAG_PACIFIED = 8, FLAG_FROZEN = 16, FLAG_PHASED = 32,
            FLAG_LOST = 64, FLAG_BABY = 128;

    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DIMENSION = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> BODY = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEAD = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_HEALTH = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(SpecimenEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.containment.idle");
    private static final RawAnimation LOST = RawAnimation.begin().thenPlay("animation.containment.lost");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SpecimenEntity(EntityType<? extends SpecimenEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, "minecraft:pig");
        builder.define(NAME, "");
        builder.define(DIMENSION, "");
        builder.define(TARGET_POS, BlockPos.ZERO);
        builder.define(BODY, 0f);
        builder.define(HEAD, 0f);
        builder.define(PITCH, 0f);
        builder.define(SPEED, 0f);
        builder.define(HEALTH, 20f);
        builder.define(MAX_HEALTH, 20f);
        builder.define(SCALE, 1f);
        builder.define(FLAGS, 0);
    }

    /** Copies the real target's state (server). */
    public void mirror(LivingEntity target, int manipulationFlags) {
        entityData.set(TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        entityData.set(NAME, target.getDisplayName().getString());
        entityData.set(DIMENSION, target.level().dimension().location().toString());
        entityData.set(TARGET_POS, target.blockPosition());
        entityData.set(BODY, target.yBodyRot);
        entityData.set(HEAD, target.yHeadRot);
        entityData.set(PITCH, target.getXRot());
        double dx = target.getX() - target.xo, dz = target.getZ() - target.zo;
        entityData.set(SPEED, (float) Math.sqrt(dx * dx + dz * dz));
        entityData.set(HEALTH, target.getHealth());
        entityData.set(MAX_HEALTH, target.getMaxHealth());
        entityData.set(SCALE, target.getScale());
        entityData.set(FLAGS, manipulationFlags | (target.isBaby() ? FLAG_BABY : 0));
    }

    public void markLost() {
        entityData.set(FLAGS, entityData.get(FLAGS) | FLAG_LOST);
    }

    public EntityType<?> targetType() {
        return BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse(entityData.get(TYPE)));
    }

    public String targetName() {
        return entityData.get(NAME);
    }

    public String targetDimension() {
        return entityData.get(DIMENSION);
    }

    public BlockPos targetPos() {
        return entityData.get(TARGET_POS);
    }

    public float bodyYaw() {
        return entityData.get(BODY);
    }

    public float headYaw() {
        return entityData.get(HEAD);
    }

    public float pitch() {
        return entityData.get(PITCH);
    }

    public float speed() {
        return entityData.get(SPEED);
    }

    public float health() {
        return entityData.get(HEALTH);
    }

    public float maxHealth() {
        return entityData.get(MAX_HEALTH);
    }

    public float targetScale() {
        return entityData.get(SCALE);
    }

    public int flags() {
        return entityData.get(FLAGS);
    }

    public boolean has(int flag) {
        return (flags() & flag) != 0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(has(FLAG_LOST) ? LOST : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(6, 4, 6);
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
}

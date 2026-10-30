package dev.infinitygauntlet.entity.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A head knocked clean off by a Power Stone finisher. Purely cosmetic: it arcs away spinning, bounces and rolls to a
 * stop, then fades. It is never saved and nothing can interact with it.
 */
public class SeveredHeadEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> HEAD = SynchedEntityData.defineId(SeveredHeadEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SeveredHeadEntity.class, EntityDataSerializers.FLOAT);
    public static final int LIFETIME = 160;

    /** Client-side tumble state. */
    public float spin;
    public float spinO;
    public float spinSpeed = 25f;

    public SeveredHeadEntity(EntityType<? extends SeveredHeadEntity> type, Level level) {
        super(type, level);
    }

    public void setup(ItemStack head, float bodyWidth) {
        entityData.set(HEAD, head.copyWithCount(1));
        entityData.set(SIZE, Math.max(0.5f, Math.min(1.2f, bodyWidth / 0.6f)));
    }

    public ItemStack head() {
        return entityData.get(HEAD);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HEAD, ItemStack.EMPTY);
        builder.define(SIZE, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
            return;
        }
        Vec3 v = getDeltaMovement().add(0, -0.06, 0);
        boolean wasOnGround = onGround();
        move(MoverType.SELF, v);
        if (onGround()) {
            if (!wasOnGround && v.y < -0.2) {
                v = new Vec3(v.x * 0.7, -v.y * 0.4, v.z * 0.7); // bounce
            } else {
                v = new Vec3(v.x * 0.8, 0, v.z * 0.8); // roll to a stop
            }
        } else if (horizontalCollision) {
            v = new Vec3(-v.x * 0.4, v.y, -v.z * 0.4);
        }
        setDeltaMovement(v.scale(0.98));
        if (level().isClientSide) {
            spinO = spin;
            spinSpeed = onGround() ? (float) v.horizontalDistance() * 120f : spinSpeed;
            spin += spinSpeed;
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
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}

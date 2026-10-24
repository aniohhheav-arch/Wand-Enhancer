package dev.telephoneorder.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A fully animated GeckoLib prop used by the delivery sequences (rift, containment case, crate, drone, pod, container).
 * The server drives position / scale / triggered animations; every client just renders.
 */
public class DeliveryActor extends Entity implements GeoEntity {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(DeliveryActor.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(DeliveryActor.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(DeliveryActor.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float shownScale = 0.01f;
    private float prevShown = 0.01f;
    private int age;

    public DeliveryActor(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, "crate");
        b.define(SCALE, 1f);
        b.define(YAW, 0f);
    }

    public String kind() { return entityData.get(KIND); }
    public void setKind(String k) { entityData.set(KIND, k); }
    public float targetScale() { return entityData.get(SCALE); }
    public void setTargetScale(float s) { entityData.set(SCALE, s); }
    public float modelYaw() { return entityData.get(YAW); }
    public void setModelYaw(float y) { entityData.set(YAW, y); }

    /** Smoothly interpolated render scale. */
    public float shownScale(float partial) { return prevShown + (shownScale - prevShown) * partial; }

    @Override
    public void tick() {
        super.tick();
        prevShown = shownScale;
        float t = targetScale();
        shownScale += (t - shownScale) * (t > shownScale ? 0.28f : 0.4f);
        if (Math.abs(t - shownScale) < 0.002f) shownScale = t;
        if (!level().isClientSide() && ++age > 20 * 60 * 6) discard();     // safety net; sequences kill their props long before
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override protected void readAdditionalSaveData(ValueInput in) { }
    @Override protected void addAdditionalSaveData(ValueOutput out) { }

    // ---- GeckoLib ----
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<DeliveryActor>("main", 3, test -> {
            String k = kind();
            RawAnimation a = switch (k) {
                case "rift" -> RawAnimation.begin().thenPlay("animation.rift.open").thenLoop("animation.rift.idle");
                case "drone" -> RawAnimation.begin().thenLoop("animation.drone.fly");
                default -> RawAnimation.begin().thenLoop("animation." + k + ".idle");
            };
            return test.setAndContinue(a);
        })
                .triggerableAnim("rift_close", RawAnimation.begin().thenPlay("animation.rift.close"))
                .triggerableAnim("case_open", RawAnimation.begin().thenPlay("animation.case.open"))
                .triggerableAnim("crate_open", RawAnimation.begin().thenPlay("animation.crate.open"))
                .triggerableAnim("pod_open", RawAnimation.begin().thenPlay("animation.pod.open"))
                .triggerableAnim("container_open", RawAnimation.begin().thenPlay("animation.container.open")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

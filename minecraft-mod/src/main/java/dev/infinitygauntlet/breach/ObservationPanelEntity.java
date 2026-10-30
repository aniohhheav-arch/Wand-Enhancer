package dev.infinitygauntlet.breach;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The floating control panel in the observation chamber. Its buttons are drawn and hit-tested through {@link PanelLayout}. */
public class ObservationPanelEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> SPECIMEN = SynchedEntityData.defineId(ObservationPanelEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation DEPLOY = RawAnimation.begin().thenPlay("animation.observation_panel.deploy")
            .thenLoop("animation.observation_panel.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ObservationPanelEntity(EntityType<? extends ObservationPanelEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void bindSpecimen(SpecimenEntity specimen) {
        entityData.set(SPECIMEN, specimen.getId());
    }

    public int specimenId() {
        return entityData.get(SPECIMEN);
    }

    /** The panel faces along its yaw (toward the operator). */
    public PanelLayout layout(float pt) {
        return new PanelLayout(getPosition(pt).add(0, Math.sin((tickCount + pt) * 0.0785) * 0.03, 0), getYRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPECIMEN, -1);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(DEPLOY)));
        controllers.add(new AnimationController<>(this, "press", 0, state -> software.bernie.geckolib.animation.PlayState.STOP)
                .triggerableAnim("press", RawAnimation.begin().thenPlay("animation.observation_panel.press")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(2.5);
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

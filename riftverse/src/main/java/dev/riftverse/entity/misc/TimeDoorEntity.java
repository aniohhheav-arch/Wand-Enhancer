package dev.riftverse.entity.misc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The TSA time door opened by the Pruning Staff: a glowing orange frame whose door swings open, then folds away. */
public class TimeDoorEntity extends Entity implements GeoEntity {
    public static final int LIFE = 40;
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.time_door.open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("animation.time_door.close");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TimeDoorEntity(EntityType<? extends TimeDoorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) discard();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "door", 0, st -> st.setAndContinue(tickCount < LIFE - 12 ? OPEN : CLOSE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
    }
}

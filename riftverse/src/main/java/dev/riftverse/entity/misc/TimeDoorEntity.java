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
    public static final int LIFE = 160;
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.time_door.open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("animation.time_door.close");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.phys.Vec3 exit;

    /** Where stepping through this door leads (null for an exit-only door). */
    public void setExit(net.minecraft.world.phys.Vec3 exit) {
        this.exit = exit;
    }

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
        if (level().isClientSide) return;
        if (tickCount > LIFE) {
            discard();
            return;
        }
        if (exit == null || tickCount < 8 || tickCount > LIFE - 12) return;
        for (var p : level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, getBoundingBox().inflate(0.15))) {
            if (p.getPersistentData().getLong("riftverse_door_cd") > level().getGameTime()) continue;
            p.getPersistentData().putLong("riftverse_door_cd", level().getGameTime() + 20);
            p.teleportTo(exit.x, exit.y, exit.z);
            p.fallDistance = 0;
            var l = (net.minecraft.server.level.ServerLevel) level();
            l.sendParticles(dev.riftverse.registry.RvParticles.RING.get().with(0xFF8A20, 2f, 14), exit.x, exit.y + 1, exit.z, 1, 0, 0, 0, 0);
            l.playSound(null, net.minecraft.core.BlockPos.containing(exit), net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.6f);
        }
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

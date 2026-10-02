package dev.riftverse.entity.vehicle;

import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.temporal.TemporalManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A drivable time machine. Ride it, sneak-use it to set the time circuits, arm them, and hit 88 mph: the car leaves
 * twin fire trails, vanishes in a flash and its driver arrives in the destination year.
 */
public class DeLoreanEntity extends Entity implements software.bernie.geckolib.animatable.GeoEntity {
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        software.bernie.geckolib.animation.RawAnimation open = software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.delorean.doors_open");
        software.bernie.geckolib.animation.RawAnimation close = software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.delorean.doors_close");
        software.bernie.geckolib.animation.RawAnimation drive = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.delorean.drive");
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "wheels", 0, st -> {
            float mph = Math.max(mph(), (float) getDeltaMovement().horizontalDistance() * MPH);
            if (mph < 1f) return software.bernie.geckolib.animation.PlayState.STOP;
            st.getController().setAnimationSpeed(Math.min(6.0, mph / 15.0));
            return st.setAndContinue(drive);
        }));
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "doors", 3, st -> st.setAndContinue(!isVehicle() && level().getNearestPlayer(this, 3.5) != null ? open : close)));
    }

    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(DeLoreanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(DeLoreanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(DeLoreanEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> JUMP = SynchedEntityData.defineId(DeLoreanEntity.class, EntityDataSerializers.INT);
    /** Displayed mph per block/tick. 88 mph = 0.88 blocks per tick. */
    public static final float MPH = 100f;
    private Vec3 lastServerPos;
    private float speedAvg;
    private int lerpSteps;
    private double lx, ly, lz, lyaw;

    public DeLoreanEntity(EntityType<? extends DeLoreanEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TARGET, 1955);
        b.define(ARMED, false);
        b.define(SPEED, 0f);
        b.define(JUMP, 0);
    }

    public int targetYear() {
        return entityData.get(TARGET);
    }

    public boolean armed() {
        return entityData.get(ARMED);
    }

    public float mph() {
        return entityData.get(SPEED) * MPH;
    }

    /** Ticks since the last jump flash (counting down from 40). */
    public int jump() {
        return entityData.get(JUMP);
    }

    public void setCircuits(int year, boolean armed) {
        entityData.set(TARGET, year);
        entityData.set(ARMED, armed);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new Payloads.Vehicle(getId(), Payloads.Vehicle.OPEN_CIRCUITS, targetYear()));
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide) player.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return true;
        if (source.getEntity() instanceof Player p && p.isShiftKeyDown()) {
            if (!p.isCreative()) spawnAtLocation(new ItemStack(RvItems.DELOREAN.get()));
            ejectPassengers();
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dims, float scale) {
        return new Vec3(0, 0.25, 0).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (getControllingPassenger() == null) return;
        // the driver's view turns with the car, like a boat
        float delta = Mth.wrapDegrees(getYRot() - yRotO);
        passenger.setYRot(passenger.getYRot() + delta);
        passenger.setYHeadRot(passenger.getYHeadRot() + delta);
        passenger.setYBodyRot(getYRot());
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x;
        ly = y;
        lz = z;
        lyaw = yRot;
        lerpSteps = 10;
    }

    @Override
    public void tick() {
        super.tick();
        if (jump() > 0 && !level().isClientSide) entityData.set(JUMP, jump() - 1);
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            drive();
            if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.08, 0));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(1, 0.98, 1));
        } else {
            if (lerpSteps > 0) {
                double f = 1.0 / lerpSteps;
                setPos(getX() + (lx - getX()) * f, getY() + (ly - getY()) * f, getZ() + (lz - getZ()) * f);
                setYRot(getYRot() + (float) Mth.wrapDegrees(lyaw - getYRot()) * (float) f);
                lerpSteps--;
            }
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) {
            clientFx();
            return;
        }
        serverSpeed();
    }

    private void drive() {
        LivingEntity d = getControllingPassenger();
        Vec3 v = getDeltaMovement();
        if (jump() > 0) {
            setDeltaMovement(0, v.y, 0);
            return;
        }
        double hs = v.horizontalDistance();
        float forward = d == null ? 0 : d.zza;
        float turn = d == null ? 0 : d.xxa;
        double speed = hs * Math.signum(v.x * -Mth.sin(getYRot() * Mth.DEG_TO_RAD) + v.z * Mth.cos(getYRot() * Mth.DEG_TO_RAD) + 1e-9);
        if (forward > 0) speed += 0.02;
        else if (forward < 0) speed -= speed > 0 ? 0.05 : 0.01;
        else speed *= 0.97;
        speed = Mth.clamp(speed, -0.25, 1.05);
        if (!onGround()) speed *= 0.995;
        setYRot(getYRot() - turn * (float) (3.5 * Math.min(1.0, Math.abs(speed) * 4 + 0.15)) * Math.signum((float) speed == 0 ? 1 : (float) speed));
        Vec3 dir = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
        if (d instanceof Player p && tickCount % 4 == 0) {
            float mph = (float) Math.abs(speed) * MPH;
            p.displayClientMessage(Component.literal(String.format("%3.0f MPH", mph)).withColor(mph >= 88 ? 0xFF6020 : 0xFFD040)
                    .append(Component.literal("   ⧗ " + TemporalManager.formatYear(targetYear()) + (armed() ? "  [ARMED]" : "  [standby]")).withColor(armed() ? 0xFF4040 : 0x80FF80)), true);
        }
        setOnGround(onGround());
        if (horizontalCollision) setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
        maxUpStep();
    }

    @Override
    public float maxUpStep() {
        return 1.1f;
    }

    private void clientFx() {
        if (!armed() || tickCount % 2 != 0) return;
        float s = entityData.get(SPEED);
        if (s * MPH > 60) {
            Vec3 back = new Vec3(Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
            Vec3 side = new Vec3(back.z, 0, -back.x).scale(0.8);
            for (int k = -1; k <= 1; k += 2) {
                Vec3 p = position().add(back.scale(2)).add(side.scale(k)).add(0, 0.6, 0);
                level().addParticle(RvParticles.STREAK.get().with(0x60C0FF, 0.8f, 10), p.x, p.y, p.z, 0, 0.02, 0);
            }
        }
    }

    private void serverSpeed() {
        Vec3 now = position();
        if (lastServerPos != null) {
            double d = now.subtract(lastServerPos).horizontalDistance();
            if (d > 5) d = 0; // a teleport, not driving
            speedAvg = speedAvg * 0.7f + (float) Math.min(d, 3) * 0.3f;
            entityData.set(SPEED, speedAvg);
        }
        lastServerPos = now;
        // burning tyre tracks once the car is armed and past 40 mph
        if (armed() && speedAvg * MPH > 40f && onGround()) {
            for (int k = -1; k <= 1; k += 2) dev.riftverse.temporal.FireTrails.add((ServerLevel) level(), wheelGround(k), 30);
        }
        if (armed() && speedAvg * MPH >= 88f && getControllingPassenger() instanceof ServerPlayer driver) timeJump(driver);
    }

    private Vec3 wheelGround(int side) {
        Vec3 back = new Vec3(Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        Vec3 lateral = new Vec3(back.z, 0, -back.x).scale(0.85 * side);
        return position().add(back.scale(1.4)).add(lateral);
    }

    private void timeJump(ServerPlayer driver) {
        ServerLevel level = (ServerLevel) level();
        entityData.set(ARMED, false);
        // twin fire trails burn on along the track the car just left
        Vec3 back = new Vec3(Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        for (int i = 0; i < 30; i++) {
            for (int k = -1; k <= 1; k += 2) dev.riftverse.temporal.FireTrails.add(level, wheelGround(k).add(back.scale(i * 0.7)), 100 - i);
        }
        level.sendParticles(RvParticles.RING.get().with(0x80D0FF, 5f, 14), getX(), getY() + 1, getZ(), 2, 0, 0, 0, 0);
        level.sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 1.5f, 14), getX(), getY() + 1, getZ(), 60, 1.5, 0.8, 1.5, 0.5);
        level.playSound(null, blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 3f, 1.8f);
        TemporalManager.jumpNow(driver, targetYear(), this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        setCircuits(t.getInt("target"), t.getBoolean("armed"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("target", targetYear());
        t.putBoolean("armed", armed());
    }
}

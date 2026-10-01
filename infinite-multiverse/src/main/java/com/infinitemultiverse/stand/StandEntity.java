package com.infinitemultiverse.stand;

import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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

/**
 * A manifested Stand. Never saved, never collides, cannot be hurt. Its position is driven every tick by the
 * server from its owner, and it disappears the moment its owner is gone, dead or in another dimension.
 */
public class StandEntity extends Entity implements GeoEntity {
    public static final String CONTROLLER = "main";
    public static final String TRIGGER_HEAVY = "heavy";
    public static final int FADE_TICKS = 8;

    private static final EntityDataAccessor<String> DATA_STAND_TYPE = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ENTITY = SynchedEntityData.defineId(StandEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.stand.idle");
    private static final RawAnimation BARRAGE = RawAnimation.begin().thenLoop("animation.stand.barrage");
    private static final RawAnimation GUARD = RawAnimation.begin().thenLoop("animation.stand.guard");
    private static final RawAnimation TIME_STOP = RawAnimation.begin().thenPlayAndHold("animation.stand.time_stop");
    private static final RawAnimation HEAVY = RawAnimation.begin().thenPlay("animation.stand.heavy");

    private static final int HEAVY_IMPACT_TICK = 7;
    private static final int HEAVY_LENGTH = 14;
    private static final double GUARD_CATCH_RADIUS = 4.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID ownerId;
    private int actionTicks;
    private int actionDuration;
    private int heavyTargetId = -1;
    private float heavyMultiplier = 1f;
    private int dismissTicks;
    private int clientFadeTicks;
    private float lookPitch;
    private float lookPitchO;

    public StandEntity(EntityType<? extends StandEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STAND_TYPE, "");
        builder.define(DATA_ACTION, StandAction.IDLE.ordinal());
        builder.define(DATA_OWNER_ENTITY, -1);
    }

    // ---- state ----

    public void setOwner(ServerPlayer owner) {
        this.ownerId = owner.getUUID();
        entityData.set(DATA_OWNER_ENTITY, owner.getId());
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    public void setStandType(StandType type) {
        entityData.set(DATA_STAND_TYPE, type.id().toString());
    }

    @Nullable
    public StandType standType() {
        ResourceLocation id = ResourceLocation.tryParse(entityData.get(DATA_STAND_TYPE));
        return id == null ? null : MultiverseRegistries.STAND_TYPES.get(id);
    }

    public StandAction action() {
        return StandAction.byId(entityData.get(DATA_ACTION));
    }

    /** True while the Stand can accept a new command (it is not mid-attack or leaving). */
    public boolean isReady() {
        StandAction action = action();
        return action == StandAction.IDLE;
    }

    public void startAction(StandAction action, int duration) {
        entityData.set(DATA_ACTION, action.ordinal());
        actionTicks = 0;
        actionDuration = duration;
    }

    public void startHeavy(LivingEntity target, float damageMultiplier) {
        heavyTargetId = target.getId();
        heavyMultiplier = damageMultiplier;
        startAction(StandAction.HEAVY, HEAVY_LENGTH);
        triggerAnim(CONTROLLER, TRIGGER_HEAVY);
    }

    public void beginDismiss() {
        startAction(StandAction.DISMISSING, FADE_TICKS);
        dismissTicks = FADE_TICKS;
    }

    public boolean isGuarding() {
        return action() == StandAction.GUARD;
    }

    /** 0..1 opacity: fades in after spawning and out while dismissing. Client side. */
    public float fade(float partialTick) {
        if (action() == StandAction.DISMISSING) {
            return Mth.clamp((FADE_TICKS - clientFadeTicks - partialTick) / FADE_TICKS, 0f, 1f);
        }
        return Mth.clamp((tickCount + partialTick) / FADE_TICKS, 0f, 1f);
    }

    // ---- ticking ----

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFadeTicks = action() == StandAction.DISMISSING ? clientFadeTicks + 1 : 0;
            followOwnerLook();
            return;
        }
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != level()) {
            discard();
            return;
        }
        if (dismissTicks > 0) {
            follow(owner);
            if (--dismissTicks == 0) {
                discard();
            }
            return;
        }

        StandAction action = action();
        if (action != StandAction.IDLE) {
            actionTicks++;
            switch (action) {
                case BARRAGE -> tickBarrage(owner);
                case HEAVY -> tickHeavy(owner);
                case GUARD -> tickGuard(owner);
                default -> {
                }
            }
            if (actionTicks >= actionDuration) {
                entityData.set(DATA_ACTION, StandAction.IDLE.ordinal());
            }
        }
        follow(owner);
    }

    @Nullable
    private ServerPlayer owner() {
        if (ownerId == null) {
            return null;
        }
        Player player = level().getPlayerByUUID(ownerId);
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private void follow(ServerPlayer owner) {
        float yaw = owner.getYHeadRot();
        double rad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
        Vec3 right = new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad));
        Vec3 anchor;
        if (action().isForward()) {
            // Ahead and slightly to the right so the Stand frames the target instead of blocking the view.
            double reach = action() == StandAction.GUARD ? 1.7 : 2.0;
            double lift = Mth.clamp(owner.getLookAngle().y, -0.6, 0.6) - 0.25;
            anchor = owner.position().add(forward.scale(reach)).add(right.scale(0.45)).add(0.0, lift, 0.0);
        } else {
            double bob = Math.sin(tickCount * 0.08) * 0.08;
            anchor = owner.position().add(forward.scale(-0.6)).add(right.scale(0.8)).add(0.0, 0.35 + bob, 0.0);
        }
        Vec3 next = position().lerp(anchor, tickCount <= 1 ? 1.0 : 0.45);
        setPos(next.x, next.y, next.z);
        setYRot(yaw);
        setXRot(owner.getXRot());
    }

    private void tickBarrage(ServerPlayer owner) {
        if (actionTicks % 2 != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Vec3 look = owner.getLookAngle();
        Vec3 strike = owner.getEyePosition().add(look.scale(2.2));
        AABB box = new AABB(strike, strike).inflate(1.4);
        float damage = MultiverseConfig.SERVER.barrageDamage.get().floatValue();
        DamageSource source = owner.damageSources().playerAttack(owner);
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> AbilityTargeting.isHostileTarget(owner, e))) {
            target.invulnerableTime = 0;
            if (target.hurt(source, damage)) {
                target.knockback(0.12, -look.x, -look.z);
                hit = true;
                MultiverseVfx.broadcast(level, VfxIds.STAND_PUNCH, target.getEyePosition().add(look.scale(-0.4)), look, 0.7f);
            }
        }
        MultiverseVfx.sound(level, strike, hit ? ModSounds.STAND_PUNCH : ModSounds.STAND_SWING, hit ? 0.55f : 0.35f, 1.0f);
    }

    private void tickHeavy(ServerPlayer owner) {
        if (actionTicks != HEAVY_IMPACT_TICK) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Entity entity = level.getEntity(heavyTargetId);
        double reach = MultiverseConfig.SERVER.standReach.get() + 1.5;
        if (!(entity instanceof LivingEntity target) || !AbilityTargeting.isHostileTarget(owner, target)
                || target.distanceToSqr(owner) > reach * reach) {
            MultiverseVfx.sound(level, position(), ModSounds.STAND_SWING, 0.8f, 0.7f);
            return;
        }
        Vec3 direction = target.position().subtract(owner.position()).multiply(1.0, 0.0, 1.0);
        if (direction.lengthSqr() < 1.0E-4) {
            direction = owner.getLookAngle().multiply(1.0, 0.0, 1.0);
        }
        direction = direction.normalize();
        target.invulnerableTime = 0;
        target.hurt(owner.damageSources().playerAttack(owner), MultiverseConfig.SERVER.precisionDamage.get().floatValue() * heavyMultiplier);
        target.knockback(MultiverseConfig.SERVER.precisionKnockback.get(), -direction.x, -direction.z);
        target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.4, 0.0));
        target.hurtMarked = true;
        MultiverseVfx.sound(level, target.position(), ModSounds.STAND_HEAVY, 1.0f, 1.0f);
        MultiverseVfx.broadcast(level, VfxIds.STAND_HEAVY, target.getEyePosition().subtract(direction.scale(0.5)), direction, 1f);
    }

    private void tickGuard(ServerPlayer owner) {
        ServerLevel level = (ServerLevel) level();
        Vec3 center = owner.getEyePosition();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(GUARD_CATCH_RADIUS),
                p -> p.getOwner() != owner)) {
            Vec3 toOwner = center.subtract(projectile.position());
            if (projectile.getDeltaMovement().dot(toOwner) <= 0.0) {
                continue;
            }
            Vec3 at = projectile.position();
            projectile.discard();
            MultiverseVfx.sound(level, at, ModSounds.STAND_CATCH, 0.9f, 1.0f);
            MultiverseVfx.broadcast(level, VfxIds.STAND_PUNCH, at, toOwner.normalize().scale(-1.0), 0.5f);
        }
    }

    /** Client side: mirror the owner's view every tick so the Stand turns and looks with its user. */
    private void followOwnerLook() {
        lookPitchO = lookPitch;
        if (level().getEntity(entityData.get(DATA_OWNER_ENTITY)) instanceof Player owner) {
            setYRot(owner.getYHeadRot());
            lookPitch = Mth.clamp(owner.getXRot(), -70f, 70f);
            if (tickCount <= 1) {
                yRotO = getYRot();
                lookPitchO = lookPitch;
            }
        }
    }

    /** Interpolated owner pitch in degrees, for the head bone. */
    public float lookPitch(float partialTick) {
        return Mth.lerp(partialTick, lookPitchO, lookPitch);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // Rotation is driven locally from the owner; only take the server position.
        setPos(x, y, z);
    }

    // ---- entity plumbing ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 3, state -> state.setAndContinue(switch (action()) {
            case BARRAGE -> BARRAGE;
            case GUARD -> GUARD;
            case TIME_STOP -> TIME_STOP;
            default -> IDLE;
        })).triggerableAnim(TRIGGER_HEAVY, HEAVY));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

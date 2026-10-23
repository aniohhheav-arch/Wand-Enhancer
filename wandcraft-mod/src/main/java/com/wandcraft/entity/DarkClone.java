package com.wandcraft.entity;

import com.wandcraft.Config;
import com.wandcraft.ModSounds;
import com.wandcraft.WandCraft;
import com.wandcraft.magic.Possession;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.ShadowFx;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Dark Clone: a shadow humanoid that rises from a smoke pool, studies its prey, stalks it along real paths (stepping
 * through brief phase flickers only along reachable ground, never through walls), reaches out with an elongated arm and
 * pours itself into the target to start a {@link Possession}. Fully server-driven; clients just animate the synced state.
 */
public class DarkClone extends PathfinderMob implements GeoEntity {
    public static final int SUMMON = 0, OBSERVE = 1, HUNT = 2, APPROACH = 3, REACH = 4, MERGE = 5, DISSOLVE = 6;

    private static final int SUMMON_TICKS = 40, OBSERVE_TICKS = 28, REACH_TICKS = 26, MERGE_TICKS = 24, DISSOLVE_TICKS = 46;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(DarkClone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DarkClone.class, EntityDataSerializers.OPTIONAL_UUID);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID targetId;
    private int stateTicks, life, lostTicks, stuck, unreachable, flickerCd, rumbleCd = 40, whisperCd = 70;
    private double range = 24;
    private Vec3 lastPos = Vec3.ZERO, mergeFrom = Vec3.ZERO;
    private boolean locked, ended;

    public DarkClone(EntityType<? extends DarkClone> type, Level level) {
        super(type, level);
        xpReward = 0;
        setInvulnerable(true);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation g) g.setCanOpenDoors(false);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Server: arm the clone against {@code target} for {@code durationTicks} (hunting time before it gives up). */
    public void begin(ServerPlayer owner, Mob target, int durationTicks, double range) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        this.targetId = target.getUUID();
        this.life = durationTicks;
        this.range = range;
        var follow = getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.min(128, range + 16));
        Possession.claim(owner.getUUID(), getUUID());
        Possession.claimTarget(targetId, getUUID());
    }

    public int state() { return entityData.get(STATE); }
    private void setState(int s) {
        entityData.set(STATE, s);
        stateTicks = 0;
    }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, SUMMON);
        b.define(OWNER, Optional.empty());
    }

    // ------------------------------------------------------------------------------------ animation
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> {
            RawAnimation a = switch (state()) {
                case SUMMON -> hold("materialize");
                case REACH -> hold("reach");
                case MERGE -> hold("merge");
                case DISSOLVE -> hold("dissolve");
                case APPROACH -> st.isMoving() ? loop("approach") : loop("observe");
                case HUNT -> st.isMoving() ? loop("hunt") : loop("observe");
                default -> loop("observe");
            };
            return st.setAndContinue(a);
        }));
        controllers.add(new AnimationController<>(this, "phase", 0, st -> PlayState.STOP)
                .triggerableAnim("phase", RawAnimation.begin().then("animation.dark_clone.phase", Animation.LoopType.PLAY_ONCE)));
    }

    private static RawAnimation hold(String n) { return RawAnimation.begin().then("animation.dark_clone." + n, Animation.LoopType.HOLD_ON_LAST_FRAME); }
    private static RawAnimation loop(String n) { return RawAnimation.begin().thenLoop("animation.dark_clone." + n); }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ------------------------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel l = (ServerLevel) level();
        if (ended) return;
        stateTicks++;
        if (tickCount == 1) {
            Fx.sound(l, position(), ModSounds.DC_SUMMON.get(), 1.5f, 1f);
            ShadowFx.puff(l, position().add(0, 0.3, 0), 30, 1.2);
        }
        Player owner = owner();
        if (owner == null || !owner.isAlive()) {
            if (!locked) {
                startDissolve();
                if (!(state() == DISSOLVE)) return;
            }
        }
        Mob target = target();
        int st = state();
        if (st != DISSOLVE && !locked && st > SUMMON && --life <= 0) {
            startDissolve();
            return;
        }
        if (st == SUMMON) {
            if (target != null) face(target, 6f);
            if (stateTicks == 8) ShadowFx.puff(l, position().add(0, 1.2, 0), 14, 0.5);
            if (stateTicks >= SUMMON_TICKS) setState(OBSERVE);
            return;
        }
        if (st == DISSOLVE) {
            dissolveTick(l);
            return;
        }
        // keep a valid target; look for a new one if it was lost
        if (!locked && (target == null || !Possession.valid(owner, target) || distanceTo(target) > range * 1.8 + 8)) {
            lostTicks++;
            if (lostTicks > 30) retarget(l, owner);
            if (state() == DISSOLVE) return;
            if (target == null) return;
        } else if (!locked) {
            lostTicks = 0;
        }
        if (target == null) return;
        ambientSounds(l);
        switch (st) {
            case OBSERVE -> {
                getNavigation().stop();
                face(target, 12f);
                if (stateTicks == 1) Fx.sound(l, position(), ModSounds.DC_WHISPER.get(), 0.8f, 0.9f);
                if (stateTicks >= OBSERVE_TICKS) setState(HUNT);
            }
            case HUNT, APPROACH -> hunt(l, target, st);
            case REACH -> reach(l, target);
            case MERGE -> merge(l, target);
            default -> {}
        }
    }

    private void hunt(ServerLevel l, Mob target, int st) {
        double dist = distanceTo(target);
        double flat = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (st == HUNT && dist < 8) setState(APPROACH);
        else if (st == APPROACH && dist > 11) setState(HUNT);
        if (flat <= 1.85 && Math.abs(target.getY() - getY()) < 2.2 && hasLineOfSight(target)) {
            getNavigation().stop();
            locked = true;
            Possession.lock(target, ownerId());
            mergeFrom = position();
            setState(REACH);
            Fx.sound(l, position(), ModSounds.DC_REACH.get(), 1.2f, 1f);
            return;
        }
        double speed = st == HUNT ? 1.35 : 0.8;
        if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, speed);
        else getNavigation().setSpeedModifier(speed);
        face(target, st == HUNT ? 20f : 30f);

        // stuck / unreachable handling: hop along the real path without crossing walls
        boolean moved = position().distanceToSqr(lastPos) > 0.0036;
        if (tickCount % 6 == 0) lastPos = position();
        if (getNavigation().isDone() && dist > 2.5 || (tickCount % 6 == 0 && !moved)) stuck++; else stuck = Math.max(0, stuck - 1);
        if (flickerCd > 0) flickerCd--;
        boolean showy = st == HUNT && dist > 9 && random.nextInt(70) == 0;
        if (flickerCd == 0 && (stuck >= 12 || showy)) phaseStep(l, target);
        if (stuck > 90 && getNavigation().isDone()) unreachable++;
        if (unreachable > 3) {
            unreachable = 0;
            retarget(l, owner());
        }
    }

    /** Short flicker a few path nodes ahead. Only walkable path nodes are used, so walls are never skipped. */
    private void phaseStep(ServerLevel l, Mob target) {
        Path path = getNavigation().createPath(target, 1);
        if (path == null || path.getNodeCount() < 2) return;
        int idx = Math.min(path.getNextNodeIndex() + 4, path.getNodeCount() - 1);
        Node n = path.getNode(idx);
        Vec3 to = new Vec3(n.x + 0.5, n.y, n.z + 0.5);
        if (to.distanceToSqr(position()) > 64) return;
        if (!l.noCollision(this, getBoundingBox().move(to.subtract(position())))) return;
        Vec3 from = position();
        ShadowFx.puff(l, from.add(0, 1, 0), 14, 0.4);
        Fx.sound(l, from, ModSounds.DC_PHASE.get(), 0.9f, 0.9f + random.nextFloat() * 0.3f);
        moveTo(to.x, to.y, to.z, getYRot(), 0);
        getNavigation().stop();
        ShadowFx.stream(l, from.add(0, 1, 0), to.add(0, 1, 0), 16, 0.5);
        ShadowFx.puff(l, to.add(0, 1, 0), 14, 0.4);
        triggerAnim("phase", "phase");
        flickerCd = 28;
        stuck = 0;
    }

    private void reach(ServerLevel l, Mob target) {
        getNavigation().stop();
        face(target, 40f);
        target.getNavigation().stop();
        Vec3 hand = handPos();
        if (stateTicks >= 8 && stateTicks % 2 == 0) {
            Vec3 tc = target.position().add(0, target.getBbHeight() * 0.55, 0);
            ShadowFx.tendril(l, hand, tc, stateTicks * 0.4);
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2, h = random.nextDouble() * target.getBbHeight();
                Vec3 p = target.position().add(Math.cos(a) * target.getBbWidth() * 0.6, h, Math.sin(a) * target.getBbWidth() * 0.6);
                ShadowFx.tendril(l, tc, p, stateTicks + i);
            }
        }
        if (distanceTo(target) > 3.6) {
            // the prey slipped away: release it and keep hunting
            locked = false;
            Possession.unlock(target);
            setState(APPROACH);
            return;
        }
        if (stateTicks >= REACH_TICKS) {
            mergeFrom = position();
            setState(MERGE);
        }
    }

    private void merge(ServerLevel l, Mob target) {
        getNavigation().stop();
        face(target, 60f);
        double t = Mth.clamp(stateTicks / (double) MERGE_TICKS, 0, 1);
        Vec3 hand = handPos();
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        // the body is consumed bottom-up: smoke is drawn along the arm and pours into the prey
        double top = 2.0 * (1 - t);
        for (int i = 0; i < 3; i++) {
            Vec3 src = position().add((random.nextDouble() - .5) * 0.5, random.nextDouble() * Math.max(0.3, top), (random.nextDouble() - .5) * 0.5);
            ShadowFx.stream(l, src, hand, 2, 0.3);
        }
        ShadowFx.stream(l, hand, chest, 6 + (int) (6 * t), 0.35);
        if (stateTicks % 2 == 0) ShadowFx.tendril(l, hand, chest, stateTicks * 0.5);
        // black tendrils wrap the prey and its eyes begin to burn violet
        if (stateTicks % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 wrap = target.position().add(Math.cos(a) * target.getBbWidth() * 0.7, random.nextDouble() * target.getBbHeight(), Math.sin(a) * target.getBbWidth() * 0.7);
            ShadowFx.tendril(l, chest, wrap, stateTicks);
        }
        if (stateTicks % 4 == 0) Fx.burst(l, Fx.glow(ShadowFx.BRIGHT, 0.5f), target.getEyePosition(), 2, 0.15, 0.01);
        if (stateTicks >= MERGE_TICKS) {
            ended = true;
            Possession.begin(target, l);
            cleanup();
            ShadowFx.puff(l, chest, 26, 0.45);
            Fx.burst(l, Fx.spark(ShadowFx.BRIGHT, 0.6f), chest, 16, 0.4, 0.18);
            discard();
        }
    }

    private void retarget(ServerLevel l, Player owner) {
        if (!(owner instanceof ServerPlayer sp)) {
            startDissolve();
            return;
        }
        Mob old = target();
        if (old != null) Possession.releaseTarget(old.getUUID(), getUUID());
        lostTicks = 0;
        Mob next = Possession.pick(sp, range, old);
        if (next == null) {
            startDissolve();
            return;
        }
        targetId = next.getUUID();
        Possession.claimTarget(targetId, getUUID());
        stuck = 0;
        unreachable = 0;
        if (state() != HUNT) setState(OBSERVE);
    }

    /** Begins the clone-only collapse: extremities crumble first, then the core, leaving only smoke. */
    public void startDissolve() {
        if (state() == DISSOLVE || ended) return;
        getNavigation().stop();
        setState(DISSOLVE);
        if (level() instanceof ServerLevel l) Fx.sound(l, position(), ModSounds.DC_DISINTEGRATE.get(), 0.8f, 1.1f);
    }

    private void dissolveTick(ServerLevel l) {
        double t = stateTicks / (double) DISSOLVE_TICKS;
        double top = 2.2 * (1 - t);
        for (int i = 0; i < 4; i++) {
            Vec3 p = position().add((random.nextDouble() - .5) * 0.8, random.nextDouble() * Math.max(0.2, top), (random.nextDouble() - .5) * 0.8);
            l.sendParticles(ShadowFx.smoke(0.9f), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
        }
        if (stateTicks % 5 == 0) l.sendParticles(ShadowFx.ash(0.6f), getX(), getY() + top, getZ(), 3, 0.3, 0.1, 0.3, 0.03);
        if (stateTicks == 2) Fx.sound(l, position(), ModSounds.DC_DISINTEGRATE.get(), 0.7f, 1.2f);
        if (stateTicks >= DISSOLVE_TICKS) {
            ShadowFx.puff(l, position().add(0, 0.6, 0), 14, 0.4);
            cleanup();
            ended = true;
            discard();
        }
    }

    private void ambientSounds(ServerLevel l) {
        if (--rumbleCd <= 0) {
            rumbleCd = 70;
            Fx.sound(l, position(), ModSounds.DC_HUNT.get(), 0.5f, 1f);
        }
        if (--whisperCd <= 0) {
            whisperCd = 90 + random.nextInt(90);
            Fx.sound(l, position(), ModSounds.DC_WHISPER.get(), 0.6f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    // ------------------------------------------------------------------------------------ helpers
    public Player owner() {
        UUID id = ownerId();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    private Mob target() {
        if (targetId == null || !(level() instanceof ServerLevel l)) return null;
        return l.getEntity(targetId) instanceof Mob m ? m : null;
    }

    private Vec3 handPos() {
        double yaw = Math.toRadians(getYRot());
        return position().add(-Math.sin(yaw) * 1.85 - Math.cos(yaw) * 0.35, 1.5, Math.cos(yaw) * 1.85 - Math.sin(yaw) * 0.35);
    }

    private void face(Entity t, float maxStep) {
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        float want = (float) (Mth.atan2(dz, dx) * 57.2957795) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), want, maxStep);
        setYRot(yaw);
        yHeadRot = yaw;
        yBodyRot = yaw;
        setXRot(0);
    }

    private void cleanup() {
        UUID o = ownerId();
        if (o != null) Possession.releaseClone(o, getUUID());
        if (targetId != null) Possession.releaseTarget(targetId, getUUID());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            // never leave a prey frozen if the clone vanishes mid-hunt
            if (locked && !ended) {
                Mob t = target();
                if (t != null && Possession.isPossessed(t)) Possession.unlock(t);
            }
            cleanup();
        }
        super.remove(reason);
    }

    private void clientFx() {
        int s = state();
        if (s == DISSOLVE) return;
        if (tickCount % 2 == 0) Fx.c(level(), Fx.wisp(ShadowFx.DEEP, 1f), getX() + (random.nextDouble() - .5) * 0.7, getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - .5) * 0.7, 0, 0.02, 0);
        if (tickCount % 3 == 0) Fx.c(level(), Fx.glow(ShadowFx.VIOLET, 0.55f), getX() + (random.nextDouble() - .5) * 0.5, getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - .5) * 0.5, 0, 0.01, 0);
        if (s == SUMMON && tickCount % 2 == 0) {
            Fx.c(level(), Fx.wisp(ShadowFx.DEEP, 1.4f), getX() + (random.nextDouble() - .5) * 1.6, getY() + 0.1, getZ() + (random.nextDouble() - .5) * 1.6, 0, 0.06, 0);
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(src, amount);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return true; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override protected void dropCustomDeathLoot(ServerLevel l, DamageSource s, boolean b) {}
    @Override public boolean isAlliedTo(Entity other) { return other instanceof DarkClone || super.isAlliedTo(other); }
    @Override public void readAdditionalSaveData(CompoundTag t) {}
    @Override public void addAdditionalSaveData(CompoundTag t) {}
}

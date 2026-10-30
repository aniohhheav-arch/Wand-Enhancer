package dev.infinitygauntlet.entity.power;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.entity.TimedEffectEntity;
import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ball of condensed Power Stone energy. Artillery orbs rise, hang while their targets are locked, then fall as
 * meteors. Barrage orbs circle their caster until fired, and each of the five barrage variants detonates in its
 * own way.
 */
public class PowerOrbEntity extends TimedEffectEntity implements GeoEntity {
    public static final int CLUSTER = 0, CHAIN = 1, GRAVITY = 2, LANCE = 3, SHATTER = 4, METEOR = 5;
    public static final int RISE = 0, HOVER = 1, DIVE = 2, ORBIT = 3, FLY = 4, WELL = 5;
    public static final int[] COLORS = {0xFF3FD2, 0xC8A8FF, 0x5A1AA8, 0xFFD6FF, 0xB98CFF, 0xA032FF};
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(PowerOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(PowerOrbEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.power_orb.idle");
    private static final RawAnimation DIVE_ANIM = RawAnimation.begin().thenLoop("animation.power_orb.dive");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client-side trail of recent positions. */
    public final Deque<Vec3> trail = new ArrayDeque<>();

    private Vec3 riseFrom = Vec3.ZERO;
    private Vec3 hoverAt = Vec3.ZERO;
    private int riseTicks = 15;
    private int diveAt = Integer.MAX_VALUE;
    @Nullable
    private Entity lockOn;
    private Vec3 lockPos = Vec3.ZERO;
    private Vec3 heading = Vec3.ZERO;
    private int slot;
    private int stateAge;
    private double travelled;
    private final Set<Integer> pierced = new HashSet<>();

    public PowerOrbEntity(EntityType<? extends PowerOrbEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, METEOR);
        builder.define(STATE, RISE);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateAge = 0;
    }

    /** Artillery: rise from {@code from} to {@code hover} over {@code riseTicks}, then dive at {@code diveTick}. */
    public void artillery(Vec3 from, Vec3 hover, int riseTicks, int diveTick) {
        entityData.set(VARIANT, METEOR);
        setState(RISE);
        this.riseFrom = from;
        this.hoverAt = hover;
        this.riseTicks = riseTicks;
        this.diveAt = diveTick;
        moveTo(from.x, from.y, from.z);
    }

    public void lock(@Nullable Entity target, Vec3 fallback) {
        this.lockOn = target;
        this.lockPos = fallback;
    }

    /** Barrage: circle the owner in {@code slot} until {@link #fire}d. */
    public void barrage(int variant, int slot) {
        entityData.set(VARIANT, variant);
        setState(ORBIT);
        this.slot = slot;
    }

    public void fire(Vec3 direction) {
        heading = direction.normalize();
        setState(FLY);
        Vfx.sound((ServerLevel) level(), position(), ModSounds.POWER_LAUNCH, 1.5f, 0.8f + variant() * 0.12f);
    }

    public boolean orbiting() {
        return state() == ORBIT;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        stateAge++;
        switch (state()) {
            case RISE -> {
                float t = Math.min(1f, stateAge / (float) riseTicks);
                float e = 1 - (1 - t) * (1 - t) * (1 - t);
                Vec3 p = riseFrom.lerp(hoverAt, e).add(0, Mth.sin(t * Mth.PI) * 1.5, 0);
                setPos(p);
                if (t >= 1) {
                    setState(HOVER);
                }
            }
            case HOVER -> {
                setPos(hoverAt.add(0, Mth.sin(tickCount * 0.3f) * 0.15, 0));
                if (tickCount >= diveAt) {
                    setState(DIVE);
                    Vfx.sound(level, position(), ModSounds.POWER_METEOR, 2.5f, 0.85f + random.nextFloat() * 0.3f);
                }
            }
            case DIVE -> {
                Vec3 target = lockOn != null && lockOn.isAlive() ? lockOn.position().add(0, lockOn.getBbHeight() * 0.3, 0) : lockPos;
                Vec3 to = target.subtract(position());
                double speed = Math.min(3.2, 0.6 + stateAge * 0.25);
                if (to.length() <= speed + 0.6 || stateAge > 60) {
                    meteorImpact(level, target);
                    return;
                }
                Vec3 step = to.normalize().scale(speed);
                BlockHitResult block = level.clip(new ClipContext(position(), position().add(step), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (block.getType() != HitResult.Type.MISS) {
                    meteorImpact(level, block.getLocation());
                    return;
                }
                setPos(position().add(step));
            }
            case ORBIT -> {
                ServerPlayer owner = owner();
                if (owner == null || !owner.isAlive() || owner.level() != level) {
                    discard();
                    return;
                }
                setPos(orbitPoint(owner, tickCount));
            }
            case FLY -> fly(level);
            case WELL -> gravityWell(level);
            default -> {
            }
        }
    }

    private Vec3 orbitPoint(Entity owner, float t) {
        double a = t * 0.12 + slot * Math.PI * 2 / 5;
        double r = 1.5 + 0.15 * Math.sin(t * 0.2 + slot);
        return owner.position().add(Math.cos(a) * r, 1.3 + 0.35 * Math.sin(t * 0.15 + slot * 1.3), Math.sin(a) * r);
    }

    private void fly(ServerLevel level) {
        ServerPlayer owner = owner();
        double speed = variant() == LANCE ? 2.6 : 1.7;
        Vec3 from = position();
        Vec3 to = from.add(heading.scale(speed));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        AABB sweep = new AABB(from, end).inflate(variant() == LANCE ? 1.0 : 0.7);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> owner == null || TargetRules.canTarget(owner, e))) {
            if (variant() == LANCE) {
                if (pierced.add(e.getId()) && owner != null) {
                    CombatFx.strike(owner, e, 14f, heading.scale(1.4).add(0, 0.25, 0), 1.2f, CombatFx.DeathStyle.RAGDOLL, false);
                    CombatFx.react(level, e, CombatFx.Reaction.RECOIL, heading, 10);
                    Vfx.send(level, VfxType.IMPACT_STAR, e.getBoundingBox().getCenter(), heading, 0xFFD6FF, 0xFFFFFF, 1.6f, 6);
                }
                continue;
            }
            impact(level, e.getBoundingBox().getCenter(), e);
            return;
        }
        setPos(end);
        travelled += from.distanceTo(end);
        if (block.getType() != HitResult.Type.MISS || travelled > 56) {
            impact(level, end, null);
        }
    }

    /** Each barrage variant's detonation. */
    private void impact(ServerLevel level, Vec3 at, @Nullable LivingEntity direct) {
        ServerPlayer owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        int color = COLORS[variant()];
        switch (variant()) {
            case CLUSTER -> {
                // splits into five bomblets that pop in a ring a beat later
                Vfx.send(level, VfxType.SPHERE_PULSE, at, Vec3.ZERO, color, 0xFFFFFF, 2.5f, 8);
                Vfx.sound(level, at, ModSounds.POWER_HIT, 2f, 1.3f);
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5 + random.nextDouble() * 0.4;
                    Vec3 p = at.add(Math.cos(a) * 3, 0.3, Math.sin(a) * 3);
                    int delay = 4 + i * 2;
                    dev.infinitygauntlet.util.ServerScheduler.schedule(delay, () -> {
                        Vfx.send(level, VfxType.POWER_EXPLOSION, p, Vec3.ZERO, color, 0xFFE0F8, 1.6f, 18);
                        Vfx.sound(level, p, ModSounds.POWER_EXPLOSION, 1.6f, 1.4f);
                        for (LivingEntity e : CombatFx.targets(owner, p, 2.6)) {
                            CombatFx.strike(owner, e, 6f, e.position().subtract(p).normalize().scale(0.8).add(0, 0.5, 0), 0.8f,
                                    CombatFx.DeathStyle.RAGDOLL, false);
                        }
                    });
                }
            }
            case CHAIN -> {
                // lightning leaps from victim to victim
                List<LivingEntity> chain = new ArrayList<>();
                LivingEntity current = direct;
                Vec3 from = at;
                if (current == null) {
                    List<LivingEntity> near = CombatFx.targets(owner, at, 6);
                    current = near.isEmpty() ? null : near.getFirst();
                }
                while (current != null && chain.size() < 6) {
                    chain.add(current);
                    Vec3 c = current.getBoundingBox().getCenter();
                    Vfx.send(level, VfxType.ARC, from, c, color, 0xFFFFFF, 1.6f, 10);
                    Vfx.send(level, VfxType.ARC, from, c, 0x8A5CFF, 0xFFFFFF, 0.9f, 8);
                    from = c;
                    LivingEntity next = null;
                    for (LivingEntity e : CombatFx.targets(owner, c, 8)) {
                        if (!chain.contains(e)) {
                            next = e;
                            break;
                        }
                    }
                    current = next;
                }
                for (int i = 0; i < chain.size(); i++) {
                    LivingEntity e = chain.get(i);
                    dev.infinitygauntlet.util.ServerScheduler.schedule(1 + i * 2, () -> {
                        if (!e.isAlive()) {
                            return;
                        }
                        CombatFx.strike(owner, e, 9f, new Vec3(0, 0.35, 0), 1f, CombatFx.DeathStyle.DISINTEGRATE, false);
                        CombatFx.react(level, e, CombatFx.Reaction.STAGGER, new Vec3(0, 1, 0), 16);
                        TimeManager.apply(e, 0f, 6, true);
                        Vfx.sound(level, e.position(), ModSounds.SPACE_ARC, 1.4f, 1.6f);
                    });
                }
                Vfx.send(level, VfxType.SPARK_BURST, at, Vec3.ZERO, color, 0xFFFFFF, 1.2f, 10);
                Vfx.sound(level, at, ModSounds.POWER_OVERLOAD, 2f, 1.5f);
            }
            case GRAVITY -> {
                // stops dead and becomes a small well that drags everything in, then pops
                setPos(at);
                setState(WELL);
                Vfx.sound(level, at, ModSounds.POWER_GRAVITY, 1.6f, 1.6f);
                Vfx.send(level, VfxType.IMPLOSION, at, Vec3.ZERO, color, 0xD8B0FF, 4.5f, 36);
                return;
            }
            case LANCE -> {
                Vfx.send(level, VfxType.SPARK_BURST, at, Vec3.ZERO, color, 0xFFFFFF, 1.4f, 12);
                Vfx.sound(level, at, ModSounds.POWER_HIT, 2f, 0.7f);
            }
            case SHATTER -> {
                // crystal nova: shards slice through everything around
                Vfx.send(level, VfxType.SHARD_NOVA, at, Vec3.ZERO, color, 0xF0E6FF, 7f, 26);
                Vfx.send(level, VfxType.SHOCKWAVE, at, new Vec3(0, 1, 0), color, 0xFFFFFF, 7f, 14);
                Vfx.sound(level, at, ModSounds.TIME_SHATTER, 2.2f, 0.7f);
                for (LivingEntity e : CombatFx.targets(owner, at, 6.5)) {
                    Vec3 away = e.position().subtract(at).normalize();
                    CombatFx.strike(owner, e, 10f, away.scale(1.1).add(0, 0.3, 0), 1f, CombatFx.DeathStyle.RAGDOLL, false);
                    CombatFx.react(level, e, CombatFx.Reaction.STAGGER, away, 14);
                }
            }
            default -> {
            }
        }
        discard();
    }

    private void gravityWell(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        for (LivingEntity e : CombatFx.targets(owner, position(), 9)) {
            Vec3 to = position().subtract(e.getBoundingBox().getCenter());
            double d = Math.max(0.6, to.length());
            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(Math.min(0.9, 2.2 / d))));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (stateAge % 8 == 0) {
                CombatFx.react(level, e, CombatFx.Reaction.SUSPEND, to, 10);
            }
        }
        if (stateAge % 6 == 0) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vfx.send(level, VfxType.ARC, position().add(dir.scale(4)), position(), COLORS[GRAVITY], 0xD8B0FF, 1f, 5);
        }
        if (stateAge >= 34) {
            Vfx.send(level, VfxType.POWER_EXPLOSION, position(), Vec3.ZERO, 0x3A0A70, 0xC890FF, 3.2f, 24);
            Vfx.send(level, VfxType.SHOCKWAVE, position(), new Vec3(0, 1, 0), 0x5A1AA8, 0xD8B0FF, 8f, 16);
            Vfx.sound(level, position(), ModSounds.POWER_EXPLOSION, 2.5f, 0.7f);
            Vfx.camera(level, position(), 30, 0.7f, 0, 0, 0xC890FF, 0.25f, 6);
            for (LivingEntity e : CombatFx.targets(owner, position(), 5)) {
                Vec3 away = e.position().subtract(position()).normalize();
                CombatFx.strike(owner, e, 13f, away.scale(1.6).add(0, 0.7, 0), 1.3f, CombatFx.DeathStyle.CRUSH, false);
                CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, away, 24);
            }
            discard();
        }
    }

    /** Artillery meteor: crater, cracks, debris and a pillar of purple fire. */
    private void meteorImpact(ServerLevel level, Vec3 at) {
        ServerPlayer owner = owner();
        Vec3 below = dev.infinitygauntlet.util.TeleportUtil.groundBelow(level, at.add(0, 0.5, 0), 64);
        boolean grounded = at.y - below.y < 3;
        // a meteor that catches its target in mid-air bursts there; only a ground strike scars the earth
        Vec3 ground = grounded ? below : at.subtract(0, 0.6, 0);
        Vfx.send(level, VfxType.POWER_EXPLOSION, ground.add(0, 0.6, 0), Vec3.ZERO, PowerFx.PURPLE, 0xFFC8F4, 3.4f, 26);
        Vfx.send(level, VfxType.SHOCKWAVE, ground.add(0, 0.15, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, 6f, 14);
        if (grounded) {
            PowerFx.cracks(level, ground.add(0, 0.05, 0), 4.5f, 120, PowerFx.MAGENTA);
            PowerFx.debris(level, ground, 0.9f, 14);
            PowerFx.crater(level, ground, 2.5f);
        }
        Vfx.sound(level, ground, ModSounds.POWER_EXPLOSION, 3f, 0.9f + random.nextFloat() * 0.2f);
        Vfx.camera(level, ground, 36, 0.9f, 0, 0, 0xE0A0FF, 0.2f, 5);
        if (owner != null) {
            for (LivingEntity e : CombatFx.targets(owner, ground.add(0, 1, 0), 4.2)) {
                Vec3 away = e.position().subtract(ground).multiply(1, 0, 1).normalize();
                CombatFx.strike(owner, e, 12f, away.scale(0.9).add(0, 1.0, 0), 1.4f, CombatFx.DeathStyle.RAGDOLL, false);
                CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, away, 20);
            }
        }
        discard();
    }

    @Override
    protected void clientTick() {
        trail.addFirst(position());
        while (trail.size() > (state() == DIVE || state() == FLY ? 12 : 6)) {
            trail.removeLast();
        }
        dev.infinitygauntlet.client.power.PowerParticles.orbTick(this);
    }

    /** Render scale per variant: meteors are big, barrage orbs small. */
    public float orbScale() {
        return switch (variant()) {
            case METEOR -> 1.3f;
            case GRAVITY -> 0.85f;
            case LANCE -> 0.65f;
            default -> 0.7f;
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3,
                state -> state.setAndContinue(state() == DIVE || state() == FLY ? DIVE_ANIM : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

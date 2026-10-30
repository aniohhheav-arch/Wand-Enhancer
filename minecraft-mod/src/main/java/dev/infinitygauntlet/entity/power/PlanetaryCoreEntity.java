package dev.infinitygauntlet.entity.power;

import dev.infinitygauntlet.entity.TimedEffectEntity;
import dev.infinitygauntlet.power.CombatFx;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Planetary Force: the caster holds a blazing core wrapped in spinning rings while it swells for three seconds, the
 * ground splitting and lifting around them, then hurls it. Where it lands a four-stage impact unfolds: a blinding
 * implosion, the main blast, a ring of secondary detonations and finally a towering smoke column.
 */
public class PlanetaryCoreEntity extends TimedEffectEntity implements GeoEntity {
    public static final int CHARGE = 60;
    public static final int PHASE_CHARGE = 0, PHASE_FLIGHT = 1, PHASE_IMPACT = 2;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(PlanetaryCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_START = SynchedEntityData.defineId(PlanetaryCoreEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.planetary_core.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 heading = Vec3.ZERO;
    private Vec3 destination = Vec3.ZERO;

    public PlanetaryCoreEntity(EntityType<? extends PlanetaryCoreEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, PHASE_CHARGE);
        builder.define(PHASE_START, 0);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public float phaseAge(float pt) {
        return tickCount - entityData.get(PHASE_START) + pt;
    }

    private void setPhase(int phase) {
        entityData.set(PHASE, phase);
        entityData.set(PHASE_START, tickCount);
    }

    /** Core radius in blocks: swells through the charge, holds in flight, collapses on impact. */
    public float coreRadius(float pt) {
        return switch (phase()) {
            case PHASE_CHARGE -> 0.25f + 1.35f * (float) Math.pow(Math.min(1f, phaseAge(pt) / CHARGE), 1.5);
            case PHASE_FLIGHT -> 1.6f;
            default -> Math.max(0, 1.6f * (1 - phaseAge(pt) / 6f));
        };
    }

    public static Vec3 holdPoint(LivingEntity owner) {
        return owner.getEyePosition().add(owner.getLookAngle().scale(2.6)).add(0, -0.2, 0);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            discard();
            return;
        }
        switch (phase()) {
            case PHASE_CHARGE -> charge(level, owner);
            case PHASE_FLIGHT -> flight(level, owner);
            default -> {
            }
        }
    }

    private void charge(ServerLevel level, ServerPlayer owner) {
        float age = phaseAge(0);
        setPos(holdPoint(owner));
        owner.setDeltaMovement(owner.getDeltaMovement().multiply(0, 1, 0));
        owner.hurtMarked = true;
        if (age == 1) {
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE + 4, 6, false, false, false));
        }
        Vec3 feet = owner.position();
        if (age % 15 == 1) {
            float stage = age / CHARGE;
            PowerFx.cracks(level, feet.add(0, 0.05, 0), 2.5f + stage * 7f, 110, PowerFx.PURPLE);
            Vfx.send(level, VfxType.SHOCKWAVE, feet.add(0, 0.1, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, 3f + stage * 9f, 14);
            Vfx.shake(level, feet, 40, 0.25f + stage * 0.6f);
        }
        if (age == 25 || age == 45) {
            PowerFx.debris(level, feet, 0.35f, 16);
        }
        if (age % 3 == 0) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian()).normalize();
            Vfx.send(level, VfxType.ARC, position().add(dir.scale(coreRadius(0) * 3 + 2)), position(), PowerFx.PURPLE, 0xFFFFFF, 1.2f, 4);
        }
        // creatures nearby are dragged toward the gathering mass and held there in slow motion
        for (LivingEntity e : CombatFx.targets(owner, feet, 12)) {
            Vec3 to = position().subtract(e.position());
            e.setDeltaMovement(e.getDeltaMovement().scale(0.7).add(to.normalize().scale(0.035)));
            e.hurtMarked = true;
            TimeManager.apply(e, 0.4f, 4, true);
        }
        if (age >= CHARGE) {
            launch(level, owner);
        }
    }

    private void launch(ServerLevel level, ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(owner.getLookAngle().scale(72)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        destination = hit.getType() == HitResult.Type.MISS
                ? dev.infinitygauntlet.util.TeleportUtil.groundBelow(level, eye.add(owner.getLookAngle().scale(40)), 64)
                : hit.getLocation();
        heading = destination.subtract(position()).normalize();
        setPhase(PHASE_FLIGHT);
        Vfx.sound(level, position(), ModSounds.POWER_LAUNCH, 4f, 0.5f);
        Vfx.sound(level, position(), ModSounds.POWER_BEAM, 3f, 0.6f);
        Vfx.send(level, VfxType.SHOCKWAVE, position(), heading, PowerFx.PURPLE, 0xFFFFFF, 7f, 12);
        Vfx.camera(level, position(), 30, 1f, 1.25f, 8, 0xFFFFFF, 0.3f, 4);
        Vfx.send(level, VfxType.ENTITY_TRAIL, position(), Vec3.ZERO, PowerFx.PURPLE, PowerFx.PINK, 2.2f, 40, getId());
        PowerFx.pose(owner, PowerPose.THRUST, 16);
        owner.setDeltaMovement(heading.scale(-0.8).add(0, 0.2, 0)); // recoil
        owner.hurtMarked = true;
    }

    private void flight(ServerLevel level, ServerPlayer owner) {
        Vec3 step = heading.scale(2.6);
        Vec3 next = position().add(step);
        boolean arrived = destination.distanceToSqr(position()) <= step.lengthSqr() + 1;
        if (!arrived) {
            BlockHitResult hit = level.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                destination = hit.getLocation();
                arrived = true;
            } else if (!CombatFx.targets(owner, next, 2.2).isEmpty()) {
                destination = next;
                arrived = true;
            }
        }
        if (arrived || phaseAge(0) > 40) {
            setPos(destination);
            setPhase(PHASE_IMPACT);
            impact(level, owner, destination);
            return;
        }
        setPos(next);
    }

    private void impact(ServerLevel level, ServerPlayer owner, Vec3 at) {
        maxLife = tickCount + 12;
        Vec3 ground = dev.infinitygauntlet.util.TeleportUtil.groundBelow(level, at.add(0, 0.5, 0), 8);
        Vec3 c = ground.add(0, 1.2, 0);
        // the shot: wide, low, looking back across the blast toward the caster
        Vec3 side = owner.position().subtract(c).normalize();
        Vec3 perp = new Vec3(-side.z, 0, side.x);
        PowerFx.cinematic(owner, null, c, perp.scale(18).add(0, 3, 0).add(side.scale(-4)), perp.scale(26).add(0, 14, 0).add(side.scale(-14)), 70, 6f, 1.15f);
        CombatFx.slowmo(level, c, 24, 0.3f, 20);

        // stage 1: implosion flash
        Vfx.send(level, VfxType.IMPLOSION, c, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 6f, 10);
        Vfx.camera(level, c, 120, 0.6f, 0, 0, 0xFFFFFF, 0.9f, 10);
        Vfx.sound(level, c, ModSounds.POWER_PLANETARY_IMPACT, 6f, 0.9f);
        Vfx.glitch(level, c, 80, 1f);

        // stage 2: the blast
        ServerScheduler.schedule(5, () -> {
            Vfx.send(level, VfxType.POWER_EXPLOSION, c, Vec3.ZERO, PowerFx.PURPLE, 0xFFE8FF, 11f, 40);
            Vfx.send(level, VfxType.SPHERE_PULSE, c, Vec3.ZERO, PowerFx.MAGENTA, 0xFFFFFF, 18f, 20);
            Vfx.send(level, VfxType.SHOCKWAVE, ground.add(0, 0.2, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, PowerFx.PINK, 30f, 28);
            Vfx.send(level, VfxType.SHOCKWAVE, c, new Vec3(0.3, 1, 0.1).normalize(), PowerFx.MAGENTA, 0xFFFFFF, 22f, 22);
            PowerFx.cracks(level, ground.add(0, 0.04, 0), 16f, 220, PowerFx.MAGENTA);
            PowerFx.debris(level, ground, 1.7f, 40);
            PowerFx.crater(level, ground, 6f);
            Vfx.camera(level, c, 120, 1.5f, 0, 0, 0xF0C8FF, 0.5f, 10);
            for (LivingEntity e : CombatFx.targets(owner, c, 14)) {
                TimeManager.clear(e);
                Vec3 away = e.position().subtract(ground).multiply(1, 0, 1).normalize();
                double d = e.position().distanceTo(c);
                float falloff = (float) Math.max(0.35, 1 - d / 14);
                CombatFx.strike(owner, e, 38f * falloff, away.scale(2.4 * falloff).add(0, 1.4 * falloff + 0.3, 0), 2f, CombatFx.DeathStyle.RAGDOLL, true);
                CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, away, 30);
            }
        });
        // stage 3: secondary detonations ripple outward
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + random.nextDouble() * 0.3;
            double r = 7 + random.nextDouble() * 5;
            Vec3 p = dev.infinitygauntlet.util.TeleportUtil.groundBelow(level, ground.add(Math.cos(a) * r, 3, Math.sin(a) * r), 8).add(0, 0.8, 0);
            ServerScheduler.schedule(11 + i, () -> {
                Vfx.send(level, VfxType.POWER_EXPLOSION, p, Vec3.ZERO, 0x7A20D0, 0xFFC8F0, 3f, 22);
                Vfx.sound(level, p, ModSounds.POWER_EXPLOSION, 2.5f, 1.1f + random.nextFloat() * 0.3f);
                for (LivingEntity e : CombatFx.targets(owner, p, 3.5)) {
                    CombatFx.strike(owner, e, 8f, e.position().subtract(p).normalize().scale(0.8).add(0, 0.8, 0), 1f, CombatFx.DeathStyle.RAGDOLL, false);
                }
            });
        }
        // stage 4: the column of smoke and the last shudder
        ServerScheduler.schedule(20, () -> {
            Vfx.send(level, VfxType.SMOKE_COLUMN, ground, Vec3.ZERO, 0x1E0A33, 0x9A40FF, 26f, 160);
            Vfx.send(level, VfxType.SHOCKWAVE, ground.add(0, 0.3, 0), new Vec3(0, 1, 0), 0x5A1AA8, PowerFx.PINK, 40f, 34);
            Vfx.sound(level, ground, ModSounds.POWER_GROUND_CRACK, 4f, 0.6f);
            Vfx.shake(level, ground, 120, 1.2f);
        });
    }

    @Override
    protected void clientTick() {
        dev.infinitygauntlet.client.power.PowerParticles.planetaryTick(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

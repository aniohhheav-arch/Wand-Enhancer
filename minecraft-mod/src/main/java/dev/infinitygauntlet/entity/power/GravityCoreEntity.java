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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gravity Impact: a black singularity ringed by a glowing accretion disk sinks out of the sky. Everything beneath it
 * is lifted off the ground and held helpless in the air, lightning crawling between them and the core, until it
 * touches down and slams them all into the earth.
 */
public class GravityCoreEntity extends TimedEffectEntity implements GeoEntity {
    public static final int DESCEND = 50;
    public static final int AFTER = 24;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.gravity_core.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double startY;
    private double groundY;
    private float fieldRadius = 9f;
    private final Set<LivingEntity> held = new HashSet<>();

    public GravityCoreEntity(EntityType<? extends GravityCoreEntity> type, Level level) {
        super(type, level);
    }

    public void place(Vec3 ground, double height, float radius) {
        this.groundY = ground.y;
        this.startY = ground.y + height;
        this.fieldRadius = radius;
        moveTo(ground.x, startY, ground.z);
    }

    /** 0..1 of the descent. */
    public float descent(float pt) {
        return Math.min(1f, (tickCount + pt) / DESCEND);
    }

    public boolean landed(float pt) {
        return tickCount + pt >= DESCEND;
    }

    public float fieldRadius() {
        return fieldRadius;
    }

    public double groundY() {
        return groundY;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.POWER_GRAVITY, 4f, 0.8f);
            Vfx.send(level, VfxType.GROUND_GLYPH, new Vec3(getX(), groundY + 0.05, getZ()), Vec3.ZERO, 0x3A0A70, PowerFx.PINK, fieldRadius, DESCEND + 4);
        }
        if (tickCount < DESCEND) {
            float d = descent(0);
            double y = startY + (groundY + 2.2 - startY) * (d * d);
            setPos(getX(), y, getZ());
            double hover = groundY + 2.6 + 1.6 * (1 - d);
            for (LivingEntity e : CombatFx.targets(owner, new Vec3(getX(), groundY + 2, getZ()), fieldRadius)) {
                if (held.add(e)) {
                    CombatFx.react(level, e, CombatFx.Reaction.SUSPEND, Vec3.ZERO, DESCEND - tickCount + 2);
                }
                Vec3 v = e.getDeltaMovement();
                double lift = (hover + (e.getId() % 5) * 0.25 - e.getY()) * 0.18;
                Vec3 toAxis = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ()).scale(0.015);
                e.setDeltaMovement(v.x * 0.5 + toAxis.x, lift, v.z * 0.5 + toAxis.z);
                e.hurtMarked = true;
                e.fallDistance = 0;
                if (tickCount % 9 == e.getId() % 9) {
                    Vfx.send(level, VfxType.ARC, position(), e.getBoundingBox().getCenter(), 0x6A2AD0, 0xE6C8FF, 1.3f, 6);
                }
            }
            if (tickCount % 4 == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * fieldRadius;
                Vfx.send(level, VfxType.ARC, position(), new Vec3(getX() + Math.cos(a) * r, groundY + 0.1, getZ() + Math.sin(a) * r),
                        0x4A1490, 0xD8B0FF, 1.5f, 5);
            }
            if (tickCount % 10 == 0) {
                Vfx.shake(level, position(), 40, 0.25f + 0.4f * d);
            }
        } else if (tickCount == DESCEND) {
            crush(level, owner);
        }
    }

    private void crush(ServerLevel level, ServerPlayer owner) {
        Vec3 ground = new Vec3(getX(), groundY, getZ());
        boolean killed = false;
        List<LivingEntity> victims = new ArrayList<>(held);
        victims.addAll(CombatFx.targets(owner, ground.add(0, 1, 0), fieldRadius * 0.6));
        Set<LivingEntity> done = new HashSet<>();
        for (LivingEntity e : victims) {
            if (!e.isAlive() || !done.add(e)) {
                continue;
            }
            CombatFx.react(level, e, CombatFx.Reaction.CRUSH, new Vec3(0, -1, 0), 30);
            killed |= CombatFx.strike(owner, e, 24f, new Vec3(0, -3.2, 0), 2f, CombatFx.DeathStyle.CRUSH, true);
        }
        Vfx.send(level, VfxType.POWER_EXPLOSION, ground.add(0, 0.4, 0), Vec3.ZERO, 0x2A0848, 0xB070FF, 5f, 30);
        Vfx.send(level, VfxType.SHOCKWAVE, ground.add(0, 0.2, 0), new Vec3(0, 1, 0), 0x3A0A70, PowerFx.PINK, fieldRadius * 2.2f, 22);
        Vfx.send(level, VfxType.SHOCKWAVE, ground.add(0, 0.3, 0), new Vec3(0, 1, 0), PowerFx.PURPLE, 0xFFFFFF, fieldRadius * 1.3f, 14);
        PowerFx.cracks(level, ground.add(0, 0.04, 0), fieldRadius * 1.2f, 160, 0x7A2AE0);
        PowerFx.debris(level, ground, 1.3f, 26);
        PowerFx.crater(level, ground, 4f);
        Vfx.sound(level, ground, ModSounds.POWER_PLANETARY_IMPACT, 3.5f, 1.3f);
        Vfx.sound(level, ground, ModSounds.POWER_GROUND_CRACK, 3f, 0.8f);
        Vfx.camera(level, ground, 60, 1.4f, 0.85f, 10, 0xB070FF, 0.4f, 8);
        Vfx.glitch(level, ground, 40, 0.8f);
        CombatFx.hitstop(level, ground, fieldRadius + 4, 5, owner);
        if (killed) {
            singularityFinisher(level, owner, ground);
        }
    }

    /** Crushing Singularity: the core collapses on its victims' remains and detonates. */
    private void singularityFinisher(ServerLevel level, ServerPlayer owner, Vec3 ground) {
        Vec3 core = ground.add(0, 2.2, 0);
        PowerFx.cinematic(owner, null, core, PowerFx.rel(owner, 4, 1.2, -6).add(0, -1, 0), PowerFx.rel(owner, -7, 8, -12), 46, -8f, 0.8f);
        PowerFx.pose(owner, PowerPose.SLAM, 24);
        CombatFx.slowmo(level, ground, fieldRadius + 6, 0.25f, 26);
        Vfx.sound(level, core, ModSounds.POWER_FINISHER, 3f, 0.7f);
        ServerScheduler.schedule(6, () -> {
            Vfx.send(level, VfxType.IMPLOSION, core, Vec3.ZERO, 0x2A0848, 0xFFFFFF, 7f, 16);
            Vfx.sound(level, core, ModSounds.POWER_NOVA_CHARGE, 2.5f, 1.8f);
        });
        ServerScheduler.schedule(18, () -> {
            Vfx.send(level, VfxType.POWER_EXPLOSION, core, Vec3.ZERO, PowerFx.PURPLE, 0xFFFFFF, 7.5f, 34);
            Vfx.send(level, VfxType.SMOKE_COLUMN, ground, Vec3.ZERO, 0x1A0830, 0x7A2AE0, 16f, 100);
            Vfx.send(level, VfxType.SHOCKWAVE, core, new Vec3(0, 1, 0), PowerFx.MAGENTA, 0xFFFFFF, 26f, 24);
            Vfx.sound(level, core, ModSounds.POWER_NOVA_RELEASE, 4f, 0.6f);
            Vfx.camera(level, core, 80, 1.5f, 0, 0, 0xF4D8FF, 0.75f, 12);
            Vfx.glitch(level, core, 60, 1f);
            for (LivingEntity e : CombatFx.targets(owner, core, 10)) {
                TimeManager.clear(e);
                Vec3 away = e.position().subtract(core).normalize();
                CombatFx.strike(owner, e, 14f, away.scale(1.8).add(0, 0.9, 0), 1.6f, CombatFx.DeathStyle.RAGDOLL, true);
                CombatFx.react(level, e, CombatFx.Reaction.TUMBLE, away, 24);
            }
        });
    }

    @Override
    protected void clientTick() {
        dev.infinitygauntlet.client.power.PowerParticles.gravityTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(startY);
        buf.writeDouble(groundY);
        buf.writeFloat(fieldRadius);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        startY = buf.readDouble();
        groundY = buf.readDouble();
        fieldRadius = buf.readFloat();
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

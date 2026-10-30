package dev.infinitygauntlet.entity;

import dev.infinitygauntlet.client.ClientEffects;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A pane of broken time hanging over the battlefield. Glowing cracks spread across it like shattered glass and it
 * slowly turns; creatures nearby are hit by periodic bursts of near-frozen time and trail afterimages of their past
 * positions. It ends by imploding into a green flash.
 */
public class TimeFractureEntity extends TimedEffectEntity {
    public static final int SPREAD_TICKS = 40;
    public static final int COLLAPSE_TICKS = 16;
    private static final int PULSE_PERIOD = 40;
    private static final int PULSE_TICKS = 16;

    private float facingYaw;

    public TimeFractureEntity(EntityType<? extends TimeFractureEntity> type, Level level) {
        super(type, level);
    }

    public void face(float yaw) {
        this.facingYaw = yaw;
    }

    public float facingYaw() {
        return facingYaw;
    }

    public float spread(float pt) {
        float s = Math.min(1f, (tickCount + pt) / SPREAD_TICKS);
        return 1 - (1 - s) * (1 - s);
    }

    public float collapse(float pt) {
        return Math.max(0f, (tickCount + pt - (maxLife - COLLAPSE_TICKS)) / COLLAPSE_TICKS);
    }

    /** 0..1 intensity of the current slowing burst (for visual sync). */
    public float pulse(float pt) {
        float phase = (tickCount + pt) % PULSE_PERIOD;
        return phase < PULSE_TICKS ? 1 - phase / PULSE_TICKS : 0;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (tickCount == 1) {
            Vfx.sound(level, position(), ModSounds.TIME_FRACTURE, 4f, 0.9f);
            Vfx.camera(level, position(), 48, 0.6f, 0, 0, 0xB4FFC8, 0.2f, 6);
        }
        if (tickCount < SPREAD_TICKS / 2 || tickCount % PULSE_PERIOD != 0) {
            return;
        }
        ServerPlayer owner = owner();
        double r = visualRadius * 1.5;
        int hit = 0;
        for (Entity e : level.getEntities(this, new AABB(position(), position()).inflate(r, r, r),
                e -> (e instanceof LivingEntity || e instanceof Projectile) && TimeFieldEntity.affectedBy(owner, e))) {
            TimeManager.apply(e, 0.08f, PULSE_TICKS);
            hit++;
            if (e instanceof LivingEntity) {
                Vfx.send(level, VfxType.AFTERIMAGES, e.position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), 1f,
                        PULSE_TICKS + 10, e.getId());
            }
        }
        Vfx.send(level, VfxType.SPHERE_PULSE, position(), Vec3.ZERO, InfinityStone.TIME.color(), InfinityStone.TIME.highlight(), (float) r, 14);
        Vfx.sound(level, position(), ModSounds.TIME_FREEZE, hit > 0 ? 1.5f : 0.8f, 1.6f);
    }

    @Override
    protected void onExpire(ServerLevel level) {
        Vfx.send(level, VfxType.IMPLOSION, position(), Vec3.ZERO, InfinityStone.TIME.color(), 0xE8FFF0, visualRadius * 0.8f, 12);
        Vfx.shockwave(level, position(), Vec3.directionFromRotation(0, facingYaw), InfinityStone.TIME, visualRadius * 2.4f, 18);
        Vfx.shockwave(level, position(), new Vec3(0, 1, 0), InfinityStone.TIME, visualRadius * 2f, 16);
        Vfx.camera(level, position(), 64, 1.0f, 0, 0, 0xD8FFE4, 0.6f, 10);
        Vfx.sound(level, position(), ModSounds.TIME_SHATTER, 5f, 0.8f);
    }

    @Override
    protected void clientTick() {
        ClientEffects.fractureTick(this);
    }

    @Override
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(facingYaw);
    }

    @Override
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buf) {
        facingYaw = buf.readFloat();
    }
}

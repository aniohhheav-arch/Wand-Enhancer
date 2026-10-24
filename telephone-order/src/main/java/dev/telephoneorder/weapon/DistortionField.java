package dev.telephoneorder.weapon;

import dev.telephoneorder.Fx;
import dev.telephoneorder.TelephoneOrder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Temporary spatial distortion left by the Rift Gun: drags entities in, scrambles their position and hurts them. */
public final class DistortionField {
    private static final List<DistortionField> FIELDS = new ArrayList<>();
    private final ServerLevel level;
    private final Vec3 center;
    private final UUID owner;
    private int ticks;
    private final float radius, pull, dps;

    private DistortionField(ServerLevel level, Vec3 c, UUID owner, int ticks, float radius, float pull, float dps) {
        this.level = level; this.center = c; this.owner = owner; this.ticks = ticks; this.radius = radius; this.pull = pull; this.dps = dps;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(s -> FIELDS.removeIf(f -> !f.tick()));
    }

    public static void create(ServerLevel l, Vec3 at, ServerPlayer owner, int duration, float radius, float pull) {
        Fx.sound(l, at, "rift_open", SoundSource.PLAYERS, 1.2f, 1.6f);
        Fx.shake(l, at, 24, 0.5f, 12);
        FIELDS.add(new DistortionField(l, at, owner == null ? null : owner.getUUID(), Math.max(20, duration), radius, pull <= 0 ? 0.2f : pull, 4f));
    }

    private boolean tick() {
        if (--ticks <= 0) {
            Fx.p(level, ParticleTypes.EXPLOSION, center, 1, 0, 0, 0, 0);
            Fx.p(level, ParticleTypes.REVERSE_PORTAL, center, 30, 0.8, 0.8, 0.8, 0.4);
            Fx.sound(level, center, "emp_burst", SoundSource.PLAYERS, 0.8f, 0.6f);
            return false;
        }
        // visuals: collapsing spiral
        double t = (ticks) * 0.45;
        for (int i = 0; i < 3; i++) {
            double a = t + i * Math.PI * 2 / 3;
            double r = radius * (0.35 + 0.65 * ((ticks * 0.07 + i * 0.33) % 1.0));
            Vec3 p = center.add(Math.cos(a) * r, Math.sin(ticks * 0.2 + i) * 0.4, Math.sin(a) * r);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
            if (ticks % 2 == 0) level.sendParticles(Fx.dust(0xBE5AFF, 1.3f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.6);
        if (ticks % 20 == 0) Fx.sound(level, center, "emp_burst", SoundSource.PLAYERS, 0.35f, 0.5f);
        ServerPlayer o = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(center, center).inflate(radius), x -> x.isAlive() && !x.isSpectator())) {
            if (e == o) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(center);
            if (d > radius) continue;
            Vec3 to = center.subtract(c);
            double f = pull * (1.0 - d / radius * 0.5);
            Vec3 v = d < 0.5 ? Vec3.ZERO : to.normalize().scale(f);
            Ballistics.impulse(e, v.scale(0.6));
            if (e instanceof LivingEntity le) {
                if (ticks % 20 == 0) Ballistics.damage(level, o, le, dps, 0f, DamageKind.ENERGY, null, false, false);
                if (ticks % 12 == 0) {
                    // scramble: small random displacement
                    Vec3 np = e.position().add((level.getRandom().nextDouble() - 0.5) * 3.0, (level.getRandom().nextDouble() - 0.3) * 1.5, (level.getRandom().nextDouble() - 0.5) * 3.0);
                    if (level.noCollision(e, e.getBoundingBox().move(np.subtract(e.position())))) {
                        Fx.p(level, ParticleTypes.REVERSE_PORTAL, e.position().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.3);
                        e.teleportTo(np.x, np.y, np.z);
                    }
                }
                if (ticks % 30 == 0) le.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
            }
        }
        return true;
    }

    public static void clear() { FIELDS.clear(); }
}

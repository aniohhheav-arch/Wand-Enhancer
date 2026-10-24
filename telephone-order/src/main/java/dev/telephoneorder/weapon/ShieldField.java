package dev.telephoneorder.weapon;

import dev.telephoneorder.Fx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Aegis Emitter bubble: follows its owner, deflects hostile projectiles and grants resistance + absorption. */
public final class ShieldField {
    private static final List<ShieldField> SHIELDS = new ArrayList<>();
    private final UUID owner;
    private int ticks;
    private final float radius;

    private ShieldField(UUID o, int ticks, float r) { owner = o; this.ticks = ticks; radius = r; }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(s -> SHIELDS.removeIf(f -> !f.tick(s)));
    }

    public static boolean active(UUID p) { return SHIELDS.stream().anyMatch(s -> s.owner.equals(p)); }

    public static void create(ServerPlayer p, int duration, float radius, int absorb) {
        SHIELDS.removeIf(s -> s.owner.equals(p.getUUID()));
        SHIELDS.add(new ShieldField(p.getUUID(), duration, radius));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration + 20, Math.max(0, absorb / 4 - 1)));
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0, true, false));
    }

    private boolean tick(net.minecraft.server.MinecraftServer server) {
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p == null || p.isDeadOrDying() || --ticks <= 0) {
            if (p != null && p.level() instanceof ServerLevel sl) {
                Fx.p(sl, ParticleTypes.GLOW, p.position().add(0, 1, 0), 24, radius * 0.5, 0.8, radius * 0.5, 0.05);
                Fx.sound(p, "emp_burst", SoundSource.PLAYERS, 0.5f, 1.7f);
            }
            return false;
        }
        ServerLevel l = (ServerLevel) p.level();
        Vec3 c = p.position().add(0, 1.0, 0);
        // shell particles (cheap fibonacci sphere, rotating)
        if (ticks % 2 == 0) {
            int n = 14;
            for (int i = 0; i < n; i++) {
                double y = 1 - 2.0 * (i + 0.5) / n;
                double rr = Math.sqrt(1 - y * y);
                double a = i * 2.399963 + ticks * 0.08;
                l.sendParticles(Fx.dust(0x5AA0FF, 1.0f), c.x + Math.cos(a) * rr * radius, c.y + y * radius * 0.8, c.z + Math.sin(a) * rr * radius, 1, 0, 0, 0, 0);
            }
        }
        for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(radius + 1), x -> x instanceof Projectile)) {
            Projectile pr = (Projectile) e;
            if (pr.getOwner() == p) continue;
            if (pr.position().distanceTo(c) > radius + 0.5) continue;
            Vec3 out = pr.position().subtract(c).normalize();
            pr.setDeltaMovement(out.scale(Math.max(0.4, pr.getDeltaMovement().length() * 0.6)));
            pr.setOwner(p);
            Fx.p(l, ParticleTypes.ELECTRIC_SPARK, pr.position(), 8, 0.1, 0.1, 0.1, 0.2);
            Fx.sound(l, pr.position(), "impact_energy", SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        return true;
    }

    public static void clear() { SHIELDS.clear(); }
}

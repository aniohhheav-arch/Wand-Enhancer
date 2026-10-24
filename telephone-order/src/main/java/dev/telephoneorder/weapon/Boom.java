package dev.telephoneorder.weapon;

import dev.telephoneorder.Fx;
import dev.telephoneorder.ModConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Non-terrain-destroying (by default) explosions with real area damage. */
public final class Boom {
    private Boom() {}

    public static void explode(ServerLevel l, Vec3 at, float radius, float damage, ServerPlayer owner, DamageKind kind, Entity skip, float knock, boolean fancy) {
        dev.telephoneorder.Blast.explosion(l, at, fancy ? radius : Math.max(1f, radius * 0.6f));
        Fx.sound(l, at, "explosion_small", SoundSource.PLAYERS, 0.9f + radius * 0.12f, 0.9f + l.getRandom().nextFloat() * 0.2f);
        Fx.shake(l, at, 20 + radius * 2, Math.min(1.2f, 0.25f + radius * 0.12f), 8 + (int) radius * 2);
        AABB box = new AABB(at, at).inflate(radius);
        for (Entity e : l.getEntities(skip, box, x -> x.isAlive() && !x.isSpectator() && (x instanceof LivingEntity || x instanceof net.minecraft.world.entity.item.ItemEntity))) {
            if (e instanceof net.minecraft.world.entity.player.Player tp && owner != null && tp != owner && !owner.canHarmPlayer(tp)) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(at);
            if (d > radius) continue;
            double f = 1.0 - d / radius;
            Vec3 away = d < 0.01 ? new Vec3(0, 1, 0) : c.subtract(at).normalize();
            if (e instanceof LivingEntity) {
                Ballistics.damage(l, owner, e, (float) (damage * (0.35 + 0.65 * f)), 0f, kind, away, false, false);
            }
            Ballistics.impulse(e, away.scale(knock * f).add(0, knock * f * 0.4, 0));
        }
        if (ModConfig.get().explosionsBreakBlocks) {
            l.explode(owner, at.x, at.y, at.z, Math.max(1.0f, radius * 0.5f), Level.ExplosionInteraction.TNT);
        }
    }
}

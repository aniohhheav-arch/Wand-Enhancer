package dev.arcana.spells;

import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.SpellContext;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/** Storm: lightning strikes, chains, static fields and tempests. */
public final class StormSpells {

    /** Visual-only lightning plus our own damage, so it respects config and never sets the world on fire. */
    static void strike(SpellContext c, Vec3 pos, float dmg, double radius) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(c.level);
        if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(true);
            c.level.addFreshEntity(bolt);
        }
        Magic.fx(c.level, ParticleTypes.ELECTRIC_SPARK, pos.add(0, 0.5, 0), 30, radius * 0.4, 0.6, radius * 0.4, 0.4);
        Magic.ring(c.level, Magic.dust(c, 1.2f), pos, radius, 24, 0.2);
        for (LivingEntity e : Magic.enemies(c, pos, radius)) {
            if (Magic.hurt(c, e, dmg)) {
                Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
                Magic.push(e, new Vec3(0, 0.35, 0));
            }
        }
    }

    /** Calls a bolt down on the target. Charged casts from awakened wands call a volley. */
    public static boolean thunderbolt(SpellContext c) {
        LivingEntity t = Magic.aimEntity(c, 32);
        Vec3 pos = t != null ? t.position() : Magic.aimPoint(c, 32);
        int bolts = 1 + (c.charge > 0.5f ? c.stage : 0);
        float dmg = 7f + 8f * c.charge;
        strike(c, pos, dmg, 3 * c.radius);
        for (int i = 1; i < bolts; i++) {
            final int n = i;
            EffectManager.later(c.level, i * 5, () -> {
                double a = c.level.random.nextDouble() * Math.PI * 2;
                strike(c, pos.add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5), dmg * 0.7f, 2.5 * c.radius);
            });
        }
        return true;
    }

    /** Electricity leaps from enemy to enemy with diminishing damage. */
    public static boolean chainLightning(SpellContext c) {
        LivingEntity first = Magic.aimEntity(c, 22);
        if (first == null) {
            Vec3 p = Magic.aimPoint(c, 16);
            var near = Magic.enemies(c, p, 8);
            if (near.isEmpty()) return false;
            first = near.get(0);
        }
        int jumps = 4 + c.stage * 2;
        Set<UUID> visited = new HashSet<>();
        LivingEntity[] cur = {first};
        Vec3[] prev = {Magic.eye(c.player).add(0, -0.3, 0)};
        var dust = Magic.dust(c, 1f);
        EffectManager.run(c.level, jumps * 3 + 1, age -> {
            if (age % 3 != 0) return true;
            LivingEntity t = cur[0];
            if (t == null) return false;
            visited.add(t.getUUID());
            Vec3 at = t.position().add(0, t.getBbHeight() / 2, 0);
            Magic.line(c.level, ParticleTypes.ELECTRIC_SPARK, prev[0], at, 0.4);
            Magic.line(c.level, dust, prev[0], at, 0.8);
            Magic.fx(c.level, ParticleTypes.ELECTRIC_SPARK, at, 10, 0.3, 0.4, 0.3, 0.3);
            Magic.snd(c.level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.6f);
            Magic.hurt(c, t, 6f * (float) Math.pow(0.86, age / 3));
            Magic.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
            prev[0] = at;
            LivingEntity best = null;
            double bd = 49;
            for (LivingEntity o : Magic.enemies(c, at, 7)) {
                if (visited.contains(o.getUUID())) continue;
                double d = o.distanceToSqr(t);
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
            cur[0] = best;
            return best != null;
        }, null);
        return true;
    }

    /** Overcharges the caster: faster movement and a crackling aura that zaps nearby enemies. */
    public static boolean staticField(SpellContext c) {
        int duration = 160 + c.stage * 40;
        double r = 6 * c.radius;
        Magic.snd(c.level, c.player.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.7f, 1.4f);
        EffectManager.run(c.level, duration, age -> {
            if (!c.valid()) return false;
            Vec3 center = c.player.position().add(0, 1, 0);
            if (age % 20 == 0) c.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false));
            for (int i = 0; i < Magic.count(3); i++) {
                Vec3 d = new Vec3(c.level.random.nextGaussian(), c.level.random.nextGaussian() * 0.6, c.level.random.nextGaussian()).normalize().scale(1.4);
                c.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + d.x, center.y + d.y, center.z + d.z, 1, 0, 0, 0, 0);
            }
            if (age % 20 == 10) Magic.ring(c.level, Magic.dust(c, 1f), c.player.position(), r, 32, 0.2);
            if (age % 20 == 0) {
                for (LivingEntity e : Magic.enemies(c, center, r)) {
                    Magic.line(c.level, ParticleTypes.ELECTRIC_SPARK, center, e.position().add(0, e.getBbHeight() / 2, 0), 0.5);
                    Magic.hurt(c, e, 3f);
                    Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
                }
            }
            return true;
        }, null);
        return true;
    }

    /** Ultimate: a roaring thunderstorm that rains bolts over a wide area. */
    public static boolean tempest(SpellContext c) {
        Vec3 center = Magic.aimFar(c, 28, 17);
        double r = 12 * c.radius;
        Magic.snd(c.level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.7f);
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.STORM_CLOUD, center.add(0, 14, 0), 13.7f * c.radius, 170, dev.arcana.entity.SpellFxEntity.EASE);
        EffectManager.run(c.level, 160, age -> {
            if (!c.valid()) return false;
            for (int i = 0; i < Magic.count(8); i++) {
                double a = c.level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(c.level.random.nextDouble()) * r;
                c.level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x + Math.cos(a) * d, center.y + 14, center.z + Math.sin(a) * d, 1, 0.3, 0.2, 0.3, 0.01);
            }
            if (age % 6 == 0) {
                double a = c.level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(c.level.random.nextDouble()) * r;
                Vec3 p = new Vec3(center.x + Math.cos(a) * d, Magic.groundY(c.level, center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d), center.z + Math.sin(a) * d);
                strike(c, p, 8f, 2.8);
            }
            return true;
        }, null);
        return true;
    }

    private StormSpells() {}
}

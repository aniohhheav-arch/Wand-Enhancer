package dev.arcana.spells;

import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.SpellContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Crimson: magical chains, crescent waves, explosive seals. */
public final class CrimsonSpells {

    /** Binds the targeted enemy (and neighbours when awakened) with chains and drags them toward the caster. */
    public static boolean chains(SpellContext c) {
        LivingEntity first = Magic.aimEntity(c, 22);
        if (first == null) return false;
        List<LivingEntity> bound = new ArrayList<>();
        bound.add(first);
        int extra = c.stage;
        for (LivingEntity e : Magic.enemies(c, first.position(), 7)) if (extra > 0 && e != first) {
            bound.add(e);
            extra--;
        }
        for (LivingEntity e : bound) Magic.hurt(c, e, 3f);
        Magic.snd(c.level, c.player.position(), SoundEvents.CHAIN_PLACE, 1f, 0.6f);
        int duration = 45 + (int) (c.charge * 40);
        var dust = Magic.dust(c, 1.1f);
        EffectManager.run(c.level, duration, age -> {
            if (!c.valid()) return false;
            boolean any = false;
            Vec3 hand = Magic.eye(c.player).add(0, -0.4, 0);
            for (LivingEntity e : bound) {
                if (!e.isAlive()) continue;
                any = true;
                Vec3 mid = e.position().add(0, e.getBbHeight() / 2, 0);
                Magic.line(c.level, dust, hand, mid, 0.45);
                if (age % 3 == 0) Magic.fx(c.level, ParticleTypes.CRIT, mid, 3, 0.2, 0.2, 0.2, 0.1);
                if (e.distanceToSqr(c.player) > 9) Magic.pull(e, c.player.position().add(0, 1, 0), 0.5);
                Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 10, 4);
                if (age % 20 == 10) Magic.hurt(c, e, 1.5f);
            }
            return any;
        }, null);
        return true;
    }

    /** A crescent of crimson energy sweeping outward in front of the caster. */
    public static boolean wave(SpellContext c) {
        Vec3 origin = c.player.position().add(0, 1, 0);
        Vec3 fwd = Magic.flatLook(c.player);
        double halfArc = Math.toRadians(62 + c.stage * 10);
        Set<UUID> hit = new HashSet<>();
        var dust = Magic.dust(c, 1.5f);
        Magic.snd(c.level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        EffectManager.run(c.level, 14, age -> {
            double r = 1.5 + age * 1.1;
            int pts = Magic.count(26);
            for (int i = 0; i < pts; i++) {
                double a = -halfArc + 2 * halfArc * i / (pts - 1.0);
                Vec3 p = origin.add(Magic.rotateY(fwd, a).scale(r));
                c.level.sendParticles(i % 4 == 0 ? ParticleTypes.CRIMSON_SPORE : dust, p.x, p.y, p.z, 1, 0, 0.15, 0, 0);
            }
            for (LivingEntity e : Magic.enemies(c, origin, r + 1.3)) {
                Vec3 off = e.position().subtract(origin).multiply(1, 0, 1);
                double dist = off.length();
                if (Math.abs(dist - r) > 1.4 || dist < 0.01) continue;
                if (Math.acos(Math.max(-1, Math.min(1, off.normalize().dot(fwd)))) > halfArc) continue;
                if (!hit.add(e.getUUID())) continue;
                Magic.hurt(c, e, 6f);
                Vec3 kb = off.normalize().scale(1.1);
                Magic.push(e, new Vec3(kb.x, 0.35, kb.z));
            }
            return true;
        }, null);
        return true;
    }

    /** Draws an explosive glyph that slows everything inside, then detonates. */
    public static boolean bloodSeal(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 20);
        double r = 3.6 * c.radius;
        var dust = Magic.dust(c, 1.3f);
        Magic.snd(c.level, center, SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.5f);
        EffectManager.run(c.level, 40, age -> {
            double spin = age * 0.15;
            Magic.ring(c.level, dust, center, r, 36, 0.1);
            Magic.ring(c.level, dust, center, r * 0.55, 20, 0.1);
            for (int i = 0; i < 5; i++) {
                double a1 = spin + i * Math.PI * 2 / 5, a2 = spin + (i + 2) * Math.PI * 2 / 5;
                Magic.line(c.level, dust, center.add(Math.cos(a1) * r, 0.1, Math.sin(a1) * r), center.add(Math.cos(a2) * r, 0.1, Math.sin(a2) * r), 0.7);
            }
            Magic.fx(c.level, ParticleTypes.CRIMSON_SPORE, center.add(0, 0.3, 0), 4, r * 0.4, 0.3, r * 0.4, 0);
            for (LivingEntity e : Magic.enemies(c, center, r)) Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 10, 2);
            return true;
        }, () -> {
            Magic.fx(c.level, ParticleTypes.EXPLOSION_EMITTER, center.add(0, 0.5, 0), 1, 0, 0, 0, 0);
            Magic.fx(c.level, dust, center.add(0, 1, 0), 80, r * 0.5, 1, r * 0.5, 0.4);
            Magic.snd(c.level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.8f);
            for (LivingEntity e : Magic.enemies(c, center, r + 0.5)) {
                Magic.hurt(c, e, 11f);
                Vec3 kb = e.position().subtract(center).multiply(1, 0, 1).normalize().scale(1.2);
                Magic.push(e, new Vec3(kb.x, 0.6, kb.z));
            }
        });
        return true;
    }

    /** Ultimate: five successive crimson shockwaves with erupting pillars; each hit heals the caster. */
    public static boolean cataclysm(SpellContext c) {
        Vec3 center = c.player.position();
        double max = 14 * c.radius;
        var dust = Magic.dust(c, 1.8f);
        Magic.snd(c.level, center, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.6f);
        for (int w = 0; w < 5; w++) {
            final int wave = w;
            EffectManager.later(c.level, w * 12, () -> {
                if (!c.valid()) return;
                Set<UUID> hit = new HashSet<>();
                Vec3 ctr = c.player.position();
                Magic.snd(c.level, ctr, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 0.9f + wave * 0.1f);
                EffectManager.run(c.level, 14, age -> {
                    double r = 1.5 + (max - 1.5) * (age + 1) / 14.0;
                    Magic.ring(c.level, dust, ctr, r, 72, 0.15);
                    Magic.ring(c.level, ParticleTypes.CRIMSON_SPORE, ctr, r, 36, 0.5);
                    if (age % 3 == 0) {
                        double a = c.level.random.nextDouble() * Math.PI * 2;
                        Vec3 pp = ctr.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                        for (int h = 0; h < 6; h++) c.level.sendParticles(dust, pp.x, pp.y + h * 0.5, pp.z, 2, 0.1, 0.1, 0.1, 0);
                    }
                    for (LivingEntity e : Magic.enemies(c, ctr, r + 1.2)) {
                        if (Math.abs(e.position().subtract(ctr).multiply(1, 0, 1).length() - r) > 1.5 || !hit.add(e.getUUID())) continue;
                        if (Magic.hurt(c, e, 7f)) c.player.heal(1.5f);
                        Vec3 kb = e.position().subtract(ctr).multiply(1, 0, 1).normalize().scale(0.9);
                        Magic.push(e, new Vec3(kb.x, 0.45, kb.z));
                    }
                    return true;
                }, null);
            });
        }
        return true;
    }

    private CrimsonSpells() {}
}

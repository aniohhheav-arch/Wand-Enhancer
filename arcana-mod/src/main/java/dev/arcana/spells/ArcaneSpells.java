package dev.arcana.spells;

import dev.arcana.magic.CastService;
import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.Spell;
import dev.arcana.magic.SpellContext;
import dev.arcana.registry.ModAttachments;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Arcane: raw magic with unpredictable side effects, spell duplication and missile storms. */
public final class ArcaneSpells {

    /** Fast piercing bolt. Every cast also triggers a random "wild" effect (two when ascended). */
    public static boolean bolt(SpellContext c) {
        fire(c, Magic.look(c.player), 0);
        return true;
    }

    private static void fire(SpellContext c, Vec3 dir, int depth) {
        Vec3 a = tip(c);
        Vec3 end = Magic.aimPoint(c, 30);
        if (depth > 0) end = a.add(dir.scale(30));
        Magic.line(c.level, Magic.dust(c, 1.1f), a, end, 0.3);
        Magic.line(c.level, ParticleTypes.ENCHANT, a, end, 1.2);
        Magic.line(c.level, ParticleTypes.END_ROD, a, end, 2.0);
        float dmg = 4f + 4f * c.charge;
        LivingEntity first = null;
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(a, end).inflate(1), x -> Magic.isEnemy(c.player, x))) {
            if (e.getBoundingBox().inflate(0.3).clip(a, end).isEmpty()) continue;
            Magic.hurt(c, e, dmg);
            if (first == null) first = e;
        }
        Vec3 impact = first != null ? first.position().add(0, first.getBbHeight() / 2, 0) : end;
        Magic.fx(c.level, ParticleTypes.WITCH, impact, 12, 0.3, 0.3, 0.3, 0.2);
        Magic.snd(c.level, impact, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.2f);
        int wilds = c.stage >= 2 ? 2 : 1;
        for (int i = 0; i < wilds; i++) wild(c, impact, first, depth);
    }

    private static Vec3 tip(SpellContext c) {
        return CastService.tip(c.player);
    }

    private static void wild(SpellContext c, Vec3 at, LivingEntity target, int depth) {
        switch (c.level.random.nextInt(8)) {
            case 0 -> { // burst
                Magic.fx(c.level, ParticleTypes.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
                for (LivingEntity e : Magic.enemies(c, at, 3.2)) Magic.hurt(c, e, 3f);
            }
            case 1 -> { // spell duplication
                if (depth < 1) {
                    Vec3 d = Magic.rotateY(Magic.look(c.player), Math.toRadians(c.level.random.nextInt(31) - 15));
                    EffectManager.later(c.level, 4, () -> {
                        if (c.valid()) fire(c, d, depth + 1);
                    });
                }
            }
            case 2 -> { // restorative
                c.player.heal(3f);
                Magic.fx(c.level, ParticleTypes.HAPPY_VILLAGER, c.player.position().add(0, 1, 0), 10, 0.4, 0.7, 0.4, 0);
            }
            case 3 -> { // levitate
                if (target != null) Magic.effect(target, MobEffects.LEVITATION, 50, 1);
            }
            case 4 -> { // shove
                for (LivingEntity e : Magic.enemies(c, at, 3)) {
                    Vec3 kb = e.position().subtract(at).normalize().scale(1.3);
                    Magic.push(e, new Vec3(kb.x, 0.5, kb.z));
                }
            }
            case 5 -> { // arc
                int n = 0;
                for (LivingEntity e : Magic.enemies(c, at, 6)) {
                    if (e == target || n++ >= 2) continue;
                    Magic.line(c.level, ParticleTypes.ENCHANT, at, e.position().add(0, 1, 0), 0.5);
                    Magic.hurt(c, e, 2.5f);
                }
            }
            case 6 -> { // hex
                if (target != null) {
                    Magic.effect(target, MobEffects.GLOWING, 120, 0);
                    Magic.effect(target, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
                }
            }
            default -> { // feedback
                c.player.getData(ModAttachments.MAGIC).mana += 6;
                Magic.fx(c.level, ParticleTypes.ENCHANT, c.player.position().add(0, 1.2, 0), 20, 0.4, 0.6, 0.4, 0.5);
            }
        }
    }

    /** Re-casts the last spell this player cast (from any wand) - spell duplication. Ascended wands echo it three times. */
    public static boolean echo(SpellContext c) {
        PlayerMagic m = c.player.getData(ModAttachments.MAGIC);
        Spell last = m.lastSpell;
        if (last == null || last == Spell.ECHO) return false;
        int repeats = c.scale(1, 2, 3);
        float charge = m.lastCharge;
        for (int i = 0; i < repeats; i++) {
            EffectManager.later(c.level, i * 10, () -> {
                if (!c.valid()) return;
                SpellContext ec = SpellContext.of(c.player, c.wand, last, charge);
                last.impl.cast(ec);
                Magic.fx(c.level, ParticleTypes.ENCHANT, c.player.position().add(0, 1.4, 0), 24, 0.5, 0.5, 0.5, 0.6);
            });
        }
        Magic.snd(c.level, c.player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        return true;
    }

    /** A volley of homing missiles that seek out the nearest enemies. */
    public static boolean barrage(SpellContext c) {
        int missiles = 5 + c.stage * 3;
        EffectManager.run(c.level, missiles * 3, age -> {
            if (!c.valid()) return false;
            if (age % 3 != 0) return true;
            Vec3 from = tip(c);
            LivingEntity best = null;
            double bd = 18 * 18;
            for (LivingEntity e : Magic.enemies(c, c.player.position(), 18)) {
                double d = e.distanceToSqr(c.player) + c.level.random.nextInt(40);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            Vec3 to = best != null ? best.position().add(0, best.getBbHeight() / 2, 0) : from.add(Magic.look(c.player).scale(14));
            Vec3 mid = from.add(to).scale(0.5).add(c.level.random.nextGaussian() * 1.5, 1.5 + c.level.random.nextDouble() * 2, c.level.random.nextGaussian() * 1.5);
            var dust = Magic.dust(c, 1f);
            for (double t = 0; t <= 1; t += 0.06) {
                Vec3 p = from.scale((1 - t) * (1 - t)).add(mid.scale(2 * (1 - t) * t)).add(to.scale(t * t));
                c.level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            Magic.fx(c.level, ParticleTypes.WITCH, to, 8, 0.2, 0.2, 0.2, 0.1);
            Magic.snd(c.level, from, SoundEvents.EVOKER_CAST_SPELL, 0.5f, 1.6f);
            if (best != null) Magic.hurt(c, best, 3.5f);
            return true;
        }, null);
        return true;
    }

    /** Ultimate: telegraphed pillars of arcane energy crash down across a wide area. */
    public static boolean cataclysm(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 26);
        double r = 11 * c.radius;
        Magic.snd(c.level, center, SoundEvents.EVOKER_PREPARE_SUMMON, 1.5f, 0.6f);
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.SIGIL, center.add(0, 0.05, 0), 14.7f * c.radius, 160, dev.arcana.entity.SpellFxEntity.EASE);
        var dust = Magic.dust(c, 1.6f);
        Set<Integer> dummy = new HashSet<>();
        EffectManager.run(c.level, 150, age -> {
            if (!c.valid()) return false;
            Magic.ring(c.level, dust, center, r, 40, 0.1);
            if (age % 6 == 0 && dummy.size() < 1000) {
                double a = c.level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(c.level.random.nextDouble()) * r;
                Vec3 p = new Vec3(center.x + Math.cos(a) * d, 0, center.z + Math.sin(a) * d);
                Vec3 g = new Vec3(p.x, Magic.groundY(c.level, p.x, center.y, p.z), p.z);
                EffectManager.run(c.level, 14, t -> {
                    Magic.ring(c.level, dust, g, 2.8 * (1 - t / 16.0) + 0.3, 20, 0.1);
                    return true;
                }, () -> {
                    for (int h = 0; h < 14; h++) c.level.sendParticles(h % 2 == 0 ? dust : ParticleTypes.END_ROD, g.x, g.y + h * 0.9, g.z, Magic.count(4), 0.25, 0.1, 0.25, 0.02);
                    Magic.fx(c.level, ParticleTypes.EXPLOSION, g.add(0, 0.5, 0), 1, 0, 0, 0, 0);
                    Magic.snd(c.level, g, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.4f);
                    for (LivingEntity e : Magic.enemies(c, g, 3)) {
                        Magic.hurt(c, e, 9f);
                        Magic.push(e, new Vec3(0, 0.6, 0));
                    }
                });
            }
            return true;
        }, null);
        return true;
    }

    private ArcaneSpells() {}
}

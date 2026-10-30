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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Solar: piercing beams, miniature suns, radiant light and supernovae. */
public final class SolarSpells {
    private static final int GOLD = 0xFFD54F;

    /** Charged piercing beam. Awakened/ascended wands sustain it for several ticks and sweep it with the caster's aim. */
    public static boolean beam(SpellContext c) {
        int ticks = c.scale(1, 6, 10);
        float total = (4f + 9f * c.charge) * (1f + 0.15f * c.stage);
        float perTick = total / ticks;
        Set<UUID> igniteOnce = new HashSet<>();
        Magic.snd(c.level, c.player.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        EffectManager.run(c.level, ticks, age -> {
            if (!c.valid()) return false;
            Vec3 a = Magic.eye(c.player).add(0, -0.25, 0);
            Vec3 dir = Magic.look(c.player);
            double range = 26;
            Vec3 end = Magic.aimPoint(c, range);
            Magic.line(c.level, School_gold(c), a.add(dir.scale(0.8)), end, 0.35);
            Magic.line(c.level, ParticleTypes.END_ROD, a.add(dir.scale(0.8)), end, 1.6);
            Magic.fx(c.level, ParticleTypes.FLAME, end, 6, 0.2, 0.2, 0.2, 0.08);
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(a, end).inflate(1.2), x -> Magic.isEnemy(c.player, x))) {
                if (e.getBoundingBox().inflate(0.4).clip(a, end).isEmpty()) continue;
                Magic.hurt(c, e, perTick);
                if (igniteOnce.add(e.getUUID())) e.igniteForSeconds(4);
            }
            return true;
        }, null);
        return true;
    }

    private static net.minecraft.core.particles.DustParticleOptions School_gold(SpellContext c) {
        return dev.arcana.magic.School.dust(c.style.color() >= 0 ? c.rgb() : GOLD, 1.4f);
    }

    /** A miniature sun that hovers ahead of the caster, scorching enemies, then bursts in a blinding explosion. */
    public static boolean miniSun(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 9).add(0, 2.2, 0);
        double r = 6 * c.radius;
        Magic.snd(c.level, center, SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.SUN, center, 2.2f, 130, dev.arcana.entity.SpellFxEntity.EASE);
        EffectManager.run(c.level, 120, age -> {
            double pulse = 0.7 + Math.sin(age * 0.3) * 0.12;
            for (int i = 0; i < Magic.count(14); i++) {
                Vec3 d = new Vec3(c.level.random.nextGaussian(), c.level.random.nextGaussian(), c.level.random.nextGaussian()).normalize().scale(pulse);
                c.level.sendParticles(i % 3 == 0 ? ParticleTypes.FLAME : School_gold(c), center.x + d.x, center.y + d.y, center.z + d.z, 1, 0, 0, 0, 0);
            }
            Magic.fx(c.level, ParticleTypes.END_ROD, center, 3, 0.9, 0.9, 0.9, 0.05);
            if (age % 8 == 0) Magic.ring(c.level, ParticleTypes.SMALL_FLAME, center.subtract(0, 2.2, 0), r, 24, 0.2);
            if (age % 10 == 0) {
                for (LivingEntity e : Magic.enemies(c, center, r)) {
                    if (Magic.hurt(c, e, 2.5f)) e.igniteForSeconds(3);
                }
            }
            return true;
        }, () -> {
            Magic.fx(c.level, ParticleTypes.FLASH, center, 1, 0, 0, 0, 0);
            Magic.fx(c.level, ParticleTypes.END_ROD, center, 90, 0.2, 0.2, 0.2, 0.9);
            Magic.snd(c.level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.3f);
            for (LivingEntity e : Magic.enemies(c, center, r + 1)) {
                Magic.hurt(c, e, 10f);
                Magic.effect(e, MobEffects.BLINDNESS, 60, 0);
                Vec3 kb = e.position().subtract(center).normalize().scale(0.9);
                Magic.push(e, new Vec3(kb.x, 0.4, kb.z));
            }
        });
        return true;
    }

    /** Radiant pulse: heals and cleanses allies, scorches the undead. */
    public static boolean dawnlight(SpellContext c) {
        Vec3 center = c.player.position();
        double r = 8 * c.radius;
        for (LivingEntity a : Magic.allies(c, center, r)) {
            a.heal(6f + c.stage * 2);
            for (var inst : new java.util.ArrayList<>(a.getActiveEffects())) if (!inst.getEffect().value().isBeneficial()) a.removeEffect(inst.getEffect());
            Magic.effect(a, MobEffects.REGENERATION, 80, 1);
            Magic.fx(c.level, ParticleTypes.END_ROD, a.position().add(0, 1, 0), 14, 0.3, 0.8, 0.3, 0.05);
        }
        for (LivingEntity e : Magic.enemies(c, center, r)) {
            if (e.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                Magic.hurt(c, e, 9f);
                e.igniteForSeconds(5);
            }
        }
        EffectManager.run(c.level, 12, age -> {
            Magic.ring(c.level, School_gold(c), center, r * (age + 1) / 12.0, 48, 0.3);
            for (int h = 0; h < 4; h++) Magic.fx(c.level, ParticleTypes.END_ROD, center.add(0, h, 0), 3, r * 0.2, 0.4, r * 0.2, 0.02);
            return true;
        }, null);
        Magic.snd(c.level, center, SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.2f);
        return true;
    }

    /** Ultimate: a sun gathers above the target and detonates across a huge radius. */
    public static boolean supernova(SpellContext c) {
        Vec3 ground = Magic.aimFar(c, 32, 19);
        Vec3 core = ground.add(0, 7, 0);
        double r = 14 * c.radius;
        Magic.snd(c.level, core, SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.SUN, core, 11f, 80, dev.arcana.entity.SpellFxEntity.GATHER);
        EffectManager.run(c.level, 70, age -> {
            double size = 0.4 + age * 0.04;
            for (int i = 0; i < Magic.count(22); i++) {
                Vec3 d = new Vec3(c.level.random.nextGaussian(), c.level.random.nextGaussian(), c.level.random.nextGaussian()).normalize().scale(size);
                c.level.sendParticles(i % 4 == 0 ? ParticleTypes.FLAME : School_gold(c), core.x + d.x, core.y + d.y, core.z + d.z, 1, 0, 0, 0, 0);
            }
            Magic.ring(c.level, ParticleTypes.END_ROD, ground, r * (1 - age / 80.0), 40, 0.15);
            if (c.valid()) c.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 3, false, false));
            return true;
        }, () -> {
            Magic.fx(c.level, ParticleTypes.FLASH, core, 2, 0, 0, 0, 0);
            Magic.fx(c.level, ParticleTypes.END_ROD, core, 160, 0.3, 0.3, 0.3, 1.1);
            Magic.fx(c.level, ParticleTypes.FLAME, core, 120, 0.3, 0.3, 0.3, 0.9);
            for (int i = 1; i <= 5; i++) Magic.ring(c.level, School_gold(c), ground, r * i / 5.0, 40, 0.3);
            Magic.snd(c.level, core, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.6f);
            Magic.snd(c.level, core, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 1.4f);
            for (LivingEntity e : Magic.enemies(c, ground, r)) {
                if (Magic.hurt(c, e, 22f)) e.igniteForSeconds(8);
                Magic.effect(e, MobEffects.BLINDNESS, 80, 0);
                Vec3 kb = e.position().subtract(ground).multiply(1, 0, 1).normalize().scale(1.6);
                Magic.push(e, new Vec3(kb.x, 0.7, kb.z));
            }
        });
        return true;
    }

    private SolarSpells() {}
}
